// app/src/main/java/com/m8/emulator/M8Synth.kt
package com.m8.emulator

import android.util.Log
import com.m8.audio.M8AudioPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Simple, stable, loud synthesizer for the M8 tracker emulator.
 * 8 voices with naive oscillators, one-pole lowpass filter, stereo delay,
 * and fast soft-clip output. No complex DSP -- just clear, audible audio.
 */
class M8Synth {

    companion object {
        const val SAMPLE_RATE = M8AudioPlayer.SAMPLE_RATE // 44100
        const val CHANNELS = 2
        const val CHUNK_SAMPLES = 735
        const val WAVEFORM_CAPTURE_SIZE = 320
        const val NOTE_OFF = 0xFF
        private const val TWO_PI = 2.0 * Math.PI
        private const val TAG = "M8Synth"

        // Envelope rates in per-sample increments
        private const val ATTACK_RATE = 1.0 / (SAMPLE_RATE * 0.005)   // 5ms attack
        private const val RELEASE_RATE = 1.0 / (SAMPLE_RATE * 0.100)  // 100ms release

        // Stereo delay: 300ms circular buffer
        private const val DELAY_SAMPLES = (SAMPLE_RATE * 0.3).toInt()
        private const val DELAY_FEEDBACK = 0.4
        private const val DELAY_MIX = 0.3

        // Output buffer size in bytes (16-bit stereo)
        private const val CHUNK_BYTES = CHUNK_SAMPLES * CHANNELS * 2

        // Hardcoded stereo pan per track (0.0=full left, 1.0=full right)
        private val TRACK_PAN = doubleArrayOf(0.15, 0.3, 0.7, 0.35, 0.65, 0.25, 0.5, 0.8)

        // Default waveform per track: 0=saw, 1=pulse, 2=sine, 3=tri, 4=noise, 5=fm_bell
        private val TRACK_WAVEFORM = intArrayOf(0, 0, 3, 4, 5, 1, 2, 4)

        // Default one-pole filter cutoff coefficient per track
        private val TRACK_CUTOFF = doubleArrayOf(0.3, 0.15, 0.5, 0.4, 0.9, 0.25, 0.95, 0.6)

        fun noteToFreq(midiNote: Int): Double {
            return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
        }
    }

    // --- Public fields consumed by M8ViewModel for visualization ---
    val trackLevels = DoubleArray(8)
    var masterLevelL = 0.0
    var masterLevelR = 0.0
    var swingAmount = 0.15
    var mixerSettings: MixerSettings? = null
    var fxEngine: M8FxEngine? = null

    // --- Internal state ---
    private val voices = Array(8) { Voice(it) }
    private val random = java.util.Random()
    private val delayBufL = DoubleArray(DELAY_SAMPLES)
    private val delayBufR = DoubleArray(DELAY_SAMPLES)
    private var delayWritePos = 0
    private val waveformBuf = DoubleArray(WAVEFORM_CAPTURE_SIZE)
    private var waveformWriteIdx = 0
    private var debugCount = 0L
    private val silenceBytes = ByteArray(CHUNK_BYTES)

    // --- Voice ---

    /** Simple voice: oscillator + one-pole filter + linear AR envelope. */
    private inner class Voice(val track: Int) {
        var frequency = 0.0
        var phase = 0.0
        var volume = 0.0
        var active = false
        var envelope = 0.0
        var noteOn = false
        var waveform = TRACK_WAVEFORM[track]
        var filterAlpha = TRACK_CUTOFF[track]
        var filterState = 0.0
        var fmPhase = 0.0

        fun reset() {
            frequency = 0.0; phase = 0.0; volume = 0.0
            active = false; envelope = 0.0; noteOn = false
            filterState = 0.0; fmPhase = 0.0
        }

        /** Generate one sample. Returns value in roughly -1..1 range. */
        fun generateSample(sampleIndex: Int): Double {
            if (!active) return 0.0

            // Envelope
            if (noteOn) {
                envelope = min(1.0, envelope + ATTACK_RATE)
            } else {
                envelope = max(0.0, envelope - RELEASE_RATE)
                if (envelope <= 0.0) { active = false; return 0.0 }
            }

            // Frequency with optional FX modulation (arpeggio, vibrato, etc.)
            val freq = fxEngine?.getFreqModifier(track, frequency, sampleIndex)
                ?: frequency

            // Oscillator
            val raw = when (waveform) {
                0 -> 2.0 * phase - 1.0                           // Saw
                1 -> if (phase < 0.5) 1.0 else -1.0              // Pulse 50%
                2 -> sin(phase * TWO_PI)                          // Sine
                3 -> 4.0 * abs(phase - 0.5) - 1.0                // Triangle
                4 -> random.nextDouble() * 2.0 - 1.0             // Noise
                5 -> {                                            // FM bell
                    val mod = sin(fmPhase * TWO_PI * 3.0) * 2.0
                    val out = sin(phase * TWO_PI + mod)
                    fmPhase += freq / SAMPLE_RATE
                    if (fmPhase >= 1.0) fmPhase -= 1.0
                    out
                }
                else -> 0.0
            }

            // Advance oscillator phase
            phase += freq / SAMPLE_RATE
            if (phase >= 1.0) phase -= 1.0
            if (phase < 0.0) phase = 0.0

            // One-pole lowpass: y += alpha * (x - y)
            filterState += filterAlpha * (raw - filterState)
            filterState = filterState.coerceIn(-2.0, 2.0)

            // Scale by envelope and velocity — clean gain, no clipping
            return filterState * envelope * volume
        }
    }

    // --- Public API ---

    /** Map an M8Instrument to our simple voice parameters. */
    fun configureVoice(track: Int, inst: M8Instrument) {
        if (track !in 0..7) return
        val v = voices[track]
        when (inst.type) {
            InstrumentType.WAVSYNTH -> {
                v.waveform = when (inst.wavSynth.shape) {
                    WavShape.SAW -> 0
                    WavShape.PULSE_12, WavShape.PULSE_25,
                    WavShape.PULSE_50, WavShape.PULSE_75 -> 1
                    WavShape.SINE -> 2
                    WavShape.TRIANGLE -> 3
                    WavShape.NOISE, WavShape.NOISE_PITCH -> 4
                    WavShape.OVERFLOW -> 0
                }
                v.filterAlpha = inst.filter.cutoff / 255.0 * 0.98 + 0.01
            }
            InstrumentType.FM_SYNTH -> {
                v.waveform = 5
                v.filterAlpha = inst.filter.cutoff / 255.0 * 0.98 + 0.01
            }
            else -> {
                v.filterAlpha = inst.filter.cutoff / 255.0 * 0.98 + 0.01
            }
        }
    }

    /** Alias for configureVoice -- called per-row when instrument changes. */
    fun applyInstrument(trackIndex: Int, instrument: M8Instrument) {
        configureVoice(trackIndex, instrument)
    }

    /** Return the current frequency of a voice (used by FX engine). */
    fun getVoiceFreq(track: Int): Double {
        if (track !in 0..7) return 0.0
        return voices[track].frequency
    }

    /**
     * Trigger notes from the current sequencer row.
     * Each element: [note, instrument, volume, fx1, fx2].
     * note 0 = continue, NOTE_OFF = release, 1-127 = MIDI note.
     */
    fun triggerRow(rowData: Array<IntArray>) {
        for (t in 0 until 8) {
            val data = rowData[t]
            val note = data[0]
            val vol = data[2]
            val voice = voices[t]

            when {
                note == NOTE_OFF -> {
                    voice.noteOn = false
                }
                note in 1..127 -> {
                    voice.frequency = noteToFreq(note)
                    voice.volume = if (vol > 0) vol / 255.0 else 0.8
                    voice.phase = 0.0
                    voice.fmPhase = 0.0
                    voice.envelope = 0.0
                    voice.noteOn = true
                    voice.active = true
                    voice.filterState = 0.0
                }
                // note == 0: continue playing -- no state change
            }
        }
    }

    /** Generate one chunk of 16-bit stereo PCM audio. */
    fun generateChunk(): ByteArray {
        val buf = ByteBuffer.allocate(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        var peakL = 0.0
        var peakR = 0.0
        val localTrackPeaks = DoubleArray(8)

        for (i in 0 until CHUNK_SAMPLES) {
            var mixL = 0.0
            var mixR = 0.0

            for (t in 0 until 8) {
                val sample = voices[t].generateSample(i)

                // Per-track pan and volume from mixer (or hardcoded defaults)
                val pan = mixerSettings?.let { it.trackPans[t] / 255.0 } ?: TRACK_PAN[t]
                val trackVol = mixerSettings?.let { it.trackVolumes[t] / 255.0 } ?: 1.0

                val scaled = sample * trackVol
                mixL += scaled * (1.0 - pan)
                mixR += scaled * pan

                val peak = abs(scaled)
                if (peak > localTrackPeaks[t]) localTrackPeaks[t] = peak
            }

            // Stereo delay effect
            val readPos = (delayWritePos + 1) % DELAY_SAMPLES
            val delayL = delayBufL[readPos]
            val delayR = delayBufR[readPos]
            delayBufL[delayWritePos] = (mixL + delayL * DELAY_FEEDBACK).coerceIn(-2.0, 2.0)
            delayBufR[delayWritePos] = (mixR + delayR * DELAY_FEEDBACK).coerceIn(-2.0, 2.0)
            delayWritePos = (delayWritePos + 1) % DELAY_SAMPLES

            // Mix dry + delay wet
            var outL = mixL + delayL * DELAY_MIX
            var outR = mixR + delayR * DELAY_MIX

            // Master gain: scale to fill 16-bit range well
            // With 8 voices each producing ~0.5 peak, sum is ~2.0 per channel
            // Scale down to ~0.8 peak for headroom
            outL *= 0.35
            outR *= 0.35

            // Gentle transparent limiter: only acts above 0.9, preserves signal below
            fun softLimit(x: Double): Double {
                val ax = abs(x)
                return if (ax < 0.9) x
                else sign(x) * (0.9 + 0.1 * tanh((ax - 0.9) * 10.0))
            }
            outL = softLimit(outL)
            outR = softLimit(outR)

            if (abs(outL) > peakL) peakL = abs(outL)
            if (abs(outR) > peakR) peakR = abs(outR)

            // Waveform capture
            if (waveformWriteIdx < WAVEFORM_CAPTURE_SIZE) {
                waveformBuf[waveformWriteIdx] = (outL + outR) * 0.5
                waveformWriteIdx++
            }

            // 16-bit PCM — use full range
            val sL = (outL * 32000.0).toInt().coerceIn(-32768, 32767)
            val sR = (outR * 32000.0).toInt().coerceIn(-32768, 32767)
            buf.putShort(sL.toShort())
            buf.putShort(sR.toShort())
        }

        // Smoothed peak meters for visualization
        masterLevelL = masterLevelL * 0.7 + peakL * 0.3
        masterLevelR = masterLevelR * 0.7 + peakR * 0.3
        for (t in 0 until 8) {
            trackLevels[t] = trackLevels[t] * 0.7 + localTrackPeaks[t] * 0.3
        }

        waveformWriteIdx = 0

        debugCount++
        if (debugCount % 60 == 0L) {
            val active = voices.count { it.active }
            Log.d(TAG, "chunk=$debugCount active=$active L=$masterLevelL R=$masterLevelR")
        }

        return buf.array()
    }

    /** Return a chunk of silence (same size as generateChunk output). */
    fun generateSilence(): ByteArray {
        return silenceBytes.clone()
    }

    /** Release all voices immediately. */
    fun allNotesOff() {
        for (voice in voices) {
            voice.noteOn = false
        }
        masterLevelL = 0.0
        masterLevelR = 0.0
        for (i in trackLevels.indices) trackLevels[i] = 0.0
    }

    /** Return 320 bytes of waveform data (0-255) for oscilloscope display. */
    fun getWaveformData(): ByteArray {
        val out = ByteArray(WAVEFORM_CAPTURE_SIZE)
        for (i in 0 until WAVEFORM_CAPTURE_SIZE) {
            val v = ((waveformBuf[i] + 1.0) * 0.5 * 255.0).toInt().coerceIn(0, 255)
            out[i] = v.toByte()
        }
        return out
    }

    /** Calculate swing delay in samples for BPM-synced timing. */
    fun getSwingDelaySamples(row: Int, bpm: Int): Int {
        if (row % 2 != 0) return 0
        val rowDuration = SAMPLE_RATE * 60.0 / (bpm * 4.0)
        return (rowDuration * swingAmount).toInt()
    }
}
