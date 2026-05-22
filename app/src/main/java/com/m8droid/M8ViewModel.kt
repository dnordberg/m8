// app/src/main/java/com/m8/M8ViewModel.kt
package com.m8droid

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8droid.audio.M8AudioPlayer
import com.m8droid.audio.NativeSynth
import com.m8droid.audio.SampleCache
import com.m8droid.data.ServerConfig
import com.m8droid.data.ServerSettings
import com.m8droid.emulator.*
import com.m8droid.academy.data.EmulatorEventRepository
import com.m8droid.academy.data.EmulatorSnapshot
import com.m8droid.midi.MidiEngine
import com.m8droid.input.StickyKeyLatch
import com.m8droid.network.ConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Local-only M8 emulator ViewModel.
 * Drives the M8Song sequencer (song grid -> chains -> phrases -> steps)
 * and feeds the M8Synth for audio, while keeping the M8Emulator for display rendering.
 *
 * Audio generation runs on a dedicated high-priority thread to prevent
 * coroutine preemption and scheduling gaps that cause audio cutoff.
 * Display rendering stays on a coroutine at ~30fps for UI updates.
 */
class M8ViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "M8ViewModel"
    }

    private val fontBitmap = try {
        application.assets.open("m8_font.png").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
    val connectionManager = ConnectionManager(fontBitmap)

    // Persistent settings (host/port/layout). Backed by DataStore.
    val serverConfig = ServerConfig(application.applicationContext)
    val serverSettings: StateFlow<ServerSettings> = serverConfig.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ServerSettings())

    // Tick counter to trigger display recomposition (~30fps)
    private val _displayTick = MutableStateFlow(0)
    val displayTick: StateFlow<Int> = _displayTick

    private val emulator = M8Emulator()
    private val synth = M8Synth() // kept for visualization API compat
    private val sampleCache = SampleCache(File(application.filesDir, "m8sd"))
    private val localAudioPlayer = M8AudioPlayer()
    private val fxEngine = M8FxEngine()
    private var nativeSynthReady = false

    // Academy snapshot bridge
    val emulatorEvents = EmulatorEventRepository()

    @Volatile var keyInputPaused = false

    // Tutorial system (reads emulator state)
    val tutorial = com.m8droid.tutorial.M8Tutorial(emulator)

    // Expose emulator screen for tutorial context
    val currentScreen: Int get() = emulator.screen
    val isEditMode: Boolean get() = emulator.editMode
    val canEnterHexDigit: Boolean get() = emulator.canEnterHexDigit()
    val canEnterNoteFromPicker: Boolean get() = emulator.canEnterNoteFromPicker()
    private var emulatorRenderJob: Job? = null
    private var audioThread: Thread? = null
    private var samplesUntilNextFxTick = 0
    private var fxTickInRow = 0
    private var lastTriggeredRowData = Array(8) { track -> intArrayOf(0, track, 0, 0, 0) }
    private var pendingPhraseHop = -1
    private var pendingSongHop = -1

    // Use the emulator's song data model (shared state for display + audio)
    private val song get() = emulator.song
    private val instruments get() = emulator.instruments

    // ======================= MIDI =======================
    private val midiEngine = MidiEngine(application.applicationContext).also { engine ->
        engine.onNoteOn = { channel, note, velocity -> handleMidiNoteOn(channel, note, velocity) }
        engine.onNoteOff = { channel, note -> handleMidiNoteOff(channel, note) }
        engine.onActivity = { markMidiActivity() }
    }

    /** Last time (ms) we saw a MIDI event in either direction. Used by the M indicator. */
    @Volatile private var lastMidiActivityMs: Long = 0L

    /** Notes currently held on each MIDI channel so we can send matching Note Offs. */
    private val midiHeldNotes = IntArray(8) { -1 }

    /** Last notes we sent out from the sequencer, per track, so we can release them. */
    private val midiOutHeld = IntArray(8) { -1 }

    private fun markMidiActivity() {
        lastMidiActivityMs = System.currentTimeMillis()
        emulator.midiActive = true
    }

    /** Route an incoming MIDI note to the native synth on the matching M8 track. */
    private fun handleMidiNoteOn(channel: Int, note: Int, velocity: Int) {
        val track = channel.coerceIn(0, 7)
        midiHeldNotes[track] = note
        val notes = ByteArray(8) { if (it == track) note.toByte() else 0 }
        val vols = ByteArray(8) { if (it == track) velocity.toByte() else 0 }
        try {
            if (nativeSynthReady) NativeSynth.triggerRow(notes, vols)
            else synth.triggerRow(Array(8) { t ->
                if (t == track) intArrayOf(note, t, velocity, 0, 0)
                else intArrayOf(0, t, 0, 0, 0)
            })
        } catch (_: Exception) { }
    }

    private fun handleMidiNoteOff(channel: Int, note: Int) {
        val track = channel.coerceIn(0, 7)
        if (midiHeldNotes[track] != note) return
        midiHeldNotes[track] = -1
        val notes = ByteArray(8) { if (it == track) M8Synth.NOTE_OFF.toByte() else 0 }
        val vols = ByteArray(8)
        try {
            if (nativeSynthReady) NativeSynth.triggerRow(notes, vols)
            else synth.triggerRow(Array(8) { t ->
                if (t == track) intArrayOf(M8Synth.NOTE_OFF, t, 0, 0, 0)
                else intArrayOf(0, t, 0, 0, 0)
            })
        } catch (_: Exception) { }
    }

    /**
     * Broadcast the sequencer row out to any connected MIDI devices. Called
     * from the audio thread immediately after [triggerCurrentRow] fires the
     * internal synth, so software and hardware stay locked in time.
     */
    private fun broadcastMidiRow(rowData: Array<IntArray>) {
        for (t in 0 until 8) {
            val note = rowData[t][0]
            val vel = (rowData[t][2].coerceIn(0, 255) / 2).coerceIn(0, 127)
            // Release any previously held note on this track before a new trigger.
            val prev = midiOutHeld[t]
            if (prev >= 0 && (note == M8Synth.NOTE_OFF || note in 1..127)) {
                midiEngine.sendNoteOff(t, prev)
                midiOutHeld[t] = -1
            }
            when {
                note in 1..127 -> {
                    midiEngine.sendNoteOn(t, note, if (vel > 0) vel else 100)
                    midiOutHeld[t] = note
                }
                note == M8Synth.NOTE_OFF -> {
                    // Already released above.
                }
            }
        }
    }

    private fun releaseAllMidiOut() {
        for (t in 0 until 8) {
            val n = midiOutHeld[t]
            if (n >= 0) {
                midiEngine.sendNoteOff(t, n)
                midiOutHeld[t] = -1
            }
        }
    }

    fun startMidi() = midiEngine.start()
    fun stopMidi() {
        releaseAllMidiOut()
        midiEngine.stop()
    }

    // Public accessors for views that read live emulator state.
    val songData: M8Song get() = emulator.song
    val instrumentList: Array<M8Instrument> get() = emulator.instruments
    val isPlaying: Boolean get() = emulator.playing
    val currentPlayRow: Int get() = emulator.playRow
    val liveMasterLevels: Pair<Double, Double>
        get() = emulator.liveMasterLevelL to emulator.liveMasterLevelR
    val liveTrackLevelArray: DoubleArray? get() = emulator.liveTrackLevels

    fun togglePlayback() {
        emulator.playing = !emulator.playing
        if (emulator.playing) emulator.playRow = 0
    }

    fun stopPlayback() {
        emulator.playing = false
        emulator.playRow = 0
    }

    fun setTempo(bpm: Int) {
        emulator.song.tempo = bpm.coerceIn(30, 300)
    }

    fun setTrackVolume(track: Int, vol: Int) {
        if (track in 0..7) emulator.song.mixer.trackVolumes[track] = vol.coerceIn(0, 255)
    }

    fun setTrackPan(track: Int, pan: Int) {
        if (track in 0..7) emulator.song.mixer.trackPans[track] = pan.coerceIn(0, 255)
    }

    fun setMasterVolume(vol: Int) {
        emulator.song.mixer.masterVolume = vol.coerceIn(0, 255)
    }

    // Sequencer position
    @Volatile private var songRow = 0       // row in song grid (0-255)
    @Volatile private var chainRow = 0      // row within current chain (0-15)
    @Volatile private var phraseRow = 0     // step within current phrase (0-15)

    // BPM-synced row timing
    @Volatile private var samplesUntilNextRow = 0
    @Volatile private var wasPlaying = false

    // Previous songRow value for detecting song-loop wraparound
    @Volatile private var previousSongRow = 0

    fun startLocalEmulator() {
        // Clean stop any existing audio before restarting
        stopEmulator()

        // Sync sequencer state
        songRow = 0
        chainRow = 0
        phraseRow = 0
        samplesUntilNextRow = 0
        wasPlaying = false
        previousSongRow = 0

        // Display render loop — runs on default dispatcher for UI updates
        emulatorRenderJob?.cancel()
        emulatorRenderJob = viewModelScope.launch {
            while (true) {
                // Pipe live synth data into emulator for visualization.
                // Reads from synth fields written by the audio thread; wrapped
                // in try/catch since these are best-effort visualization values.
                try {
                    if (nativeSynthReady) {
                        val levels = NativeSynth.getMasterLevels()
                        emulator.liveMasterLevelL = levels[0]
                        emulator.liveMasterLevelR = levels[1]
                        emulator.liveTrackLevels = NativeSynth.getTrackLevels()
                    } else {
                        emulator.liveWaveformData = synth.getWaveformData()
                        emulator.liveTrackLevels = synth.trackLevels.clone()
                        emulator.liveMasterLevelL = synth.masterLevelL
                        emulator.liveMasterLevelR = synth.masterLevelR
                    }
                } catch (_: Exception) {
                    // Non-critical: visualization data may be briefly inconsistent
                }

                // Decay the MIDI-activity indicator after ~200ms of silence.
                emulator.midiActive = System.currentTimeMillis() - lastMidiActivityMs < 200

                val frameData = emulator.renderFrame()
                connectionManager.protocol.processBytes(frameData)

                emulatorEvents.emit(EmulatorSnapshot(
                    playing = emulator.playing,
                    screen = emulator.screen,
                    songRow = songRow,
                    chainRow = chainRow,
                    phraseRow = phraseRow,
                    bpm = emulator.bpm,
                    cursorX = emulator.cursorX,
                    cursorY = emulator.cursorY,
                    editMode = emulator.editMode,
                    midiActive = emulator.midiActive,
                ))

                _displayTick.value++
                delay(33) // ~30fps
            }
        }

        // Initialize native Rust synth engine
        try {
            NativeSynth.init()
            nativeSynthReady = true
            Log.i(TAG, "Native Rust synth engine initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init native synth, falling back to Kotlin: ${e.message}")
            nativeSynthReady = false
        }

        // Start audio player
        localAudioPlayer.start()

        // Pass mixer settings so the synth can read per-track volumes, pans,
        // and send levels directly from the song's mixer configuration.
        synth.mixerSettings = song.mixer

        // Give the synth a reference to the FX engine so it can call
        // getFreqModifier() per-sample for arpeggio, vibrato, and portamento.
        synth.fxEngine = fxEngine

        // Configure synth voices from loaded instrument definitions instead
        // of relying solely on hardcoded TRACK_PRESETS defaults.
        configureVoicesFromInstruments()

        // Audio render loop — dedicated thread at urgent-audio priority.
        // Owns BPM timing, row advance, song sequencing, and synth output.
        // Runs independently of the coroutine scheduler to avoid preemption.
        audioThread?.interrupt()
        audioThread?.join(500)
        audioThread = Thread({
            android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
            )
            Log.i(TAG, "Audio render thread started")
            var audioErrorCount = 0

            while (!Thread.currentThread().isInterrupted) {
                try {
                    val isPlaying = emulator.playing

                    if (isPlaying) {
                        if (!wasPlaying) {
                            wasPlaying = true
                            samplesUntilNextRow = 0
                            samplesUntilNextFxTick = 0
                            fxTickInRow = 0
                            songRow = 0
                            chainRow = 0
                            phraseRow = 0
                            fxEngine.reset()
                            syncEmulatorPosition()
                        }

                        val bpm = song.tempo.coerceIn(60, 300)
                        val rowDurationSamples =
                            (M8Synth.SAMPLE_RATE * 60.0 / (bpm * 4.0)).toInt()

                        if (samplesUntilNextRow <= 0) {
                            triggerCurrentRow()

                            val nextPhraseRow = (phraseRow + 1) % 16
                            val swingDelay =
                                synth.getSwingDelaySamples(nextPhraseRow, bpm)
                            samplesUntilNextRow = rowDurationSamples + swingDelay
                            fxTickInRow = 0
                            samplesUntilNextFxTick = fxTickDurationSamples(samplesUntilNextRow)

                            advanceSequencer()
                        }

                        val pcm = if (nativeSynthReady) NativeSynth.generateChunk()
                                  else synth.generateChunk()
                        localAudioPlayer.write(pcm)
                        samplesUntilNextRow -= M8Synth.CHUNK_SAMPLES
                        samplesUntilNextFxTick -= M8Synth.CHUNK_SAMPLES
                        while (samplesUntilNextFxTick <= 0 && samplesUntilNextRow > 0) {
                            processRuntimeFxTick()
                            samplesUntilNextFxTick += fxTickDurationSamples(rowDurationSamples)
                        }
                    } else {
                        if (wasPlaying) {
                            wasPlaying = false
                            if (nativeSynthReady) NativeSynth.allNotesOff()
                            else synth.allNotesOff()
                            fxEngine.reset()
                            samplesUntilNextRow = 0
                            samplesUntilNextFxTick = 0
                            fxTickInRow = 0
                        }
                        // Write silence — just zeros
                        localAudioPlayer.write(ByteArray(M8Synth.CHUNK_SAMPLES * 2 * 2))
                    }
                    // Reset error count on successful iteration
                    audioErrorCount = 0
                } catch (e: Exception) {
                    if (e is InterruptedException) break
                    audioErrorCount++
                    Log.e(TAG, "Audio render error ($audioErrorCount): ${e.message}", e)
                    if (audioErrorCount > 100) {
                        Log.e(TAG, "Too many consecutive audio errors, stopping render thread")
                        break
                    }
                    // Do not sleep — skip this chunk and continue to avoid audio gaps
                }
            }

            Log.i(TAG, "Audio render thread exiting")
        }, "M8SynthThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun fxTickDurationSamples(rowDurationSamples: Int): Int = (rowDurationSamples / 6).coerceAtLeast(M8Synth.CHUNK_SAMPLES)

    private fun processRuntimeFxTick() {
        fxTickInRow++
        if (fxTickInRow <= 0) return
        for (track in 0 until 8) {
            val tickResult = fxEngine.processTick(track, fxTickInRow)
            if (tickResult.releaseNote) {
                releaseRuntimeTrack(track)
            }
            if (tickResult.delayedNote >= 0) {
                triggerRuntimeTrack(
                    track = track,
                    note = tickResult.delayedNote,
                    instrument = tickResult.delayedInstrument,
                    volume = tickResult.delayedVolume,
                )
            } else if (tickResult.retrigger) {
                val row = lastTriggeredRowData[track]
                if (row[0] > 0 && row[0] != M8Synth.NOTE_OFF) {
                    triggerRuntimeTrack(
                        track = track,
                        note = row[0],
                        instrument = row[1],
                        volume = row[2],
                    )
                }
            }
        }
    }

    private fun releaseRuntimeTrack(track: Int) {
        if (nativeSynthReady) {
            val notes = ByteArray(8) { if (it == track) M8Synth.NOTE_OFF.toByte() else 0 }
            val vols = ByteArray(8)
            NativeSynth.triggerRow(notes, vols)
        } else {
            synth.releaseTrack(track)
        }
        if (midiOutHeld[track] >= 0) {
            midiEngine.sendNoteOff(track, midiOutHeld[track])
            midiOutHeld[track] = -1
            markMidiActivity()
        }
    }

    private fun triggerRuntimeTrack(track: Int, note: Int, instrument: Int, volume: Int) {
        val inst = instruments.getOrNull(instrument)
        if (inst != null) configureTrackInstrument(track, inst)
        val safeVolume = volume.coerceIn(0, 0xFF)
        if (nativeSynthReady) {
            val notes = ByteArray(8) { if (it == track) note.toByte() else 0 }
            val vols = ByteArray(8) { if (it == track) safeVolume.toByte() else 0 }
            NativeSynth.triggerRow(notes, vols)
        } else {
            synth.triggerRow(Array(8) { t ->
                if (t == track) intArrayOf(note, instrument, safeVolume, 0, 0)
                else intArrayOf(0, t, 0, 0, 0)
            })
        }
        if (midiOutHeld[track] >= 0) midiEngine.sendNoteOff(track, midiOutHeld[track])
        midiEngine.sendNoteOn(track, note, safeVolume)
        midiOutHeld[track] = note
        markMidiActivity()
    }

    /**
     * Trigger the current phrase row across all 8 tracks.
     * Resolves: songGrid[songRow][track] -> chain -> phrase -> PhraseStep
     * Converts PhraseStep to the IntArray format M8Synth.triggerRow expects.
     */
    private fun triggerCurrentRow() {
        // Periodic logging for audio debugging
        if (phraseRow == 0) {
            Log.d(TAG, "triggerCurrentRow: songRow=$songRow chainRow=$chainRow phraseRow=$phraseRow tempo=${song.tempo}")
        }

        val rowData = Array(8) { track ->
            val chainIdx = song.songGrid[songRow][track]
            if (chainIdx == M8Song.EMPTY || chainIdx > 254) {
                // No chain on this track — send empty (note=0 means "continue")
                intArrayOf(0, track, 0, 0, 0)
            } else {
                val chain = song.chains[chainIdx]
                val chainEntry = chain.rows[chainRow.coerceIn(0, 15)]
                val phraseIdx = chainEntry.phrase
                val transpose = chainEntry.transpose

                if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) {
                    intArrayOf(0, track, 0, 0, 0)
                } else {
                    val step = song.phrases[phraseIdx].steps[phraseRow.coerceIn(0, 15)]
                    stepToIntArray(step, track, transpose)
                }
            }
        }

        // Process FX commands before triggering
        for (track in 0 until 8) {
            val chainIdx = song.songGrid[songRow][track]
            if (chainIdx != M8Song.EMPTY && chainIdx <= 254) {
                val chain = song.chains[chainIdx]
                val chainEntry = chain.rows[chainRow.coerceIn(0, 15)]
                val phraseIdx = chainEntry.phrase
                if (phraseIdx != M8Song.EMPTY && phraseIdx <= 254) {
                    val step = song.phrases[phraseIdx].steps[phraseRow.coerceIn(0, 15)]
                    val baseNote = if (step.note != M8Song.EMPTY && step.note != M8Song.NOTE_OFF) {
                        step.note + chainEntry.transpose
                    } else {
                        0
                    }
                    val fxResult = fxEngine.processStepFx(track, step, 0, baseNote)

                    // Apply FX results
                    if (fxResult.skipNote) {
                        // Replace with empty (continue) row data for this track
                        rowData[track] = intArrayOf(0, track, 0, 0, 0)
                    }
                    if (fxResult.hopToRow >= 0 && pendingPhraseHop < 0) {
                        pendingPhraseHop = fxResult.hopToRow.coerceIn(0, 15)
                    }
                    if (fxResult.songHopToRow >= 0 && pendingSongHop < 0) {
                        pendingSongHop = fxResult.songHopToRow.coerceIn(0, 255)
                    }
                    if (fxResult.tempoChange > 0) {
                        song.tempo = fxResult.tempoChange
                        emulator.bpm = song.tempo
                    }
                    if (fxResult.noteOffset != 0 && rowData[track][0] > 0) {
                        rowData[track][0] = (rowData[track][0] + fxResult.noteOffset).coerceIn(1, 127)
                    }
                    if (fxResult.volumeOverride >= 0) {
                        rowData[track][2] = fxResult.volumeOverride
                    }
                    if (fxResult.ampOverride >= 0) {
                        synth.setRuntimeTrackAmp(track, fxResult.ampOverride)
                    }
                    if (fxResult.panOverride >= 0) {
                        synth.setRuntimeTrackPan(track, fxResult.panOverride)
                    }
                    if (fxResult.delaySendOverride >= 0) {
                        synth.setRuntimeTrackDelaySend(track, fxResult.delaySendOverride)
                    }
                }
            }
        }

        // Apply instrument presets before triggering notes so sampler params,
        // envelopes, and sample assignments affect the current row immediately.
        for (track in 0 until 8) {
            val data = rowData[track]
            val instIdx = data[1]
            if (instIdx in instruments.indices) {
                configureTrackInstrument(track, instruments[instIdx])
            }
        }
        lastTriggeredRowData = Array(8) { track -> rowData[track].copyOf() }

        if (nativeSynthReady) {
            // Pack notes and volumes into byte arrays for JNI
            val notes = ByteArray(8) { rowData[it][0].toByte() }
            val vols = ByteArray(8) { rowData[it][2].toByte() }
            NativeSynth.triggerRow(notes, vols)
        } else {
            synth.triggerRow(rowData)
        }

        // Mirror the row to any connected MIDI-out device.
        broadcastMidiRow(rowData)

        // Log first row's notes for debugging
        if (phraseRow == 0) {
            val notes = rowData.map { it[0] }.joinToString(",")
            Log.d(TAG, "Notes triggered: [$notes]")
        }
    }

    /**
     * Convert a PhraseStep to the IntArray format the synth expects:
     * intArrayOf(note, instrument, volume, fx1Cmd, fx2Cmd)
     */
    private fun stepToIntArray(step: PhraseStep, track: Int, transpose: Int): IntArray {
        val note = when (step.note) {
            M8Song.EMPTY -> 0   // Empty = continue (synth interprets 0 as "keep playing")
            M8Song.NOTE_OFF -> M8Synth.NOTE_OFF  // Note off -> synth's NOTE_OFF (0xFF)
            else -> (step.note + transpose).coerceIn(1, 127)  // Apply chain transpose
        }
        val instrument = if (step.instrument == M8Song.EMPTY) track else step.instrument
        val volume = if (step.volume == M8Song.EMPTY) 0xCC else step.volume
        val fx1 = step.fx1Cmd
        val fx2 = step.fx2Cmd
        return intArrayOf(note, instrument, volume, fx1, fx2)
    }

    /**
     * Configure synth voices from the loaded M8Instrument definitions.
     * Called at startup so voices use instrument parameters instead of
     * only falling back to hardcoded TRACK_PRESETS defaults.
     */
    private fun configureVoicesFromInstruments() {
        for (i in 0 until 8) {
            val inst = instruments.getOrNull(i) ?: continue
            configureTrackInstrument(i, inst)
        }
    }

    private fun configureTrackInstrument(track: Int, inst: M8Instrument) {
        synth.configureVoice(track, inst)
        if (inst.type == InstrumentType.SAMPLER && inst.sampler.samplePath.isNotBlank()) {
            synth.loadSample(track, sampleCache.load(inst.sampler.samplePath))
        } else {
            synth.loadSample(track, null)
        }
    }

    /**
     * Advance the sequencer position after triggering the current row.
     * phraseRow overflows -> advance chainRow.
     * chainRow overflows (all chains exhausted) -> advance songRow.
     */
    private fun advanceSequencer() {
        if (pendingSongHop >= 0) {
            previousSongRow = songRow
            songRow = pendingSongHop.coerceIn(0, 255)
            chainRow = 0
            phraseRow = 0
            pendingSongHop = -1
            pendingPhraseHop = -1
            syncEmulatorPosition()
            return
        }
        if (pendingPhraseHop >= 0) {
            phraseRow = pendingPhraseHop.coerceIn(0, 15)
            pendingPhraseHop = -1
            syncEmulatorPosition()
            return
        }

        phraseRow++

        if (phraseRow >= 16) {
            phraseRow = 0
            chainRow++

            // Check if we've exhausted the current chains (all tracks' chains done at this chainRow)
            var exhausted = true
            for (t in 0 until 8) {
                val chainIdx = song.songGrid[songRow][t]
                if (chainIdx != M8Song.EMPTY && chainIdx <= 254) {
                    val chain = song.chains[chainIdx]
                    if (chainRow < 16 && chain.rows[chainRow].phrase != M8Song.EMPTY) {
                        exhausted = false
                        break
                    }
                }
            }

            if (exhausted) {
                chainRow = 0
                previousSongRow = songRow
                songRow++

                // Find next non-empty song row (or wrap)
                var tries = 0
                while (tries < 256) {
                    if (songRow >= 256) songRow = 0
                    var hasContent = false
                    for (t in 0 until 8) {
                        if (song.songGrid[songRow][t] != M8Song.EMPTY) {
                            hasContent = true
                            break
                        }
                    }
                    if (hasContent) break
                    songRow++
                    tries++
                }

                // Log when the song wraps back to the beginning
                if (songRow < previousSongRow) {
                    Log.i(TAG, "Song looped: wrapped from songRow=$previousSongRow back to songRow=$songRow (tempo=${song.tempo})")
                }
            }
        }

        syncEmulatorPosition()
    }

    /**
     * Sync emulator display state from the sequencer position.
     */
    private fun syncEmulatorPosition() {
        emulator.playRow = phraseRow
        emulator.songRow = songRow
        emulator.chainRow = chainRow
        emulator.bpm = song.tempo
    }

    private fun stopEmulator() {
        // Stop the audio thread first — interrupt and wait for clean exit
        audioThread?.interrupt()
        try { audioThread?.join(1000) } catch (_: InterruptedException) { }
        audioThread = null

        // Cancel the display render coroutine
        emulatorRenderJob?.cancel()
        emulatorRenderJob = null

        // Stop audio playback and silence all voices
        localAudioPlayer.stop()
        if (nativeSynthReady) {
            NativeSynth.allNotesOff()
            NativeSynth.destroy()
            nativeSynthReady = false
        }
        synth.allNotesOff()
    }

    // 3-button combo detection for tutorial toggle: OPT+EDIT+SHIFT
    private var comboHeldFrames = 0
    private val COMBO_MASK = com.m8droid.protocol.M8Commands.KEY_OPTION or
            com.m8droid.protocol.M8Commands.KEY_EDIT or
            com.m8droid.protocol.M8Commands.KEY_SHIFT

    // Separate masks per input source so touch + keyboard can be held simultaneously
    // without one overwriting the other. Combined state is exposed via [keyState]
    // so the on-screen controls can light up in response to keyboard input.
    private var touchKeys = 0
    private var keyboardKeys = 0
    private val stickyTouchKeys = StickyKeyLatch()
    private val _stickyKeyState = MutableStateFlow(0)
    val stickyKeyState: StateFlow<Int> = _stickyKeyState
    private val _keyState = MutableStateFlow(0)
    val keyState: StateFlow<Int> = _keyState

    fun setTouchKeys(keys: Int) {
        touchKeys = keys
        dispatchKeys()
    }

    fun setKeyboardKeys(keys: Int) {
        keyboardKeys = keys
        dispatchKeys()
    }

    fun toggleStickyTouchKey(key: Int) {
        stickyTouchKeys.toggle(key)
        _stickyKeyState.value = stickyTouchKeys.mask
        dispatchKeys()
    }

    fun clearStickyTouchKeys() {
        stickyTouchKeys.clear()
        _stickyKeyState.value = 0
        dispatchKeys()
    }

    private fun dispatchKeys() {
        val keys = stickyTouchKeys.applyTo(touchKeys) or keyboardKeys
        _keyState.value = keys
        // Detect OPT+EDIT+SHIFT held simultaneously (like on real hardware)
        if (keys and COMBO_MASK == COMBO_MASK) {
            comboHeldFrames++
            if (comboHeldFrames == 1) {
                toggleTutorial()
                return // Don't pass combo to emulator
            }
            return
        } else {
            comboHeldFrames = 0
        }
        if (!keyInputPaused) emulator.handleKeyState(keys)
    }

    @Deprecated("Use setTouchKeys/setKeyboardKeys", ReplaceWith("setTouchKeys(keys)"))
    fun sendKeyState(keys: Int) = setTouchKeys(keys)

    /** Persist new settings and restart the local emulator so changes take effect. */
    fun saveSettings(settings: ServerSettings) {
        viewModelScope.launch {
            serverConfig.save(settings)
        }
    }

    /** Restart local emulator — useful when user wants a clean-slate server state. */
    fun restartServer() {
        startLocalEmulator()
    }

    fun toggleTutorial() {
        if (tutorial.active) {
            if (tutorial.paused) tutorial.resume() else tutorial.pause()
        } else {
            tutorial.start()
        }
    }

    fun setScreen(screen: Int) {
        emulator.screen = screen.coerceIn(0, 7)
        emulator.editMode = false
    }

    fun handleDisplayTap(m8X: Int, m8Y: Int) {
        emulator.handleDisplayTap(m8X, m8Y)
    }

    fun enterHexDigit(digit: Int): Boolean = emulator.enterHexDigit(digit)

    fun enterNoteFromPicker(semitone: Int): Boolean = emulator.enterNoteFromPicker(semitone)

    fun nextScreen() {
        emulator.screen = (emulator.screen + 1) % 8
        emulator.editMode = false
    }

    fun prevScreen() {
        emulator.screen = (emulator.screen - 1 + 8) % 8
        emulator.editMode = false
    }

    fun adjustTempo(delta: Int) {
        song.tempo = (song.tempo + delta).coerceIn(40, 300)
        emulator.bpm = song.tempo
    }

    /** Number of instrument slots the emulator exposes. */
    val instrumentSlotCount: Int get() = instruments.size

    /**
     * Replace the instrument at [slot] with a newly-parsed M8Instrument
     * (typically loaded from a .m8i file). Reconfigures the corresponding
     * synth voice so the change is audible immediately.
     */
    fun replaceInstrument(slot: Int, newInst: M8Instrument) {
        if (slot !in instruments.indices) return
        instruments[slot] = newInst
        runCatching { configureTrackInstrument(slot, newInst) }
    }

    /**
     * Replace the playing song with the data in [parsed]. Mutates the
     * emulator's live M8Song (since it's held as a val) and rewinds the
     * sequencer to row 0 so the new song starts from the top.
     *
     * Intentionally does NOT touch instruments — the .m8s parser is
     * playback-core only (grid/phrases/chains/tables/tempo). Existing
     * instrument slots stay live so steps with instrument references
     * still produce sound. See DECISIONS.md.
     */
    fun replaceSong(parsed: M8sParser.ParsedSong) {
        val wasPlaying = emulator.playing
        emulator.playing = false
        M8sParser.applyTo(parsed, song)
        emulator.bpm = song.tempo
        songRow = 0
        chainRow = 0
        phraseRow = 0
        samplesUntilNextRow = 0
        previousSongRow = 0
        emulator.cursorX = 0
        emulator.cursorY = 0
        emulator.resetPlayheadAndResolve()
        if (wasPlaying) {
            emulator.playing = true
            emulator.playRow = 0
        }
        Log.i(TAG, "Loaded song '${song.name}' @ ${song.tempo} BPM")
    }

    fun playFromCursor() {
        emulator.playing = true
        emulator.playRow = emulator.cursorY
        phraseRow = emulator.cursorY
        samplesUntilNextRow = 0
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulator()
    }
}
