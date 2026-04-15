// app/src/main/java/com/m8/M8ViewModel.kt
package com.m8droid

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8droid.audio.M8AudioPlayer
import com.m8droid.audio.NativeSynth
import com.m8droid.data.ServerConfig
import com.m8droid.data.ServerSettings
import com.m8droid.emulator.*
import com.m8droid.network.ConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val localAudioPlayer = M8AudioPlayer()
    private val fxEngine = M8FxEngine()
    private var nativeSynthReady = false

    // Tutorial system (reads emulator state)
    val tutorial = com.m8droid.tutorial.M8Tutorial(emulator)

    // Expose emulator screen for tutorial context
    val currentScreen: Int get() = emulator.screen
    private var emulatorRenderJob: Job? = null
    private var audioThread: Thread? = null

    // Use the emulator's song data model (shared state for display + audio)
    private val song get() = emulator.song
    private val instruments get() = emulator.instruments

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

                val frameData = emulator.renderFrame()
                connectionManager.protocol.processBytes(frameData)

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

                            advanceSequencer()
                        }

                        val pcm = if (nativeSynthReady) NativeSynth.generateChunk()
                                  else synth.generateChunk()
                        localAudioPlayer.write(pcm)
                        samplesUntilNextRow -= M8Synth.CHUNK_SAMPLES
                    } else {
                        if (wasPlaying) {
                            wasPlaying = false
                            if (nativeSynthReady) NativeSynth.allNotesOff()
                            else synth.allNotesOff()
                            fxEngine.reset()
                            samplesUntilNextRow = 0
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
                    if (fxResult.tempoChange > 0) {
                        song.tempo = fxResult.tempoChange
                        emulator.bpm = song.tempo
                    }
                    if (fxResult.noteOffset != 0 && rowData[track][0] > 0) {
                        rowData[track][0] = (rowData[track][0] + fxResult.noteOffset).coerceIn(1, 127)
                    }
                }
            }
        }

        if (nativeSynthReady) {
            // Pack notes and volumes into byte arrays for JNI
            val notes = ByteArray(8) { rowData[it][0].toByte() }
            val vols = ByteArray(8) { rowData[it][2].toByte() }
            NativeSynth.triggerRow(notes, vols)
        } else {
            synth.triggerRow(rowData)
        }

        // Log first row's notes for debugging
        if (phraseRow == 0) {
            val notes = rowData.map { it[0] }.joinToString(",")
            Log.d(TAG, "Notes triggered: [$notes]")
        }

        // Apply instrument presets to synth voices for correct sound
        for (track in 0 until 8) {
            val data = rowData[track]
            val instIdx = data[1]
            if (instIdx in instruments.indices) {
                synth.applyInstrument(track, instruments[instIdx])
            }
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
            synth.configureVoice(i, inst)
        }
    }

    /**
     * Advance the sequencer position after triggering the current row.
     * phraseRow overflows -> advance chainRow.
     * chainRow overflows (all chains exhausted) -> advance songRow.
     */
    private fun advanceSequencer() {
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

    fun sendKeyState(keys: Int) {
        // Detect OPT+EDIT+SHIFT held simultaneously (like on real hardware)
        if (keys and COMBO_MASK == COMBO_MASK) {
            comboHeldFrames++
            if (comboHeldFrames == 1) {
                // Toggle tutorial on first frame of combo
                toggleTutorial()
                return // Don't pass combo to emulator
            }
            return
        } else {
            comboHeldFrames = 0
        }
        emulator.handleKeyState(keys)
    }

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
        runCatching { synth.configureVoice(slot, newInst) }
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
