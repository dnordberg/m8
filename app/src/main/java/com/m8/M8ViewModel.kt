package com.m8

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8.audio.M8AudioPlayer
import com.m8.emulator.M8Emulator
import com.m8.emulator.M8Synth
import com.m8.network.ConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Local-only M8 emulator ViewModel.
 * Runs M8Emulator in-process with synthesizer — no network needed.
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
    private var emulatorRenderJob: Job? = null
    private var audioRenderJob: Job? = null

    // BPM-synced row timing
    @Volatile private var samplesUntilNextRow = 0
    @Volatile private var wasPlaying = false

    fun startLocalEmulator() {
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

        // Audio render loop — owns BPM timing, row advance, and song progression
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
                            emulator.playRow = 0
                        }

                        val bpm = emulator.bpm.coerceIn(60, 300)
                        val rowDurationSamples = (M8Synth.SAMPLE_RATE * 60.0 / (bpm * 4.0)).toInt()

                        if (samplesUntilNextRow <= 0) {
                            val row = emulator.playRow.coerceIn(0, 15)
                            val activePhrase = emulator.getActivePhrase()
                            synth.triggerRow(activePhrase[row])

                            val nextRow = (row + 1) % 16
                            val swingDelay = synth.getSwingDelaySamples(nextRow, bpm)
                            samplesUntilNextRow = rowDurationSamples + swingDelay

                            emulator.playRow = nextRow
                            if (nextRow == 0) {
                                emulator.advancePattern()
                            }
                        }

                        val pcm = synth.generateChunk()
                        localAudioPlayer.write(pcm)
                        samplesUntilNextRow -= M8Synth.CHUNK_SAMPLES
                    } else {
                        if (wasPlaying) {
                            wasPlaying = false
                            synth.allNotesOff()
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
