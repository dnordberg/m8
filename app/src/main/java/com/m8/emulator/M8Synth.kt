package com.m8.emulator

import com.m8.audio.M8AudioPlayer
import kotlin.math.*

/**
 * Simple polyphonic synthesizer for the M8 emulator.
 *
 * Generates 16-bit stereo PCM at 44100Hz, driven by the tracker's
 * phrase data. Supports multiple waveforms, envelopes, and a
 * basic low-pass filter for a warm chiptune-style sound.
 */
class M8Synth {

    companion object {
        const val SAMPLE_RATE = M8AudioPlayer.SAMPLE_RATE // 44100
        const val CHANNELS = 2
        const val CHUNK_SAMPLES = 735 // ~16.7ms per chunk (one frame at 60fps, two at 30fps)

        // Waveform types per track
        private val TRACK_WAVEFORMS = intArrayOf(
            WAVE_PULSE,     // Track 0: lead (pulse wave)
            WAVE_SAW,       // Track 1: bass (sawtooth)
            WAVE_TRIANGLE,  // Track 2: pad (triangle)
            WAVE_NOISE,     // Track 3: percussion (noise)
            WAVE_SINE,      // Track 4
            WAVE_PULSE,     // Track 5
            WAVE_SAW,       // Track 6
            WAVE_TRIANGLE,  // Track 7
        )

        const val WAVE_SINE = 0
        const val WAVE_SAW = 1
        const val WAVE_PULSE = 2
        const val WAVE_TRIANGLE = 3
        const val WAVE_NOISE = 4

        // MIDI note to frequency
        fun noteToFreq(midiNote: Int): Double {
            // M8 note 1 = C-0 (MIDI ~24), so offset accordingly
            val midi = midiNote + 23
            return 440.0 * 2.0.pow((midi - 69) / 12.0)
        }
    }

    // Per-voice state (8 tracks)
    private val voices = Array(8) { i -> Voice(i) }

    // Master phase for noise LFSR
    private var noiseLfsr = 0x7FFF

    // Simple low-pass filter state per channel
    private var lpfL = 0.0
    private var lpfR = 0.0
    private val lpfCutoff = 0.6 // 0.0 = no filter, 1.0 = fully open

    /**
     * Trigger notes from the current tracker row.
     * Call this once per row advance.
     *
     * @param rowData Array of 8 tracks, each IntArray(note, instrument, volume, fx, fx2)
     */
    fun triggerRow(rowData: Array<IntArray>) {
        for (track in rowData.indices) {
            if (track >= voices.size) break
            val (note, _, vol, _, _) = rowData[track]
            val voice = voices[track]

            if (note > 0) {
                voice.frequency = noteToFreq(note)
                voice.volume = (vol and 0xFF) / 255.0
                voice.envelope = 1.0
                voice.active = true
                voice.waveform = TRACK_WAVEFORMS.getOrElse(track) { WAVE_SINE }
            }
            // note == 0 means no trigger, let existing note continue with envelope decay
        }
    }

    /**
     * Generate one chunk of stereo PCM audio.
     * Returns a ByteArray of 16-bit LE stereo samples.
     */
    fun generateChunk(): ByteArray {
        val numSamples = CHUNK_SAMPLES
        val buffer = ByteArray(numSamples * CHANNELS * 2) // 16-bit stereo

        for (i in 0 until numSamples) {
            var mixL = 0.0
            var mixR = 0.0

            for (v in voices) {
                if (!v.active || v.envelope < 0.001) {
                    v.active = false
                    continue
                }

                val sample = when (v.waveform) {
                    WAVE_SINE -> sin(v.phase * 2.0 * PI)
                    WAVE_SAW -> 2.0 * (v.phase - floor(v.phase + 0.5))
                    WAVE_PULSE -> if (v.phase % 1.0 < 0.5) 1.0 else -1.0
                    WAVE_TRIANGLE -> 4.0 * abs(v.phase % 1.0 - 0.5) - 1.0
                    WAVE_NOISE -> {
                        // Linear feedback shift register noise
                        val bit = (noiseLfsr xor (noiseLfsr shr 1)) and 1
                        noiseLfsr = (noiseLfsr shr 1) or (bit shl 14)
                        (noiseLfsr and 1) * 2.0 - 1.0
                    }
                    else -> 0.0
                }

                val out = sample * v.volume * v.envelope * 0.15 // Master gain per voice

                // Simple stereo pan based on track index
                val pan = (v.trackIndex.toDouble() / 7.0) * 0.6 + 0.2 // 0.2 to 0.8
                mixL += out * (1.0 - pan)
                mixR += out * pan

                // Advance phase
                v.phase += v.frequency / SAMPLE_RATE
                if (v.phase > 1e6) v.phase -= 1e6 // Prevent overflow

                // Envelope decay
                v.envelope *= 0.9997 // Slow decay ~3 seconds
            }

            // Simple one-pole low-pass filter
            lpfL += lpfCutoff * (mixL - lpfL)
            lpfR += lpfCutoff * (mixR - lpfR)

            // Soft clip
            val outL = softClip(lpfL)
            val outR = softClip(lpfR)

            // Convert to 16-bit LE
            val sL = (outL * 32767).toInt().coerceIn(-32768, 32767)
            val sR = (outR * 32767).toInt().coerceIn(-32768, 32767)

            val offset = i * 4
            buffer[offset] = (sL and 0xFF).toByte()
            buffer[offset + 1] = (sL shr 8 and 0xFF).toByte()
            buffer[offset + 2] = (sR and 0xFF).toByte()
            buffer[offset + 3] = (sR shr 8 and 0xFF).toByte()
        }

        return buffer
    }

    /**
     * Generate silence (when tracker is stopped).
     */
    fun generateSilence(): ByteArray {
        return ByteArray(CHUNK_SAMPLES * CHANNELS * 2)
    }

    /**
     * Kill all voices immediately.
     */
    fun allNotesOff() {
        for (v in voices) {
            v.active = false
            v.envelope = 0.0
        }
        lpfL = 0.0
        lpfR = 0.0
    }

    private fun softClip(x: Double): Double {
        return if (x > 1.0) 1.0 - 1.0 / (x + 1.0)
        else if (x < -1.0) -1.0 + 1.0 / (-x + 1.0)
        else x
    }

    /**
     * Per-voice state.
     */
    class Voice(val trackIndex: Int) {
        var frequency = 0.0
        var phase = 0.0
        var volume = 0.0
        var envelope = 0.0
        var active = false
        var waveform = WAVE_SINE
    }
}
