package com.m8.emulator

import com.m8.audio.M8AudioPlayer
import kotlin.math.*

/**
 * Advanced polyphonic synthesizer for the M8 emulator.
 *
 * Features:
 * - Band-limited waveforms (PolyBLEP anti-aliasing)
 * - WavSynth engine with 10 shapes, SIZE, MULT, WARP, MIRROR
 * - 4-operator FM synthesis with 12 algorithms
 * - HyperSynth: 6-oscillator chord/supersaw with stereo spread
 * - Full ADSR envelopes per voice
 * - Pulse width modulation with LFO
 * - Resonant state-variable filter per voice (LP/HP/BP/BS/LP+HP)
 * - Multiple limiter modes (tanh/clip/sin/fold/wrap)
 * - DJ filter (global LP/HP crossover)
 * - Stereo delay effect (ping-pong)
 * - Chorus effect (dual modulated delay lines)
 * - Plate reverb (Schroeder/Moorer style)
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
        val pan: Double = 0.5,  // 0=left, 1=right; overridden by track spread
        // WavSynth parameters
        val wavSize: Int = 0x80,
        val wavMult: Int = 1,
        val wavWarp: Double = 0.0,
        val wavMirror: Boolean = false,
        // FM 4-operator parameters
        val fm4Algorithm: Int = 0,
        val fm4Op1Ratio: Double = 1.0,
        val fm4Op1Level: Double = 1.0,
        val fm4Op1Feedback: Double = 0.0,
        val fm4Op2Ratio: Double = 2.0,
        val fm4Op2Level: Double = 0.8,
        val fm4Op2Feedback: Double = 0.0,
        val fm4Op3Ratio: Double = 3.0,
        val fm4Op3Level: Double = 0.5,
        val fm4Op3Feedback: Double = 0.0,
        val fm4Op4Ratio: Double = 4.0,
        val fm4Op4Level: Double = 0.3,
        val fm4Op4Feedback: Double = 0.0,
        // HyperSynth parameters
        val hyperChordIntervals: IntArray = intArrayOf(0, 4, 7, 12, 16, 19),
        val hyperSwarm: Double = 0.02,
        val hyperWidth: Double = 1.0,
        val hyperSubOsc: Double = 0.0,
        // Limiter type
        val limiterType: Int = 0  // 0=tanh, 1=clip, 2=sin, 3=fold, 4=wrap
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
        var releaseStartLevel = 0.0  // Captured level at release trigger for exponential release

        // Per-voice gain from instrument AMP parameter (0.0-1.0)
        var instrumentGain = 1.0

        // Filter envelope
        var filterEnvLevel = 0.0
        var filterEnvTime = 0.0

        // PWM LFO phase
        var pwmLfoPhase = 0.0

        // Filter LFO phase
        var filterLfoPhase = 0.0

        // FM operator phase (2-op legacy)
        var fmPhase = 0.0

        // 4-operator FM state
        var fm4Phases = DoubleArray(4)
        var fm4Feedback = DoubleArray(4) // previous output for self-feedback

        // HyperSynth state: 6 oscillator phases
        var hyperPhases = DoubleArray(6)

        // MacroSynth state: multiple detuned oscillator phases for SAW_SWARM
        var macroSwarmPhases = DoubleArray(7)

        // WavSynth state
        var wavPhase = 0.0

        // State-variable filter state
        var svfLow = 0.0
        var svfBand = 0.0
        var svfHigh = 0.0

        // Second SVF for series LP+HP mode
        var svf2Low = 0.0
        var svf2Band = 0.0
        var svf2High = 0.0

        // Noise LFSR per voice
        var noiseLfsr = 0x7FFF
        var noiseValue = 0.0
        var noiseSampleCount = 0

        // WavSynth noise LFSR (separate)
        var wavNoiseLfsr = 0x7FFF

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
                releaseStartLevel = envLevel  // Capture current level for exponential release
                envStage = 4 // release
                envTime = 0.0
            }
            noteOn = false
        }

        fun processEnvelope(dt: Double) {
            val adsr = preset.adsr
            when (envStage) {
                1 -> { // Attack - exponential rise
                    envTime += dt
                    envLevel = if (adsr.attack <= 0.0) 1.0
                    else (1.0 - exp(-envTime * 5.0 / adsr.attack)).coerceIn(0.0, 1.0)
                    if (envLevel >= 0.999) {
                        envLevel = 1.0
                        envStage = 2
                        envTime = 0.0
                    }
                }
                2 -> { // Decay - exponential fall to sustain
                    envTime += dt
                    envLevel = if (adsr.decay <= 0.0) adsr.sustain
                    else adsr.sustain + (1.0 - adsr.sustain) * exp(-envTime * 5.0 / adsr.decay)
                    if (envLevel <= adsr.sustain + 0.001) {
                        envLevel = adsr.sustain
                        envStage = 3
                    }
                }
                3 -> { // Sustain
                    envLevel = adsr.sustain
                }
                4 -> { // Release - exponential fall from captured level
                    envTime += dt
                    envLevel = if (adsr.release <= 0.0) 0.0
                    else releaseStartLevel * exp(-envTime * 5.0 / adsr.release)
                    if (envLevel < 0.0001) {
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

                // --- WavSynth shapes ---
                WAVE_PULSE_12 -> generateWavSynthSample(phaseInc, 0.12)
                WAVE_PULSE_25 -> generateWavSynthSample(phaseInc, 0.25)
                WAVE_PULSE_75 -> generateWavSynthSample(phaseInc, 0.75)
                WAVE_OVERFLOW -> generateOverflowSample(phaseInc)
                WAVE_WAVSYNTH -> generateWavSynthEngine(phaseInc)

                // --- 4-operator FM ---
                WAVE_FM4 -> generateFm4Sample()

                // --- HyperSynth ---
                WAVE_HYPER -> generateHyperSample()

                // --- MacroSynth models ---
                WAVE_MACRO_CSAW -> generateMacroCsaw(phaseInc)
                WAVE_MACRO_MORPH -> generateMacroMorph(phaseInc)
                WAVE_MACRO_SAW_SWARM -> generateMacroSawSwarm()

                else -> 0.0
            }

            // Advance oscillator phase
            phase += phaseInc
            if (phase > 1e6) phase -= 1e6

            // Apply amplitude envelope
            val enveloped = raw * envLevel * volume

            // Apply per-voice limiter shaping before filter
            val shaped = applyLimiter(enveloped, preset.limiterType)

            // State-variable filter
            val cutoffNorm = preset.filterCutoff +
                    preset.filterEnvDepth * filterEnvLevel +
                    if (preset.filterLfoDepth > 0.0) {
                        filterLfoPhase += preset.filterLfoRate * dt
                        sin(filterLfoPhase * TWO_PI) * preset.filterLfoDepth
                    } else 0.0

            val cutoffHz = 20.0 * 2.0.pow(cutoffNorm.coerceIn(0.0, 1.0) * 11.0) // 20Hz - 40960Hz
            val f = 2.0 * sin(PI * (cutoffHz / SAMPLE_RATE).coerceIn(0.0, 0.49))
            val q = max(0.5, 1.0 - preset.filterResonance.coerceIn(0.0, 1.0) * 0.98)

            svfHigh = shaped - svfLow - q * svfBand
            svfBand += f * svfHigh
            svfLow += f * svfBand

            // Prevent filter state blowup
            if (svfBand.isNaN() || svfBand.isInfinite()) { svfBand = 0.0; svfLow = 0.0; svfHigh = 0.0 }
            svfBand = svfBand.coerceIn(-10.0, 10.0)
            svfLow = svfLow.coerceIn(-10.0, 10.0)

            // Soft-clip band state at high resonance to allow near-self-oscillation without instability
            if (preset.filterResonance > 0.7) {
                svfBand = tanh(svfBand * 1.2) / 1.2
            }

            return when (preset.filterType) {
                FILTER_LP -> svfLow
                FILTER_HP -> svfHigh
                FILTER_BP -> svfBand
                FILTER_BS -> svfLow + svfHigh  // Bandstop/notch
                FILTER_LP_HP -> {
                    // Series LP then HP: run LP output through a second SVF as HP
                    val lpOut = svfLow
                    svf2High = lpOut - svf2Low - q * svf2Band
                    svf2Band += f * svf2High
                    svf2Low += f * svf2Band

                    // Prevent filter state blowup
                    if (svf2Band.isNaN() || svf2Band.isInfinite()) { svf2Band = 0.0; svf2Low = 0.0; svf2High = 0.0 }
                    svf2Band = svf2Band.coerceIn(-10.0, 10.0)
                    svf2Low = svf2Low.coerceIn(-10.0, 10.0)
                    svf2High
                }
                else -> svfLow
            }
        }

        // --- WavSynth: generate a pulse wave with given duty cycle, applying WARP and MIRROR ---
        private fun generateWavSynthSample(phaseInc: Double, duty: Double): Double {
            val p = applyWavSynthModifiers(phase % 1.0)
            val naive = if (p < duty) 1.0 else -1.0
            return naive + polyBlep(phase, phaseInc) - polyBlep(phase - duty, phaseInc)
        }

        // --- WavSynth OVERFLOW shape: 8-bit counter overflow distortion ---
        private fun generateOverflowSample(phaseInc: Double): Double {
            val mult = preset.wavMult.coerceAtLeast(1)
            val p = applyWavSynthModifiers((phase * mult) % 1.0)
            // Simulate 8-bit overflow: sawtooth scaled and wrapped
            val size = preset.wavSize.coerceIn(1, 0xFF)
            val counter = (p * size * 2).toInt() and 0xFF
            return (counter.toDouble() / 127.5) - 1.0
        }

        // --- WavSynth engine: full wavetable with SIZE, MULT, WARP, MIRROR ---
        private fun generateWavSynthEngine(phaseInc: Double): Double {
            val mult = preset.wavMult.coerceAtLeast(1)
            val size = preset.wavSize.coerceIn(1, 0xFF)

            // Hard-sync: multiply frequency by MULT
            wavPhase += phaseInc * mult
            if (wavPhase > 1.0) wavPhase -= floor(wavPhase)

            var p = applyWavSynthModifiers(wavPhase)

            // Generate waveform in a buffer conceptually of 'size' samples
            // Map phase to buffer position
            val bufPos = (p * size).toInt().coerceIn(0, size - 1)
            val frac = (p * size) - bufPos

            // Generate base waveform at buffer position (sine-based wavetable)
            val t0 = bufPos.toDouble() / size
            val t1 = ((bufPos + 1) % size).toDouble() / size
            val s0 = sin(t0 * TWO_PI)
            val s1 = sin(t1 * TWO_PI)

            return s0 + (s1 - s0) * frac
        }

        // Apply WARP and MIRROR modifications to a normalized phase [0,1)
        private fun applyWavSynthModifiers(p: Double): Double {
            var result = p.coerceIn(0.0, 0.9999)

            // WARP: asymmetric push of waveform shape
            if (preset.wavWarp != 0.0) {
                val warp = preset.wavWarp.coerceIn(-1.0, 1.0)
                // Power curve warping
                result = if (warp > 0.0) {
                    result.pow(1.0 + warp * 3.0)
                } else {
                    1.0 - (1.0 - result).pow(1.0 - warp * 3.0)
                }
            }

            // MIRROR: reflect the waveform around the midpoint
            if (preset.wavMirror) {
                result = if (result < 0.5) result * 2.0 else (1.0 - result) * 2.0
            }

            return result.coerceIn(0.0, 0.9999)
        }

        // --- 4-operator FM synthesis ---
        private fun generateFm4Sample(): Double {
            val dt = 1.0 / SAMPLE_RATE
            val p = preset
            val ratios = doubleArrayOf(p.fm4Op1Ratio, p.fm4Op2Ratio, p.fm4Op3Ratio, p.fm4Op4Ratio)
            val levels = doubleArrayOf(p.fm4Op1Level, p.fm4Op2Level, p.fm4Op3Level, p.fm4Op4Level)
            val feedbacks = doubleArrayOf(p.fm4Op1Feedback, p.fm4Op2Feedback, p.fm4Op3Feedback, p.fm4Op4Feedback)

            // Advance all operator phases
            for (i in 0 until 4) {
                fm4Phases[i] += frequency * ratios[i] * dt
                if (fm4Phases[i] > 1e6) fm4Phases[i] -= 1e6
            }

            // Compute operators based on algorithm
            // Each operator: sin(phase * 2pi + modulation_input + self_feedback)
            val ops = DoubleArray(4)

            when (p.fm4Algorithm) {
                // ALG 0: Serial chain op4->op3->op2->op1(output)
                0 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[2] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return ops[0]
                }
                // ALG 1: op4->op3->op2, op2+op1 (op1 independent carrier)
                1 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[2] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[1] + ops[0]) * 0.5
                }
                // ALG 2: op4->op3, op3+op2->op1
                2 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[3] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + ops[2] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return ops[0]
                }
                // ALG 3: Two parallel pairs [op4->op3] + [op2->op1]
                3 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[2] + ops[0]) * 0.5
                }
                // ALG 4: op4->op3, op4->op2, op1 independent; output=op3+op2+op1
                4 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[3] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[2] + ops[1] + ops[0]) / 3.0
                }
                // ALG 5: op4->all three carriers (op3, op2, op1)
                5 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[3] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[3] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[2] + ops[1] + ops[0]) / 3.0
                }
                // ALG 6: op4->op3->op2 + op4->op1; output=op2+op1
                6 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[2] + ops[3] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[3] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[1] + ops[0]) * 0.5
                }
                // ALG 7: op4->op3, op2->op1; output=op3+op1 (same pairs but swapped outputs)
                7 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[2] + ops[0]) * 0.5
                }
                // ALG 8: [op4+op3]->op2->op1
                8 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[3] + ops[2] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return ops[0]
                }
                // ALG 9: [op4+op3+op2]->op1
                9 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[3] + ops[2] + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return ops[0]
                }
                // ALG 10: op4->op3, op2 and op1 independent; output=op3+op2+op1
                10 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[2] + ops[1] + ops[0]) / 3.0
                }
                // ALG 11: All parallel/additive: op1+op2+op3+op4
                11 -> {
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return (ops[0] + ops[1] + ops[2] + ops[3]) * 0.25
                }
                else -> {
                    // Default to serial chain
                    ops[3] = sin(fm4Phases[3] * TWO_PI + feedbacks[3] * fm4Feedback[3]) * levels[3]
                    ops[2] = sin(fm4Phases[2] * TWO_PI + ops[3] + feedbacks[2] * fm4Feedback[2]) * levels[2]
                    ops[1] = sin(fm4Phases[1] * TWO_PI + ops[2] + feedbacks[1] * fm4Feedback[1]) * levels[1]
                    ops[0] = sin(fm4Phases[0] * TWO_PI + ops[1] + feedbacks[0] * fm4Feedback[0]) * levels[0]
                    fm4Feedback = ops.copyOf()
                    return ops[0]
                }
            }
        }

        // --- HyperSynth: 6-oscillator supersaw/chord with stereo spread ---
        // Returns mono sample; stereo spread is applied at the mix stage
        private fun generateHyperSample(): Double {
            val p = preset
            val intervals = p.hyperChordIntervals
            val swarm = p.hyperSwarm
            val dt = 1.0 / SAMPLE_RATE

            var sum = 0.0
            val numOsc = minOf(6, intervals.size)

            for (i in 0 until numOsc) {
                // Chord interval in semitones -> frequency ratio
                val intervalRatio = 2.0.pow(intervals[i] / 12.0)
                // Progressive detune: each oscillator gets more detune
                val detuneAmount = swarm * (i - (numOsc - 1) * 0.5) / (numOsc - 1).coerceAtLeast(1).toDouble()
                val oscFreq = frequency * intervalRatio * (1.0 + detuneAmount)

                hyperPhases[i] += oscFreq * dt
                if (hyperPhases[i] > 1e6) hyperPhases[i] -= 1e6

                // Sawtooth oscillator
                val oscPhase = hyperPhases[i] % 1.0
                val phaseInc = oscFreq / SAMPLE_RATE
                val naive = 2.0 * (oscPhase - floor(oscPhase + 0.5))
                sum += naive - polyBlep(hyperPhases[i], phaseInc)
            }

            // Sub-oscillator (square wave, 1-2 octaves below based on subOsc value)
            if (p.hyperSubOsc > 0.0) {
                val subOctave = if (p.hyperSubOsc > 0.5) 2.0 else 1.0
                val subFreq = frequency / (2.0.pow(subOctave))
                // Use phase from first oscillator scaled down
                val subPhase = (hyperPhases[0] * (subFreq / frequency)) % 1.0
                val subSample = if (subPhase < 0.5) 1.0 else -1.0
                sum += subSample * p.hyperSubOsc.coerceIn(0.0, 1.0)
            }

            return sum / (numOsc + if (p.hyperSubOsc > 0.0) 1 else 0).coerceAtLeast(1)
        }

        // --- MacroSynth: CSAW (crossfading saw) ---
        // Timbre controls crossfade point between saw and inverted saw
        private fun generateMacroCsaw(phaseInc: Double): Double {
            val p = phase % 1.0
            val timbre = preset.pulseWidth  // Reuse pulseWidth as timbre (0.0-1.0)
            // Two saws with crossfade: normal saw fades into reversed/shifted saw
            val saw1 = 2.0 * p - 1.0
            val saw2 = 2.0 * ((p + 0.5) % 1.0) - 1.0
            return saw1 * (1.0 - timbre) + saw2 * timbre - polyBlep(phase, phaseInc)
        }

        // --- MacroSynth: MORPH (morphing between basic shapes) ---
        // Timbre (via pulseWidth) morphs: 0.0=sine, 0.33=triangle, 0.66=saw, 1.0=square
        private fun generateMacroMorph(phaseInc: Double): Double {
            val p = phase % 1.0
            val morph = preset.pulseWidth.coerceIn(0.0, 1.0) * 3.0  // 0..3 range

            val sine = sin(p * TWO_PI)
            val tri = 4.0 * abs(p - 0.5) - 1.0
            val saw = 2.0 * (p - floor(p + 0.5)) - polyBlep(phase, phaseInc)
            val square = (if (p < 0.5) 1.0 else -1.0) + polyBlep(phase, phaseInc) - polyBlep(phase - 0.5, phaseInc)

            return when {
                morph < 1.0 -> sine * (1.0 - morph) + tri * morph
                morph < 2.0 -> tri * (2.0 - morph) + saw * (morph - 1.0)
                else -> saw * (3.0 - morph) + square * (morph - 2.0)
            }
        }

        // --- MacroSynth: SAW_SWARM (multiple detuned saws) ---
        // Uses 7 detuned saw oscillators; pulseWidth controls detune spread
        private fun generateMacroSawSwarm(): Double {
            val dt = 1.0 / SAMPLE_RATE
            val spread = preset.pulseWidth.coerceIn(0.0, 1.0) * 0.03  // Max 3% detune
            val numOsc = 7
            var sum = 0.0

            for (i in 0 until numOsc) {
                val detuneRatio = 1.0 + spread * (i - (numOsc - 1) * 0.5) / (numOsc - 1).toDouble()
                val oscFreq = frequency * detuneRatio
                macroSwarmPhases[i] += oscFreq * dt
                if (macroSwarmPhases[i] > 1e6) macroSwarmPhases[i] -= 1e6

                val oscPhase = macroSwarmPhases[i] % 1.0
                val phaseIncOsc = oscFreq / SAMPLE_RATE
                val naive = 2.0 * (oscPhase - floor(oscPhase + 0.5))
                sum += naive - polyBlep(macroSwarmPhases[i], phaseIncOsc)
            }

            return sum / numOsc
        }

        // --- HyperSynth stereo pair generation ---
        // Returns (left, right) incorporating WIDTH parameter
        fun generateHyperStereo(): Pair<Double, Double> {
            if (preset.waveform != WAVE_HYPER) return Pair(0.0, 0.0)
            if (!active || envStage == 0) return Pair(0.0, 0.0)

            val p = preset
            val intervals = p.hyperChordIntervals
            val swarm = p.hyperSwarm
            val width = p.hyperWidth
            val dt = 1.0 / SAMPLE_RATE
            val numOsc = minOf(6, intervals.size)

            var sumL = 0.0
            var sumR = 0.0

            for (i in 0 until numOsc) {
                val intervalRatio = 2.0.pow(intervals[i] / 12.0)
                val detuneAmount = swarm * (i - (numOsc - 1) * 0.5) / (numOsc - 1).coerceAtLeast(1).toDouble()
                val oscFreq = frequency * intervalRatio * (1.0 + detuneAmount)

                hyperPhases[i] += oscFreq * dt
                if (hyperPhases[i] > 1e6) hyperPhases[i] -= 1e6

                val oscPhase = hyperPhases[i] % 1.0
                val phaseIncOsc = oscFreq / SAMPLE_RATE
                val naive = 2.0 * (oscPhase - floor(oscPhase + 0.5))
                val sample = naive - polyBlep(hyperPhases[i], phaseIncOsc)

                // Stereo placement: spread oscillators across stereo field
                val panPos = if (numOsc <= 1) 0.5
                else i.toDouble() / (numOsc - 1).toDouble()
                val stereoPos = 0.5 + (panPos - 0.5) * width
                val panL = cos(stereoPos * PI * 0.5)
                val panR = sin(stereoPos * PI * 0.5)
                sumL += sample * panL
                sumR += sample * panR
            }

            // Sub-oscillator (center-panned)
            if (p.hyperSubOsc > 0.0) {
                val subOctave = if (p.hyperSubOsc > 0.5) 2.0 else 1.0
                val subFreq = frequency / (2.0.pow(subOctave))
                val subPhase = (hyperPhases[0] * (subFreq / frequency)) % 1.0
                val subSample = (if (subPhase < 0.5) 1.0 else -1.0) * p.hyperSubOsc.coerceIn(0.0, 1.0)
                sumL += subSample * 0.707
                sumR += subSample * 0.707
            }

            val div = (numOsc + if (p.hyperSubOsc > 0.0) 1 else 0).coerceAtLeast(1).toDouble()
            return Pair(sumL / div, sumR / div)
        }

        // --- Limiter/waveshaper ---
        private fun applyLimiter(x: Double, type: Int): Double {
            return when (type) {
                LIMITER_TANH -> x // tanh applied at master output
                LIMITER_CLIP -> x.coerceIn(-1.0, 1.0)
                LIMITER_SIN -> sin(x * PI * 0.5).coerceIn(-1.0, 1.0)
                LIMITER_FOLD -> {
                    if (x.isNaN() || x.isInfinite()) return 0.0
                    var v = x.coerceIn(-10.0, 10.0)
                    repeat(8) {
                        if (v > 1.0) v = 2.0 - v
                        else if (v < -1.0) v = -2.0 - v
                        else return@repeat
                    }
                    v.coerceIn(-1.0, 1.0)
                }
                LIMITER_WRAP -> {
                    if (x.isNaN() || x.isInfinite()) return 0.0
                    var v = x % 2.0
                    if (v > 1.0) v -= 2.0
                    if (v < -1.0) v += 2.0
                    v
                }
                else -> x
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

    // DC offset removal (first-order high-pass, leaky integrator tracking DC level)
    private var dcL = 0.0
    private var dcR = 0.0
    private val dcAlpha = TWO_PI * 5.0 / SAMPLE_RATE // ~0.0007 for 5Hz cutoff

    // DJ filter state: 0x00=full LP, 0x80=bypass, 0xFF=full HP
    var djFilterValue = 0x80
    private var djLpL = 0.0
    private var djLpR = 0.0

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

        // Basic waveforms
        const val WAVE_SINE = 0
        const val WAVE_SAW = 1
        const val WAVE_PULSE = 2
        const val WAVE_TRIANGLE = 3
        const val WAVE_NOISE = 4
        const val WAVE_FM = 5

        // WavSynth shapes (10-19)
        const val WAVE_PULSE_12 = 10
        const val WAVE_PULSE_25 = 11
        const val WAVE_PULSE_75 = 12
        const val WAVE_OVERFLOW = 13
        const val WAVE_WAVSYNTH = 14  // Uses WavSynth engine with SIZE/MULT/WARP/MIRROR

        // FM 4-operator
        const val WAVE_FM4 = 20

        // HyperSynth
        const val WAVE_HYPER = 30

        // MacroSynth models
        const val WAVE_MACRO_CSAW = 40
        const val WAVE_MACRO_MORPH = 41
        const val WAVE_MACRO_SAW_SWARM = 42

        // Filter modes
        const val FILTER_LP = 0
        const val FILTER_HP = 1
        const val FILTER_BP = 2
        const val FILTER_BS = 3      // Bandstop/notch
        const val FILTER_LP_HP = 4   // Series LP then HP

        // Limiter types
        const val LIMITER_TANH = 0
        const val LIMITER_CLIP = 1
        const val LIMITER_SIN = 2
        const val LIMITER_FOLD = 3
        const val LIMITER_WRAP = 4

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
            return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
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
     * Apply the DJ filter to a stereo sample pair.
     * djFilterValue: 0x00=full LP, 0x80=bypass, 0xFF=full HP
     */
    private fun applyDjFilter(inL: Double, inR: Double): Pair<Double, Double> {
        if (djFilterValue == 0x80) return Pair(inL, inR)

        return if (djFilterValue < 0x80) {
            // LP mode: lower values = more filtering
            val amount = djFilterValue / 128.0  // 0.0 (full LP) to 1.0 (bypass)
            val cutoff = 0.0001 + amount * amount * 0.3  // Exponential-ish cutoff mapping
            djLpL += cutoff * (inL - djLpL)
            djLpR += cutoff * (inR - djLpR)
            Pair(djLpL, djLpR)
        } else {
            // HP mode: higher values = more filtering
            val amount = (djFilterValue - 128) / 127.0  // 0.0 (bypass) to 1.0 (full HP)
            val cutoff = 0.0001 + (1.0 - amount) * (1.0 - amount) * 0.3
            djLpL += cutoff * (inL - djLpL)
            djLpR += cutoff * (inR - djLpR)
            Pair(inL - djLpL, inR - djLpR)
        }
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
                val gain = v.instrumentGain * 0.2  // Per-instrument gain with headroom

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

            // DJ filter (before soft clip)
            val (djL, djR) = applyDjFilter(outL, outR)
            outL = djL
            outR = djR

            // DC offset removal (leaky integrator tracks DC, subtract it)
            dcL += dcAlpha * (outL - dcL)
            dcR += dcAlpha * (outR - dcR)
            outL -= dcL
            outR -= dcR

            // Soft clip
            outL = tanhClip(outL)
            outR = tanhClip(outR)
            if (outL.isNaN()) outL = 0.0
            if (outR.isNaN()) outR = 0.0

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
            v.svf2Low = 0.0
            v.svf2Band = 0.0
            v.svf2High = 0.0
            v.fm4Phases.fill(0.0)
            v.fm4Feedback.fill(0.0)
            v.hyperPhases.fill(0.0)
            v.macroSwarmPhases.fill(0.0)
            v.wavPhase = 0.0
        }
        delay.clear()
        chorus.clear()
        reverb.clear()
        dcL = 0.0
        dcR = 0.0
        djLpL = 0.0
        djLpR = 0.0
        trackLevels.fill(0.0)
        masterLevelL = 0.0
        masterLevelR = 0.0
    }

    /**
     * Apply an M8Instrument's parameters to configure a voice's preset.
     * Maps M8 byte-range parameters (0-255) to the synth engine's normalized values.
     */
    fun applyInstrument(trackIndex: Int, instrument: M8Instrument) {
        if (trackIndex < 0 || trackIndex >= voices.size) return
        val voice = voices[trackIndex]

        // Map waveform from M8 instrument type and shape
        val waveform = when (instrument.type) {
            InstrumentType.WAVSYNTH -> when (instrument.wavSynth.shape) {
                WavShape.PULSE_12 -> WAVE_PULSE_12
                WavShape.PULSE_25 -> WAVE_PULSE_25
                WavShape.PULSE_50 -> WAVE_PULSE
                WavShape.PULSE_75 -> WAVE_PULSE_75
                WavShape.SAW -> WAVE_SAW
                WavShape.TRIANGLE -> WAVE_TRIANGLE
                WavShape.SINE -> WAVE_SINE
                WavShape.NOISE_PITCH -> WAVE_NOISE
                WavShape.NOISE -> WAVE_NOISE
                WavShape.OVERFLOW -> WAVE_OVERFLOW
            }
            InstrumentType.FM_SYNTH -> WAVE_FM4
            InstrumentType.HYPERSYNTH -> WAVE_HYPER
            InstrumentType.MACROSYNTH -> when (instrument.macroSynth.model) {
                0 -> WAVE_MACRO_CSAW
                1 -> WAVE_MACRO_MORPH
                14 -> WAVE_MACRO_SAW_SWARM
                else -> WAVE_SINE  // Fallback for unimplemented models
            }
            else -> WAVE_SINE
        }

        // Map filter type
        val filterType = when (instrument.filter.type) {
            FilterType.OFF -> FILTER_LP  // Use LP with full cutoff as "off"
            FilterType.LP -> FILTER_LP
            FilterType.HP -> FILTER_HP
            FilterType.BP -> FILTER_BP
            FilterType.BS -> FILTER_BS
            FilterType.LP_HP -> FILTER_LP_HP
        }

        // Convert 0-255 byte parameters to normalized 0.0-1.0
        val filterCutoff = instrument.filter.cutoff / 255.0
        val filterResonance = instrument.filter.resonance / 255.0

        // Map envelope parameters using M8's curve: time = 0.001 + (value / 255)^2 * 5.0
        val env = instrument.modulation.env1
        val attack = m8EnvTime(env.attack)
        val decay = m8EnvTime(env.decay)
        val sustain = env.sustain / 255.0
        val release = m8EnvTime(env.release)

        // Map effect sends
        val chorusSend = instrument.amp.chorusSend / 255.0
        val delaySend = instrument.amp.delaySend / 255.0
        val reverbSend = instrument.amp.reverbSend / 255.0
        val pan = instrument.amp.pan / 255.0

        // Map limiter type
        val limiterType = when (instrument.amp.limiter) {
            LimiterType.CLIP -> LIMITER_CLIP
            LimiterType.SIN -> LIMITER_SIN
            LimiterType.FOLD -> LIMITER_FOLD
            LimiterType.WRAP -> LIMITER_WRAP
            LimiterType.POST, LimiterType.POST_AD -> LIMITER_TANH
        }

        // WavSynth-specific parameters
        val wavSize = instrument.wavSynth.size
        val wavMult = instrument.wavSynth.mult.coerceAtLeast(1)
        val wavWarp = (instrument.wavSynth.warp / 255.0) * 2.0 - 1.0  // Map 0-255 to -1..1
        val wavMirror = instrument.wavSynth.mirror > 0

        // FM 4-operator parameters
        val fm = instrument.fmSynth
        val fm4Algorithm = fm.algorithm.ordinal

        // MacroSynth: timbre maps to pulseWidth for morph/crossfade control
        val macroTimbre = instrument.macroSynth.timbre / 255.0

        // Build the preset with per-instrument filter envelope from env2 if configured
        val filterEnvDepth = if (instrument.modulation.env2.amount != 0x80) {
            (instrument.modulation.env2.amount - 0x80) / 127.0  // Bipolar: 0x80=0, 0xFF=+1
        } else 0.0
        val filterEnvDecay = m8EnvTime(instrument.modulation.env2.decay)

        // Use macroTimbre for pulseWidth when instrument is MACROSYNTH
        val pulseWidth = when (instrument.type) {
            InstrumentType.MACROSYNTH -> macroTimbre
            else -> when (instrument.wavSynth.shape) {
                WavShape.PULSE_50 -> 0.5
                WavShape.PULSE_25 -> 0.25
                WavShape.PULSE_12 -> 0.12
                WavShape.PULSE_75 -> 0.75
                else -> 0.5
            }
        }

        // Apply filter cutoff as full (bypass) when filter is OFF
        val effectiveCutoff = if (instrument.filter.type == FilterType.OFF) 1.0 else filterCutoff

        voice.preset = VoicePreset(
            waveform = waveform,
            pulseWidth = pulseWidth,
            filterType = filterType,
            filterCutoff = effectiveCutoff,
            filterResonance = filterResonance,
            adsr = ADSR(attack, decay, sustain, release),
            filterEnvDepth = filterEnvDepth.coerceIn(-1.0, 1.0),
            filterEnvDecay = filterEnvDecay,
            chorusSend = chorusSend,
            reverbSend = reverbSend,
            delaySend = delaySend,
            pan = pan,
            limiterType = limiterType,
            wavSize = wavSize,
            wavMult = wavMult,
            wavWarp = wavWarp,
            wavMirror = wavMirror,
            fm4Algorithm = fm4Algorithm,
            fm4Op1Ratio = fm.op1Ratio.toDouble(),
            fm4Op1Level = fm.op1Level / 255.0,
            fm4Op1Feedback = fm.op1Feedback / 255.0,
            fm4Op2Ratio = fm.op2Ratio.toDouble(),
            fm4Op2Level = fm.op2Level / 255.0,
            fm4Op2Feedback = fm.op2Feedback / 255.0,
            fm4Op3Ratio = fm.op3Ratio.toDouble(),
            fm4Op3Level = fm.op3Level / 255.0,
            fm4Op3Feedback = fm.op3Feedback / 255.0,
            fm4Op4Ratio = fm.op4Ratio.toDouble(),
            fm4Op4Level = fm.op4Level / 255.0,
            fm4Op4Feedback = fm.op4Feedback / 255.0,
        )

        // Apply per-instrument gain from AMP parameter
        voice.instrumentGain = instrument.amp.amp / 255.0
    }

    /** Convert M8 envelope parameter (0-255) to time in seconds using M8's quadratic curve. */
    private fun m8EnvTime(value: Int): Double {
        val norm = value / 255.0
        return 0.001 + norm * norm * 5.0
    }

    /** Tanh-style soft clipper */
    private fun tanhClip(x: Double): Double {
        if (x > 3.0) return 1.0
        if (x < -3.0) return -1.0
        val x2 = x * x
        return x * (27.0 + x2) / (27.0 + 9.0 * x2)
    }
}
