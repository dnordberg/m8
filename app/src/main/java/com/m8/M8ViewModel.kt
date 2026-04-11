package com.m8

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8.audio.M8AudioPlayer
import com.m8.emulator.*
import com.m8.network.ConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Local-only M8 emulator ViewModel.
 * Drives the M8Song sequencer (song grid -> chains -> phrases -> steps)
 * and feeds the M8Synth for audio, while keeping the M8Emulator for display rendering.
 */
class M8ViewModel(application: Application) : AndroidViewModel(application) {

    private val fontBitmap = try {
        application.assets.open("m8_font.png").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
    val connectionManager = ConnectionManager(fontBitmap)

    // Tick counter to trigger display recomposition (~30fps)
    private val _displayTick = MutableStateFlow(0)
    val displayTick: StateFlow<Int> = _displayTick

    private val emulator = M8Emulator()
    private val synth = M8Synth()
    private val localAudioPlayer = M8AudioPlayer()
    private val fxEngine = M8FxEngine()
    private var emulatorRenderJob: Job? = null
    private var audioRenderJob: Job? = null

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

    fun startLocalEmulator() {
        // Song is already loaded by emulator.init { song.loadDemoSong() }
        // Sync sequencer state
        songRow = 0
        chainRow = 0
        phraseRow = 0
        samplesUntilNextRow = 0
        wasPlaying = false

        // Display render loop
        emulatorRenderJob?.cancel()
        emulatorRenderJob = viewModelScope.launch {
            while (true) {
                // Pipe live synth data into emulator for visualization
                try {
                    emulator.liveWaveformData = synth.getWaveformData()
                    emulator.liveTrackLevels = synth.trackLevels.clone()
                    emulator.liveMasterLevelL = synth.masterLevelL
                    emulator.liveMasterLevelR = synth.masterLevelR
                } catch (_: Exception) {}

                val frameData = emulator.renderFrame()
                connectionManager.protocol.processBytes(frameData)

                _displayTick.value++
                delay(33) // ~30fps
            }
        }

        // Audio render loop — owns BPM timing, row advance, and song sequencing
        audioRenderJob?.cancel()
        localAudioPlayer.start()
        audioRenderJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
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
                        val rowDurationSamples = (M8Synth.SAMPLE_RATE * 60.0 / (bpm * 4.0)).toInt()

                        if (samplesUntilNextRow <= 0) {
                            // Trigger current phrase row across all 8 tracks
                            triggerCurrentRow()

                            // Compute swing delay for next row
                            val nextPhraseRow = (phraseRow + 1) % 16
                            val swingDelay = synth.getSwingDelaySamples(nextPhraseRow, bpm)
                            samplesUntilNextRow = rowDurationSamples + swingDelay

                            // Advance sequencer position
                            advanceSequencer()
                        }

                        val pcm = synth.generateChunk()
                        localAudioPlayer.write(pcm)
                        samplesUntilNextRow -= M8Synth.CHUNK_SAMPLES
                    } else {
                        if (wasPlaying) {
                            wasPlaying = false
                            synth.allNotesOff()
                            fxEngine.reset()
                            samplesUntilNextRow = 0
                        }
                        val pcm = synth.generateSilence()
                        localAudioPlayer.write(pcm)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("M8Audio", "Audio error: ${e.message}", e)
                    delay(16)
                }
            }
        }
    }

    /**
     * Trigger the current phrase row across all 8 tracks.
     * Resolves: songGrid[songRow][track] -> chain -> phrase -> PhraseStep
     * Converts PhraseStep to the IntArray format M8Synth.triggerRow expects.
     */
    private fun triggerCurrentRow() {
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

        synth.triggerRow(rowData)
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
        emulatorRenderJob?.cancel()
        emulatorRenderJob = null
        audioRenderJob?.cancel()
        audioRenderJob = null
        localAudioPlayer.stop()
        synth.allNotesOff()
    }

    fun sendKeyState(keys: Int) {
        emulator.handleKeyState(keys)
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulator()
    }
}
