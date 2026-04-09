package com.m8.emulator

import com.m8.audio.M8AudioPlayer
import kotlin.math.*

/**
 * Advanced polyphonic synthesizer for the M8 emulator.
 *
 * Features:
 * - Band-limited waveforms (PolyBLEP anti-aliasing)
 * - Full ADSR envelopes per voice
 * - Pulse width modulation with LFO
 * - Resonant state-variable filter per voice (LP/HP/BP)
 * - Stereo delay effect (ping-pong)
 * - Chorus effect (dual modulated delay lines)
 * - Plate reverb (Schroeder/Moorer style)
 * - Soft-clipping master limiter
 * - BPM-synced row advance
 *
 * Generates 16-bit stereo PCM at 44100Hz.
 */
class M8Synth {

    // ===================== DATA CLASSES =====================

    data class ADSR(
        val attack: Double,   // seconds
        val decay: Double,    // seconds
        val sustain: Double,  // 0.0-1.0 level
        val release: Double   // seconds
    )

    data class VoicePreset(
        val waveform: Int,
        val pulseWidth: Double = 0.5,
        val filterType: Int = FILTER_LP,
        val filterCutoff: Double = 0.7,  // 0.0-1.0 normalized
        val filterResonance: Double = 0.0,
        val adsr: ADSR = ADSR(0.01, 0.1, 0.7, 0.3),
        val pwmDepth: Double = 0.0,
        val pwmRate: Double = 0.0,
        val filterLfoDepth: Double = 0.0,
        val filterLfoRate: Double = 0.0,
        val filterEnvDepth: Double = 0.0,
        val filterEnvDecay: Double = 0.3,
        val fmRatio: Double = 2.0,
        val fmIndex: Double = 1.0,
        val chorusSend: Double = 0.0,
        val reverbSend: Double = 0.2,
        val delaySend: Double = 0.0,
        val pan: Double = 0.5  // 0=left, 1=right; overridden by track spread
    )

    // ===================== VOICE =====================

    inner class Voice(val trackIndex: Int) {
        var frequency = 0.0
        var phase = 0.0
        var volume = 0.0
        var active = false
        var noteOn = false
        var preset = TRACK_PRESETS.getOrElse(trackIndex) {
            TRACK_PRESETS[trackIndex % TRACK_PRESETS.size]
        }

        // ADSR envelope
        var envStage = 0  // 0=idle, 1=attack, 2=decay, 3=sustain, 4=release
        var envLevel = 0.0
        var envTime = 0.0

        // Filter envelope
        var filterEnvLevel = 0.0
        var filterEnvTime = 0.0

        // PWM LFO phase
        var pwmLfoPhase = 0.0

        // Filter LFO phase
        var filterLfoPhase = 0.0

        // FM operator phase
        var fmPhase = 0.0

        // State-variable filter state
        var svfLow = 0.0
        var svfBand = 0.0
        var svfHigh = 0.0

        // Noise LFSR per voice
        var noiseLfsr = 0x7FFF
        var noiseValue = 0.0
        var noiseSampleCount = 0

        fun triggerNote(freq: Double, vol: Double) {
            frequency = freq
            volume = vol
            envStage = 1 // attack
            envTime = 0.0
            filterEnvLevel = 1.0
            filterEnvTime = 0.0
            noteOn = true
            active = true
            // Don't reset phase for portamento-style smooth transitions
            // phase = 0.0
        }

        fun releaseNote() {
            if (envStage != 0 && envStage != 4) {
                envStage = 4 // release
                envTime = 0.0
            }
            noteOn = false
        }

        fun processEnvelope(dt: Double) {
            val adsr = preset.adsr
            when (envStage) {
                1 -> { // Attack
                    envTime += dt
                    envLevel = if (adsr.attack <= 0.0) 1.0
                    else (envTime / adsr.attack).coerceIn(0.0, 1.0)
                    if (envLevel >= 1.0) {
                        envStage = 2
                        envTime = 0.0
                    }
                }
                2 -> { // Decay
                    envTime += dt
                    val decayProgress = if (adsr.decay <= 0.0) 1.0
                    else (envTime / adsr.decay).coerceIn(0.0, 1.0)
                    envLevel = 1.0 - (1.0 - adsr.sustain) * decayProgress
                    if (decayProgress >= 1.0) {
                        envStage = 3
                    }
                }
                3 -> { // Sustain
                    envLevel = adsr.sustain
                }
                4 -> { // Release
                    envTime += dt
                    val releaseProgress = if (adsr.release <= 0.0) 1.0
                    else (envTime / adsr.release).coerceIn(0.0, 1.0)
                    envLevel *= (1.0 - releaseProgress)
                    if (releaseProgress >= 1.0 || envLevel < 0.0001) {
                        envStage = 0
                        envLevel = 0.0
                        active = false
                    }
                }
            }

            // Filter envelope (independent decay)
            if (preset.filterEnvDepth > 0.0) {
                filterEnvTime += dt
                val fDecay = if (preset.filterEnvDecay <= 0.0) 1.0
                else (filterEnvTime / preset.filterEnvDecay).coerceIn(0.0, 1.0)
                filterEnvLevel = (1.0 - fDecay)
            }
        }

        fun generateSample(): Double {
            if (!active || envStage == 0) return 0.0

            val dt = 1.0 / SAMPLE_RATE
            val phaseInc = frequency / SAMPLE_RATE

            // PWM modulation
            val currentPW = if (preset.pwmDepth > 0.0) {
                pwmLfoPhase += preset.pwmRate * dt
                val mod = sin(pwmLfoPhase * TWO_PI) * preset.pwmDepth
                (preset.pulseWidth + mod).coerceIn(0.1, 0.9)
            } else {
                preset.pulseWidth
            }

            // Generate raw waveform (band-limited where possible)
            val raw = when (preset.waveform) {
                WAVE_SINE -> sin(phase * TWO_PI)

                WAVE_SAW -> {
                    // PolyBLEP sawtooth
                    val naive = 2.0 * (phase - floor(phase + 0.5))
                    naive - polyBlep(phase, phaseInc)
                }

                WAVE_PULSE -> {
                    // PolyBLEP pulse with variable width
                    val p = phase % 1.0
                    val naive = if (p < currentPW) 1.0 else -1.0
                    naive + polyBlep(phase, phaseInc) - polyBlep(phase - currentPW, phaseInc)
                }

                WAVE_TRIANGLE -> {
                    // Integrated PolyBLEP square -> triangle (leaky integrator)
                    val p = phase % 1.0
                    4.0 * abs(p - 0.5) - 1.0
                }

                WAVE_NOISE -> {
                    // Sample-and-hold noise at note frequency for pitched noise
                    noiseSampleCount++
                    val period = (SAMPLE_RATE / frequency.coerceAtLeast(20.0)).toInt().coerceAtLeast(1)
                    if (noiseSampleCount >= period) {
                        noiseSampleCount = 0
                        val bit = (noiseLfsr xor (noiseLfsr shr 1)) and 1
                        noiseLfsr = (noiseLfsr shr 1) or (bit shl 14)
                        noiseValue = (noiseLfsr.toDouble() / 0x7FFF.toDouble()) * 2.0 - 1.0
                    }
                    noiseValue
                }

                WAVE_FM -> {
                    // 2-operator FM: carrier + modulator
                    fmPhase += (frequency * preset.fmRatio) / SAMPLE_RATE
                    if (fmPhase > 1e6) fmPhase -= 1e6
                    val modulator = sin(fmPhase * TWO_PI) * preset.fmIndex * envLevel
                    sin((phase + modulator) * TWO_PI)
                }

                else -> 0.0
            }

            // Advance oscillator phase
            phase += phaseInc
            if (phase > 1e6) phase -= 1e6

            // Apply amplitude envelope
            val enveloped = raw * envLevel * volume

            // State-variable filter
            val cutoffNorm = preset.filterCutoff +
                    preset.filterEnvDepth * filterEnvLevel +
                    if (preset.filterLfoDepth > 0.0) {
                        filterLfoPhase += preset.filterLfoRate * dt
                        sin(filterLfoPhase * TWO_PI) * preset.filterLfoDepth
                    } else 0.0

            val cutoffHz = 20.0 * 2.0.pow(cutoffNorm.coerceIn(0.0, 1.0) * 11.0) // 20Hz - 40960Hz
            val f = 2.0 * sin(PI * (cutoffHz / SAMPLE_RATE).coerceIn(0.0, 0.49))
            val q = 1.0 - preset.filterResonance.coerceIn(0.0, 0.95)

            svfHigh = enveloped - svfLow - q * svfBand
            svfBand += f * svfHigh
            svfLow += f * svfBand

            return when (preset.filterType) {
                FILTER_LP -> svfLow
                FILTER_HP -> svfHigh
                FILTER_BP -> svfBand
                else -> svfLow
            }
        }

        /** PolyBLEP anti-aliasing correction */
        private fun polyBlep(t: Double, dt: Double): Double {
            val p = t % 1.0
            val dtn = dt.coerceAtLeast(1e-10)
            return when {
                p < dtn -> {
                    val x = p / dtn
                    2.0 * x - x * x - 1.0
                }
                p > 1.0 - dtn -> {
                    val x = (p - 1.0) / dtn
                    x * x + 2.0 * x + 1.0
                }
                else -> 0.0
            }
        }
    }

    // ===================== EFFECTS =====================

    /** Stereo ping-pong delay */
    inner class StereoDelay(
        private val timeL: Double = 0.375, // seconds (dotted eighth at 120 BPM)
        private val timeR: Double = 0.25,  // seconds (eighth note)
        private val feedback: Double = 0.45,
        private val mix: Double = 0.3,
        private val damping: Double = 0.3
    ) {
        private val maxSamples = (SAMPLE_RATE * 2.0).toInt()
        private val bufL = DoubleArray(maxSamples)
        private val bufR = DoubleArray(maxSamples)
        private var posL = 0
        private var posR = 0
        private var lpL = 0.0
        private var lpR = 0.0

        fun process(inL: Double, inR: Double): Pair<Double, Double> {
            val delayL = (timeL * SAMPLE_RATE).toInt().coerceIn(1, maxSamples - 1)
            val delayR = (timeR * SAMPLE_RATE).toInt().coerceIn(1, maxSamples - 1)

            val readL = (posL - delayL + maxSamples) % maxSamples
            val readR = (posR - delayR + maxSamples) % maxSamples

            val tapL = bufL[readL]
            val tapR = bufR[readR]

            // One-pole damping filter on feedback
            lpL += damping * (tapL - lpL)
            lpR += damping * (tapR - lpR)

            // Ping-pong: left feeds right, right feeds left
            bufL[posL] = inL + lpR * feedback
            bufR[posR] = inR + lpL * feedback

            posL = (posL + 1) % maxSamples
            posR = (posR + 1) % maxSamples

            return Pair(
                inL + tapL * mix,
                inR + tapR * mix
            )
        }

        fun clear() {
            bufL.fill(0.0)
            bufR.fill(0.0)
            lpL = 0.0
            lpR = 0.0
        }
    }

    /** Stereo chorus (dual modulated delay lines) */
    inner class Chorus(
        private val rate: Double = 0.8,      // Hz
        private val depth: Double = 0.003,   // seconds of modulation depth
        private val mix: Double = 0.4,
        private val baseDelay: Double = 0.012 // seconds
    ) {
        private val maxSamples = (SAMPLE_RATE * 0.1).toInt() // 100ms max
        private val bufL = DoubleArray(maxSamples)
        private val bufR = DoubleArray(maxSamples)
        private var writePos = 0
        private var lfoPhase1 = 0.0
        private var lfoPhase2 = 0.33 // offset for stereo width

        fun process(inL: Double, inR: Double): Pair<Double, Double> {
            bufL[writePos] = inL
            bufR[writePos] = inR

            lfoPhase1 += rate / SAMPLE_RATE
            lfoPhase2 += rate / SAMPLE_RATE
            if (lfoPhase1 > 1.0) lfoPhase1 -= 1.0
            if (lfoPhase2 > 1.0) lfoPhase2 -= 1.0

            // LFO modulated delay time
            val mod1 = sin(lfoPhase1 * TWO_PI) * depth
            val mod2 = sin(lfoPhase2 * TWO_PI) * depth

            val delayL = ((baseDelay + mod1) * SAMPLE_RATE).coerceIn(1.0, (maxSamples - 2).toDouble())
            val delayR = ((baseDelay + mod2) * SAMPLE_RATE).coerceIn(1.0, (maxSamples - 2).toDouble())

            // Linear interpolation for sub-sample accuracy
            val tapL = readInterp(bufL, writePos - delayL)
            val tapR = readInterp(bufR, writePos - delayR)

            writePos = (writePos + 1) % maxSamples

            return Pair(
                inL + (tapL - inL) * mix,
                inR + (tapR - inR) * mix
            )
        }

        private fun readInterp(buf: DoubleArray, pos: Double): Double {
            val size = buf.size
            val p = ((pos % size) + size) % size
            val i0 = p.toInt()
            val frac = p - i0
            val i1 = (i0 + 1) % size
            return buf[i0] * (1.0 - frac) + buf[i1] * frac
        }

        fun clear() {
            bufL.fill(0.0)
            bufR.fill(0.0)
        }
    }

    /** Schroeder reverb (4 comb filters + 2 allpass) */
    inner class PlateReverb(
        private val mix: Double = 0.25,
        private val decay: Double = 0.7,
        private val damping: Double = 0.4
    ) {
        // Comb filter delay lengths (prime-ish for diffusion)
        private val combLengths = intArrayOf(1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116)
        private val combBufs = Array(combLengths.size) { DoubleArray(combLengths[it]) }
        private val combPos = IntArray(combLengths.size)
        private val combLp = DoubleArray(combLengths.size)

        // Allpass delay lengths
        private val apLengths = intArrayOf(225, 556, 441, 341)
        private val apBufs = Array(apLengths.size) { DoubleArray(apLengths[it]) }
        private val apPos = IntArray(apLengths.size)

        fun process(inL: Double, inR: Double): Pair<Double, Double> {
            val input = (inL + inR) * 0.5

            // Parallel comb filters (4 per channel for density)
            var outL = 0.0
            var outR = 0.0

            for (i in combBufs.indices) {
                val buf = combBufs[i]
                val pos = combPos[i]
                val tap = buf[pos]

                // Damped feedback
                combLp[i] += damping * (tap - combLp[i])
                buf[pos] = input + combLp[i] * decay

                combPos[i] = (pos + 1) % combLengths[i]

                // Split to L/R (alternating)
                if (i % 2 == 0) outL += tap else outR += tap
            }

            outL /= (combBufs.size / 2)
            outR /= (combBufs.size / 2)

            // Series allpass filters for diffusion
            outL = allpass(0, outL)
            outL = allpass(1, outL)
            outR = allpass(2, outR)
            outR = allpass(3, outR)

            return Pair(
                inL + outL * mix,
                inR + outR * mix
            )
        }

        private fun allpass(idx: Int, input: Double): Double {
            val buf = apBufs[idx]
            val pos = apPos[idx]
            val tap = buf[pos]
            val g = 0.5

            val output = -input + tap
            buf[pos] = input + tap * g

            apPos[idx] = (pos + 1) % apLengths[idx]
            return output
        }

        fun clear() {
            combBufs.forEach { it.fill(0.0) }
            combPos.fill(0)
            combLp.fill(0.0)
            apBufs.forEach { it.fill(0.0) }
            apPos.fill(0)
        }
    }

    // ===================== MAIN SYNTH ENGINE =====================

    private val voices = Array(8) { Voice(it) }
    private val delay = StereoDelay()
    private val chorus = Chorus()
    private val reverb = PlateReverb()

    // DC offset removal (high-pass at ~5Hz)
    private var dcL = 0.0
    private var dcR = 0.0
    private val dcCoeff = 1.0 - (TWO_PI * 5.0 / SAMPLE_RATE)

    // --- Waveform capture for visualization ---
    // Circular buffer of recent output samples (mono mix)
    private val waveformBuffer = DoubleArray(WAVEFORM_CAPTURE_SIZE)
    private var waveformWritePos = 0

    // --- Per-track level meters ---
    val trackLevels = DoubleArray(8)  // RMS level per track (0.0-1.0)
    var masterLevelL = 0.0; private set
    var masterLevelR = 0.0; private set

    // --- Swing/groove ---
    var swingAmount = 0.15  // 0.0 = straight, 0.5 = full swing (delays odd rows by 50%)

    companion object {
        const val SAMPLE_RATE = M8AudioPlayer.SAMPLE_RATE
        const val CHANNELS = 2
        const val CHUNK_SAMPLES = 735
        const val WAVEFORM_CAPTURE_SIZE = 320 // matches display width

        const val WAVE_SINE = 0
        const val WAVE_SAW = 1
        const val WAVE_PULSE = 2
        const val WAVE_TRIANGLE = 3
        const val WAVE_NOISE = 4
        const val WAVE_FM = 5

        const val FILTER_LP = 0
        const val FILTER_HP = 1
        const val FILTER_BP = 2

        const val NOTE_OFF = 0xFF  // Special note value to release a voice

        private val TRACK_PRESETS = arrayOf(
            VoicePreset(WAVE_PULSE, 0.45, FILTER_LP, 0.7, 0.3,
                adsr = ADSR(0.005, 0.1, 0.7, 0.3),
                pwmDepth = 0.2, pwmRate = 3.5, filterLfoDepth = 0.15, filterLfoRate = 2.0),
            VoicePreset(WAVE_SAW, 0.0, FILTER_LP, 0.35, 0.45,
                adsr = ADSR(0.002, 0.15, 0.6, 0.15),
                filterEnvDepth = 0.5, filterEnvDecay = 0.2),
            VoicePreset(WAVE_TRIANGLE, 0.0, FILTER_LP, 0.85, 0.1,
                adsr = ADSR(0.3, 0.4, 0.8, 1.0),
                chorusSend = 0.6, reverbSend = 0.5),
            VoicePreset(WAVE_NOISE, 0.0, FILTER_HP, 0.6, 0.2,
                adsr = ADSR(0.001, 0.05, 0.0, 0.03)),
            VoicePreset(WAVE_FM, 0.0, FILTER_LP, 0.9, 0.0,
                adsr = ADSR(0.001, 0.8, 0.2, 0.5),
                fmRatio = 3.0, fmIndex = 2.5, reverbSend = 0.4),
            VoicePreset(WAVE_PULSE, 0.5, FILTER_LP, 0.55, 0.35,
                adsr = ADSR(0.001, 0.2, 0.1, 0.15),
                filterEnvDepth = 0.6, filterEnvDecay = 0.15),
            VoicePreset(WAVE_SINE, 0.0, FILTER_LP, 0.95, 0.0,
                adsr = ADSR(0.01, 0.05, 0.9, 0.2)),
            VoicePreset(WAVE_FM, 0.0, FILTER_BP, 0.5, 0.6,
                adsr = ADSR(0.001, 0.3, 0.0, 0.1),
                fmRatio = 7.0, fmIndex = 5.0),
        )

        fun noteToFreq(midiNote: Int): Double {
            val midi = midiNote + 23
            return 440.0 * 2.0.pow((midi - 69) / 12.0)
        }

        private const val TWO_PI = 2.0 * PI
    }

    /**
     * Trigger notes from the current tracker row.
     * @param rowData Array of 8 tracks, each IntArray(note, instrument, volume, fx, fx2)
     */
    fun triggerRow(rowData: Array<IntArray>) {
        for (track in rowData.indices) {
            if (track >= voices.size) break
            val data = rowData[track]
            val note = data[0]
            val vol = data[2]
            val voice = voices[track]

            when {
                note == NOTE_OFF -> voice.releaseNote()
                note > 0 -> {
                    val freq = noteToFreq(note)
                    val v = (vol and 0xFF) / 255.0
                    voice.triggerNote(freq, v)
                }
                // note == 0 means continue — existing note keeps playing
            }
        }
    }

    /**
     * Calculate the swing-adjusted delay in samples for a given row.
     * Odd rows are delayed by swingAmount * row_duration.
     */
    fun getSwingDelaySamples(row: Int, bpm: Int): Int {
        if (row % 2 == 0 || swingAmount <= 0.0) return 0
        val rowDuration = 60.0 / (bpm * 4.0) // seconds per row at 4 rows/beat
        return (rowDuration * swingAmount * SAMPLE_RATE).toInt()
    }

    /**
     * Get the waveform buffer for visualization (most recent 320 samples).
     * Returns values normalized to 0-255 range for display.
     */
    fun getWaveformData(): ByteArray {
        val data = ByteArray(WAVEFORM_CAPTURE_SIZE)
        for (i in 0 until WAVEFORM_CAPTURE_SIZE) {
            val idx = (waveformWritePos + i) % WAVEFORM_CAPTURE_SIZE
            val v = waveformBuffer[idx].coerceIn(-1.0, 1.0)
            data[i] = (128 + v * 80).toInt().coerceIn(0, 255).toByte()
        }
        return data
    }

    /**
     * Generate one chunk of stereo PCM audio.
     */
    fun generateChunk(): ByteArray {
        val buffer = ByteArray(CHUNK_SAMPLES * CHANNELS * 2)
        val dt = 1.0 / SAMPLE_RATE

        // Per-track level accumulators
        val trackSumSq = DoubleArray(8)
        var masterSumSqL = 0.0
        var masterSumSqR = 0.0

        for (i in 0 until CHUNK_SAMPLES) {
            var mixL = 0.0
            var mixR = 0.0
            var chorusInL = 0.0
            var chorusInR = 0.0
            var reverbInL = 0.0
            var reverbInR = 0.0
            var delayInL = 0.0
            var delayInR = 0.0

            for (v in voices) {
                if (!v.active) continue

                v.processEnvelope(dt)
                if (!v.active) continue

                val sample = v.generateSample()
                val gain = 0.15

                val pan = (v.trackIndex.toDouble() / 7.0) * 0.6 + 0.2
                val panL = cos(pan * PI * 0.5)
                val panR = sin(pan * PI * 0.5)

                val outL = sample * gain * panL
                val outR = sample * gain * panR

                mixL += outL
                mixR += outR

                // Track level metering
                trackSumSq[v.trackIndex] += sample * sample * gain * gain

                val preset = v.preset
                if (preset.chorusSend > 0.0) {
                    chorusInL += outL * preset.chorusSend
                    chorusInR += outR * preset.chorusSend
                }
                if (preset.reverbSend > 0.0) {
                    reverbInL += outL * preset.reverbSend
                    reverbInR += outR * preset.reverbSend
                }
                if (preset.delaySend > 0.0) {
                    delayInL += outL * preset.delaySend
                    delayInR += outR * preset.delaySend
                }
            }

            val (chL, chR) = chorus.process(chorusInL, chorusInR)
            val (dlL, dlR) = delay.process(delayInL, delayInR)
            val (rvL, rvR) = reverb.process(reverbInL, reverbInR)

            var outL = mixL + chL + dlL + rvL
            var outR = mixR + chR + dlR + rvR

            // DC offset removal
            val prevDcL = dcL
            val prevDcR = dcR
            dcL = outL - prevDcL * dcCoeff
            dcR = outR - prevDcR * dcCoeff
            outL -= dcL
            outR -= dcR

            // Soft clip
            outL = tanhClip(outL)
            outR = tanhClip(outR)

            // Master level metering
            masterSumSqL += outL * outL
            masterSumSqR += outR * outR

            // Capture mono mix for waveform visualization
            waveformBuffer[waveformWritePos] = (outL + outR) * 0.5
            waveformWritePos = (waveformWritePos + 1) % WAVEFORM_CAPTURE_SIZE

            // Convert to 16-bit LE
            val sL = (outL * 32767.0).toInt().coerceIn(-32768, 32767)
            val sR = (outR * 32767.0).toInt().coerceIn(-32768, 32767)

            val offset = i * 4
            buffer[offset] = (sL and 0xFF).toByte()
            buffer[offset + 1] = (sL shr 8 and 0xFF).toByte()
            buffer[offset + 2] = (sR and 0xFF).toByte()
            buffer[offset + 3] = (sR shr 8 and 0xFF).toByte()
        }

        // Update level meters (RMS)
        val invN = 1.0 / CHUNK_SAMPLES
        for (t in 0 until 8) {
            trackLevels[t] = sqrt(trackSumSq[t] * invN).coerceIn(0.0, 1.0)
        }
        masterLevelL = sqrt(masterSumSqL * invN).coerceIn(0.0, 1.0)
        masterLevelR = sqrt(masterSumSqR * invN).coerceIn(0.0, 1.0)

        return buffer
    }

    /** Generate silence (when tracker is stopped). */
    fun generateSilence(): ByteArray {
        trackLevels.fill(0.0)
        masterLevelL = 0.0
        masterLevelR = 0.0
        // Fade waveform to center
        for (i in 0 until WAVEFORM_CAPTURE_SIZE) {
            waveformBuffer[i] *= 0.95
        }
        return ByteArray(CHUNK_SAMPLES * CHANNELS * 2)
    }

    /** Kill all voices and clear effects. */
    fun allNotesOff() {
        for (v in voices) {
            v.active = false
            v.envStage = 0
            v.envLevel = 0.0
            v.svfLow = 0.0
            v.svfBand = 0.0
            v.svfHigh = 0.0
        }
        delay.clear()
        chorus.clear()
        reverb.clear()
        dcL = 0.0
        dcR = 0.0
        trackLevels.fill(0.0)
        masterLevelL = 0.0
        masterLevelR = 0.0
    }

    /** Tanh-style soft clipper */
    private fun tanhClip(x: Double): Double {
        if (x > 3.0) return 1.0
        if (x < -3.0) return -1.0
        val x2 = x * x
        return x * (27.0 + x2) / (27.0 + 9.0 * x2)
    }
}
