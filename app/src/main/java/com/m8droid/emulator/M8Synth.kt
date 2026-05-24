package com.m8droid.emulator

import android.util.Log
import com.m8droid.audio.M8AudioPlayer
import com.m8droid.audio.WavDecoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M8 tracker synthesizer.
 *
 * - PolyBLEP anti-aliased saw/pulse (no aliasing)
 * - 2-pole SVF filter with stability clamping (musical, never blows up)
 * - Exponential ADSR envelopes (natural dynamics)
 * - Stereo delay with damped feedback
 * - Clean gain staging — transparent limiter only above 0.9
 * - Zero per-chunk allocations
 */
class M8Synth {

    companion object {
        const val SAMPLE_RATE = M8AudioPlayer.SAMPLE_RATE
        const val CHANNELS = 2
        const val CHUNK_SAMPLES = 735
        const val WAVEFORM_CAPTURE_SIZE = 320
        const val NOTE_OFF = 0xFF
        private const val SR = 44100.0
        private const val TWO_PI = 2.0 * PI
        private const val TAG = "M8Synth"
        private const val CHUNK_BYTES = CHUNK_SAMPLES * CHANNELS * 2
        private const val DELAY_LEN = (SR * 0.35).toInt()

        fun noteToFreq(midiNote: Int): Double =
            440.0 * 2.0.pow((midiNote - 69) / 12.0)

        /** PolyBLEP correction — removes aliasing at saw/pulse discontinuities */
        private fun polyBlep(t: Double, dt: Double): Double {
            val p = t % 1.0
            return when {
                p < dt -> { val x = p / dt; x + x - x * x - 1.0 }
                p > 1.0 - dt -> { val x = (p - 1.0) / dt; x * x + x + x + 1.0 }
                else -> 0.0
            }
        }
    }

    // ======================== PRESETS ========================

    data class Preset(
        val wave: Int,          // 0=saw,1=pulse,2=sine,3=tri,4=noise,5=fm,6=sample,7=hyper,8=macro
        val cutoff: Double,     // 0-1 filter cutoff
        val reso: Double,       // 0-1 filter resonance
        val atkMs: Double,      // attack ms
        val decMs: Double,      // decay ms
        val sus: Double,        // sustain level 0-1
        val relMs: Double,      // release ms
        val filtEnv: Double,    // filter envelope amount 0-1
        val pw: Double = 0.5,   // pulse width
        val fmRatio: Double = 1.0,
        val fmIdx: Double = 0.0,
        val dlSend: Double = 0.0,  // delay send
        val pan: Double = 0.5,
        val amp: Double = 1.0,
    )

    private val PRESETS = arrayOf(
        Preset(0, 0.6, 0.2,   5.0, 100.0, 0.7, 200.0, 0.25, dlSend=0.15, pan=0.4),  // 0: Lead saw
        Preset(0, 0.3, 0.3,   2.0, 120.0, 0.6, 100.0, 0.4,  pan=0.5),                // 1: Bass saw
        Preset(3, 0.7, 0.05, 300.0, 400.0, 0.8, 800.0, 0.05, dlSend=0.1, pan=0.55),  // 2: Pad tri
        Preset(4, 0.8, 0.1,   0.5,  40.0, 0.0,  30.0, 0.0,  pan=0.45),               // 3: Hat noise
        Preset(5, 0.9, 0.0,   1.0, 800.0, 0.15, 400.0, 0.0,
            fmRatio=3.0, fmIdx=2.0, dlSend=0.2, pan=0.6),                              // 4: FM bell
        Preset(1, 0.35, 0.3,  1.0, 150.0, 0.1, 120.0, 0.45,
            pw=0.4, dlSend=0.25, pan=0.35),                                             // 5: Pluck pulse
        Preset(2, 0.95, 0.0,  8.0,  50.0, 0.9, 150.0, 0.0, pan=0.5),                 // 6: Sub sine
        Preset(5, 0.45, 0.35, 1.0, 200.0, 0.0,  80.0, 0.25,
            fmRatio=7.0, fmIdx=4.0, dlSend=0.15, pan=0.65),                            // 7: FX fm
    )

    // ======================== VOICE ========================

    inner class Voice(val track: Int) {
        var freq = 0.0
        var phase = 0.0
        var vol = 1.0
        var active = false
        var noteOn = false
        val p get() = voicePresets[track]

        // ADSR state
        var envStage = 0  // 0=off 1=atk 2=dec 3=sus 4=rel
        var envLevel = 0.0
        var envTime = 0.0
        var relStart = 0.0

        // Filter envelope
        var fenvLevel = 0.0
        var fenvTime = 0.0

        // SVF state
        var svfLo = 0.0
        var svfBd = 0.0

        // FM
        var fmPh = 0.0

        // Noise LFSR
        var lfsr = 0x7FFF
        var noiseVal = 0.0

        // Sampler playback
        var samplePos = 0.0
        var sampleInitialized = false

        // Instrument modulation
        var lfo1Phase = 0.0
        var lfo2Phase = 0.0

        fun trigger(f: Double, v: Double) {
            freq = f; vol = v
            samplePos = 0.0
            sampleInitialized = false
            envStage = 1; envTime = 0.0; envLevel = 0.0
            fenvLevel = 1.0; fenvTime = 0.0
            val mod = trackModulations[track]
            if (mod.lfo1.retrigger) lfo1Phase = 0.0
            if (mod.lfo2.retrigger) lfo2Phase = 0.0
            noteOn = true; active = true
            // Clear SVF so a stale filter tail doesn't bleed into the new note
            svfLo = 0.0; svfBd = 0.0
        }

        fun release() {
            if (envStage in 1..3) { relStart = envLevel; envStage = 4; envTime = 0.0 }
            noteOn = false
        }

        fun gen(sampleIdx: Int): Double {
            if (!active || envStage == 0) return 0.0
            val dt = 1.0 / SR
            val pr = p

            // --- ADSR (exponential) ---
            when (envStage) {
                1 -> {
                    envTime += dt
                    val a = pr.atkMs / 1000.0
                    envLevel = if (a < 0.001) 1.0 else 1.0 - exp(-envTime * 5.0 / a)
                    if (envLevel >= 0.99) { envLevel = 1.0; envStage = 2; envTime = 0.0 }
                }
                2 -> {
                    envTime += dt
                    val d = pr.decMs / 1000.0
                    envLevel = if (d < 0.001) pr.sus
                    else pr.sus + (1.0 - pr.sus) * exp(-envTime * 5.0 / d)
                    if (envLevel <= pr.sus + 0.001) { envLevel = pr.sus; envStage = 3 }
                }
                3 -> envLevel = pr.sus
                4 -> {
                    envTime += dt
                    val r = pr.relMs / 1000.0
                    envLevel = if (r < 0.001) 0.0 else relStart * exp(-envTime * 5.0 / r)
                    if (envLevel < 0.0001) { envLevel = 0.0; envStage = 0; active = false; return 0.0 }
                }
            }

            // Filter envelope (decays to 0)
            fenvTime += dt
            fenvLevel = exp(-fenvTime * 6.0).coerceIn(0.0, 1.0)

            // Freq with FX + instrument modulation. Debug helpers are intentionally
            // non-mutating; runtime FX state advances only through this render path.
            val f = runtimeModulatedFrequency(track, sampleIdx)
            val phInc = f / SR

            // --- Oscillator (PolyBLEP where needed) ---
            val raw = when (pr.wave) {
                0 -> { // Saw — PolyBLEP
                    val naive = 2.0 * phase - 1.0
                    naive - polyBlep(phase, phInc)
                }
                1 -> { // Pulse — PolyBLEP
                    val pw = pr.pw
                    val naive = if (phase < pw) 1.0 else -1.0
                    naive + polyBlep(phase, phInc) - polyBlep((phase - pw + 1.0) % 1.0, phInc)
                }
                2 -> sin(phase * TWO_PI)
                3 -> 4.0 * abs(phase - 0.5) - 1.0
                4 -> { // Noise — full-rate LFSR through a one-pole LP (no stepping)
                    val bit = (lfsr xor (lfsr shr 1)) and 1
                    lfsr = (lfsr shr 1) or (bit shl 14)
                    val white = lfsr.toDouble() / 16384.0 - 1.0
                    val lpA = (f / SR * 6.0).coerceIn(0.02, 0.5)
                    noiseVal += lpA * (white - noiseVal)
                    noiseVal
                }
                5 -> { // FM — anti-aliased by shrinking index as mod freq approaches Nyquist
                    fmPh += f * pr.fmRatio / SR
                    if (fmPh > 1e6) fmPh -= floor(fmPh)
                    val modF = f * pr.fmRatio
                    val maxIdx = max(0.0, (SR * 0.4) / max(1.0, modF) - 1.0)
                    val safeIdx = min(pr.fmIdx, maxIdx)
                    val mod = sin(fmPh * TWO_PI) * safeIdx * envLevel
                    sin((phase + mod) * TWO_PI)
                }
                6 -> readSample(track, this)
                7 -> readHyperSynth(track, phase)
                8 -> readMacroSynth(track, phase, phInc)
                else -> 0.0
            }

            phase += phInc
            if (phase >= 1.0) phase -= floor(phase)

            // --- 2-pole SVF (Chamberlin, stability-clamped) ---
            val cutNorm = debugModulatedCutoff(track, -1)
            val cutHz = 20.0 * 2.0.pow(cutNorm.coerceIn(0.0, 1.0) * 10.0)
            val q = max(0.5, 1.0 - pr.reso * 0.95)
            val svfF = min(2.0 * sin(PI * (cutHz / SR).coerceIn(0.0, 0.48)), 2.0 * q - 0.01)

            val hp = raw - svfLo - q * svfBd
            svfBd += svfF * hp
            svfLo += svfF * svfBd
            svfBd = svfBd.coerceIn(-4.0, 4.0)
            svfLo = svfLo.coerceIn(-4.0, 4.0)
            if (!svfBd.isFinite()) svfBd = 0.0
            if (!svfLo.isFinite()) svfLo = 0.0

            val out = svfLo * envLevel * vol * debugModulatedAmp(track, -1)
            advanceVoiceLfos(track)
            return out
        }
    }

    // ======================== STATE ========================

    private val voicePresets = PRESETS.copyOf()
    private val trackSamples = arrayOfNulls<WavDecoder.DecodedWav>(8)
    private val trackSamplers = Array(8) { SamplerParams() }
    private val trackMacroSynths = Array(8) { MacroSynthParams() }
    private val trackHyperSynths = Array(8) { HyperSynthParams() }
    private val trackModulations = Array(8) { ModulationParams() }
    private val runtimeTrackAmp = IntArray(8) { -1 }
    private val runtimeTrackPan = IntArray(8) { -1 }
    private val runtimeTrackDelaySend = IntArray(8) { -1 }
    private val voices = Array(8) { Voice(it) }
    private val dlBufL = DoubleArray(DELAY_LEN)
    private val dlBufR = DoubleArray(DELAY_LEN)
    private var dlPos = 0
    private var dlDampL = 0.0
    private var dlDampR = 0.0
    // DC blocker state (y = x - x_prev + R * y_prev, R = 0.995 ≈ 35 Hz cutoff)
    private var dcXpL = 0.0; private var dcYpL = 0.0
    private var dcXpR = 0.0; private var dcYpR = 0.0
    private val wfBuf = DoubleArray(WAVEFORM_CAPTURE_SIZE)
    private var wfIdx = 0
    private var dbg = 0L
    private val silence = ByteArray(CHUNK_BYTES)
    private val outBuf = ByteArray(CHUNK_BYTES)

    val trackLevels = DoubleArray(8)
    var masterLevelL = 0.0
    var masterLevelR = 0.0
    var swingAmount = 0.15
    var mixerSettings: MixerSettings? = null
    var fxEngine: M8FxEngine? = null

    // ======================== PUBLIC API ========================

    fun configureVoice(track: Int, inst: M8Instrument) {
        if (track !in 0..7) return
        voicePresets[track] = presetFromInstrument(inst, PRESETS[track])
        trackSamplers[track] = inst.sampler.copy()
        trackMacroSynths[track] = inst.macroSynth.copy()
        trackHyperSynths[track] = inst.hyperSynth.copy()
        trackModulations[track] = inst.modulation.copy(
            env1 = inst.modulation.env1.copy(),
            env2 = inst.modulation.env2.copy(),
            lfo1 = inst.modulation.lfo1.copy(),
            lfo2 = inst.modulation.lfo2.copy(),
        )
    }

    fun applyInstrument(trackIndex: Int, instrument: M8Instrument) = configureVoice(trackIndex, instrument)

    fun loadSample(track: Int, sample: WavDecoder.DecodedWav?) {
        if (track !in 0..7) return
        trackSamples[track] = sample
    }

    fun setRuntimeTrackAmp(track: Int, value: Int) {
        if (track !in 0..7) return
        runtimeTrackAmp[track] = value.coerceIn(0, 0xFF)
    }

    fun setRuntimeTrackPan(track: Int, value: Int) {
        if (track !in 0..7) return
        runtimeTrackPan[track] = value.coerceIn(0, 0xFF)
    }

    fun setRuntimeTrackDelaySend(track: Int, value: Int) {
        if (track !in 0..7) return
        runtimeTrackDelaySend[track] = value.coerceIn(0, 0xFF)
    }

    fun clearRuntimeTrackOverrides(track: Int) {
        if (track !in 0..7) return
        runtimeTrackAmp[track] = -1
        runtimeTrackPan[track] = -1
        runtimeTrackDelaySend[track] = -1
    }

    fun getVoiceFreq(track: Int): Double = if (track in 0..7) voices[track].freq else 0.0
    fun getSamplePosition(track: Int): Double = if (track in 0..7) voices[track].samplePos else 0.0
    fun isVoiceActive(track: Int): Boolean = track in 0..7 && voices[track].active

    fun debugModulatedFrequency(track: Int, sampleIdx: Int): Double {
        if (track !in 0..7) return 0.0
        val voice = voices[track]
        return applyPitchModulation(track, voice.freq)
    }

    private fun runtimeModulatedFrequency(track: Int, sampleIdx: Int): Double {
        val voice = voices[track]
        val fxFreq = fxEngine?.getFreqModifier(track, voice.freq, sampleIdx) ?: voice.freq
        return applyPitchModulation(track, fxFreq)
    }

    private fun applyPitchModulation(track: Int, baseFreq: Double): Double {
        val semitones = modulationSum(track, ModDestination.PITCH) * 2.0
        return baseFreq * 2.0.pow(semitones / 12.0)
    }

    fun debugModulatedCutoff(track: Int, sampleIdx: Int): Double {
        if (track !in 0..7) return 0.0
        val voice = voices[track]
        val pr = voicePresets[track]
        val routedCutoffEnv = trackModulations[track].env2.dest == ModDestination.CUTOFF
        val legacyFilterEnvelope = if (routedCutoffEnv) 0.0 else pr.filtEnv * voice.fenvLevel
        return (pr.cutoff + legacyFilterEnvelope + modulationSum(track, ModDestination.CUTOFF)).coerceIn(0.0, 1.0)
    }

    fun debugModulatedAmp(track: Int, sampleIdx: Int): Double {
        if (track !in 0..7) return 1.0
        return (1.0 + modulationSum(track, ModDestination.AMP) * 0.75).coerceIn(0.0, 2.0)
    }

    private fun modulationSum(track: Int, destination: Int): Double {
        val mod = trackModulations[track]
        val voice = voices[track]
        var value = 0.0
        value += envContribution(mod.env2, destination, voice.fenvLevel)
        value += lfoContribution(mod.lfo1, destination, voice.lfo1Phase)
        value += lfoContribution(mod.lfo2, destination, voice.lfo2Phase)
        return value
    }

    private fun advanceVoiceLfos(track: Int) {
        val mod = trackModulations[track]
        val voice = voices[track]
        voice.lfo1Phase = advanceLfoPhase(voice.lfo1Phase, mod.lfo1)
        voice.lfo2Phase = advanceLfoPhase(voice.lfo2Phase, mod.lfo2)
    }

    private fun envContribution(env: Envelope, destination: Int, level: Double): Double =
        if (env.dest == destination) modAmount(env.amount) * level else 0.0

    private fun lfoContribution(lfo: Lfo, destination: Int, phase: Double): Double =
        if (lfo.dest == destination) modAmount(lfo.amount) * lfoValue(lfo.shape, phase) else 0.0

    private fun modAmount(amount: Int): Double = ((amount.coerceIn(0, 0xFF) - 0x80) / 127.0).coerceIn(-1.0, 1.0)

    private fun advanceLfoPhase(phase: Double, lfo: Lfo): Double {
        val hz = 0.05 + (lfo.speed.coerceIn(0, 0xFF) / 255.0) * 16.0
        val next = phase + hz / SR
        return next - floor(next)
    }

    private fun lfoValue(shape: LfoShape, phase: Double): Double = when (shape) {
        LfoShape.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
        LfoShape.SINE -> sin(phase * TWO_PI)
        LfoShape.RAMP_DOWN -> 1.0 - 2.0 * phase
        LfoShape.RAMP_UP -> 2.0 * phase - 1.0
        LfoShape.SQUARE -> if (phase < 0.5) 1.0 else -1.0
        LfoShape.RANDOM -> sin(floor(phase * 64.0) * 12.9898) % 1.0
        LfoShape.DRUNK -> sin(phase * TWO_PI) * 0.6 + sin(phase * TWO_PI * 0.37) * 0.4
    }.coerceIn(-1.0, 1.0)

    fun releaseTrack(track: Int) {
        if (track !in 0..7) return
        voices[track].noteOn = false
        voices[track].envStage = 0
        voices[track].active = false
    }

    fun triggerRow(rowData: Array<IntArray>) {
        for (t in 0 until min(8, rowData.size)) {
            val note = rowData[t][0]
            val vol = rowData[t][2]
            when {
                note == NOTE_OFF -> voices[t].release()
                note in 1..127 -> voices[t].trigger(noteToFreq(note), if (vol > 0) vol / 255.0 else 0.8)
            }
        }
    }

    fun generateChunk(): ByteArray {
        // ---- Silence gate ----
        // If no voice is active, flush the delay buffers so no stale tail or
        // denormal dust leaks through, and return pure digital zeros. This
        // guarantees true silence before the first note arrives and between
        // notes while the sequencer idles on empty rows.
        if (voices.none { it.active }) {
            for (k in dlBufL.indices) { dlBufL[k] = 0.0; dlBufR[k] = 0.0 }
            dlDampL = 0.0; dlDampR = 0.0
            dcXpL = 0.0; dcYpL = 0.0; dcXpR = 0.0; dcYpR = 0.0
            masterLevelL *= 0.7; masterLevelR *= 0.7
            for (t in 0 until 8) trackLevels[t] *= 0.7
            dbg++
            return silence
        }

        val buf = outBuf
        val tPk = DoubleArray(8)
        var pkL = 0.0; var pkR = 0.0
        val mx = mixerSettings

        for (i in 0 until CHUNK_SAMPLES) {
            var mixL = 0.0; var mixR = 0.0
            var dlInL = 0.0; var dlInR = 0.0

            for (t in 0 until 8) {
                val s = voices[t].gen(i)
                if (s == 0.0) continue

                val tVol = when {
                    runtimeTrackAmp[t] >= 0 -> runtimeTrackAmp[t] / 255.0
                    mx != null -> mx.trackVolumes[t] / 255.0
                    else -> voicePresets[t].amp
                }
                val pan = when {
                    runtimeTrackPan[t] >= 0 -> runtimeTrackPan[t] / 255.0
                    mx != null -> mx.trackPans[t] / 255.0
                    else -> voicePresets[t].pan
                }
                val scaled = s * tVol
                val pL = cos(pan * PI * 0.5)
                val pR = sin(pan * PI * 0.5)
                val sL = scaled * pL
                val sR = scaled * pR
                mixL += sL; mixR += sR

                // Delay send
                val ds = if (runtimeTrackDelaySend[t] >= 0) runtimeTrackDelaySend[t] / 255.0 else voicePresets[t].dlSend
                if (ds > 0.0) { dlInL += sL * ds; dlInR += sR * ds }

                val pk = abs(scaled)
                if (pk > tPk[t]) tPk[t] = pk
            }

            // Stereo delay with damped cross-feedback
            val tapL = dlBufL[dlPos]
            val tapR = dlBufR[dlPos]
            dlDampL += 0.35 * (tapR - dlDampL) // cross-feed R→L, damped
            dlDampR += 0.35 * (tapL - dlDampR) // cross-feed L→R, damped
            dlBufL[dlPos] = (dlInL + dlDampL * 0.4).coerceIn(-2.0, 2.0)
            dlBufR[dlPos] = (dlInR + dlDampR * 0.4).coerceIn(-2.0, 2.0)
            dlPos = (dlPos + 1) % DELAY_LEN

            var outL = mixL + tapL * 0.3
            var outR = mixR + tapR * 0.3

            // Master gain
            val mVol = if (mx != null) mx.masterVolume / 255.0 else 0.85
            outL *= mVol * 0.3
            outR *= mVol * 0.3

            // Transparent limiter — passes signal below 0.9 untouched
            outL = softLimit(outL)
            outR = softLimit(outR)

            // DC blocker — removes sub-bass rumble from delay recirculation
            val dcL = outL - dcXpL + 0.995 * dcYpL; dcXpL = outL; dcYpL = dcL; outL = dcL
            val dcR = outR - dcXpR + 0.995 * dcYpR; dcXpR = outR; dcYpR = dcR; outR = dcR

            if (abs(outL) > pkL) pkL = abs(outL)
            if (abs(outR) > pkR) pkR = abs(outR)

            wfBuf[wfIdx] = (outL + outR) * 0.5
            wfIdx = (wfIdx + 1) % WAVEFORM_CAPTURE_SIZE

            val sL = (outL * 32000.0).toInt().coerceIn(-32768, 32767)
            val sR = (outR * 32000.0).toInt().coerceIn(-32768, 32767)
            val off = i * 4
            buf[off] = (sL and 0xFF).toByte()
            buf[off + 1] = (sL shr 8).toByte()
            buf[off + 2] = (sR and 0xFF).toByte()
            buf[off + 3] = (sR shr 8).toByte()
        }

        masterLevelL = masterLevelL * 0.7 + pkL * 0.3
        masterLevelR = masterLevelR * 0.7 + pkR * 0.3
        for (t in 0 until 8) trackLevels[t] = trackLevels[t] * 0.7 + tPk[t] * 0.3

        dbg++
        if (dbg % 60 == 0L) {
            Log.d(TAG, "c=$dbg a=${voices.count { it.active }} L=${"%.3f".format(masterLevelL)} R=${"%.3f".format(masterLevelR)}")
        }

        return buf
    }

    fun generateSilence(): ByteArray {
        masterLevelL *= 0.9; masterLevelR *= 0.9
        for (t in 0 until 8) trackLevels[t] *= 0.9
        return silence
    }

    fun allNotesOff() {
        for (v in voices) { v.noteOn = false; v.envStage = 0; v.active = false; v.svfLo = 0.0; v.svfBd = 0.0 }
        masterLevelL = 0.0; masterLevelR = 0.0; trackLevels.fill(0.0)
    }

    fun getWaveformData(): ByteArray {
        val out = ByteArray(WAVEFORM_CAPTURE_SIZE)
        for (i in 0 until WAVEFORM_CAPTURE_SIZE)
            out[i] = (128 + wfBuf[i] * 100).toInt().coerceIn(0, 255).toByte()
        return out
    }

    fun getSwingDelaySamples(row: Int, bpm: Int): Int {
        if (row % 2 == 0 || swingAmount <= 0.0) return 0
        return (SR * 60.0 / (bpm * 4.0) * swingAmount).toInt()
    }

    private fun presetFromInstrument(inst: M8Instrument, fallback: Preset): Preset {
        val wave = when (inst.type) {
            InstrumentType.WAVSYNTH -> when (inst.wavSynth.shape) {
                WavShape.SAW -> 0
                WavShape.PULSE_12, WavShape.PULSE_25, WavShape.PULSE_50, WavShape.PULSE_75 -> 1
                WavShape.SINE -> 2
                WavShape.TRIANGLE -> 3
                WavShape.NOISE, WavShape.NOISE_PITCH -> 4
                WavShape.OVERFLOW -> 0
            }
            InstrumentType.FM_SYNTH -> 5
            InstrumentType.MACROSYNTH -> 8
            InstrumentType.HYPERSYNTH -> 7
            InstrumentType.SAMPLER -> 6
            InstrumentType.MIDI_OUT -> fallback.wave
        }
        val env = inst.modulation.env1
        val attackMs = hexToEnvelopeMs(env.attack, minMs = 0.5, maxMs = 1_500.0)
        val decayMs = hexToEnvelopeMs(env.decay, minMs = 5.0, maxMs = 2_500.0)
        val releaseMs = hexToEnvelopeMs(env.release, minMs = 5.0, maxMs = 3_000.0)
        val sustain = (env.sustain / 255.0).coerceIn(0.0, 1.0)
        val fm = inst.fmSynth
        return fallback.copy(
            wave = wave,
            cutoff = (inst.filter.cutoff / 255.0).coerceIn(0.0, 1.0),
            reso = (inst.filter.resonance / 255.0).coerceIn(0.0, 1.0),
            atkMs = attackMs,
            decMs = decayMs,
            sus = sustain,
            relMs = releaseMs,
            filtEnv = ((inst.modulation.env2.amount - 0x80) / 127.0).coerceIn(0.0, 1.0),
            pw = wavPulseWidth(inst.wavSynth.shape),
            fmRatio = max(1.0, fm.op2Ratio.toDouble()),
            fmIdx = (fm.op2Level / 255.0 * 5.0).coerceIn(0.0, 5.0),
            dlSend = (inst.amp.delaySend / 255.0).coerceIn(0.0, 1.0),
            pan = (inst.amp.pan / 255.0).coerceIn(0.0, 1.0),
            amp = (inst.amp.amp / 255.0).coerceIn(0.0, 1.0),
        )
    }

    private fun wavPulseWidth(shape: WavShape): Double = when (shape) {
        WavShape.PULSE_12 -> 0.125
        WavShape.PULSE_25 -> 0.25
        WavShape.PULSE_75 -> 0.75
        else -> 0.5
    }

    private fun hexToEnvelopeMs(v: Int, minMs: Double, maxMs: Double): Double {
        val x = (v / 255.0).coerceIn(0.0, 1.0)
        return minMs + (maxMs - minMs) * x * x
    }

    private fun readHyperSynth(track: Int, ph: Double): Double {
        val h = trackHyperSynths[track]
        val swarm = (h.swarm / 255.0).coerceIn(0.0, 1.0)
        val spreadCents = 2.0 + swarm * 42.0
        val shift = (h.shift / 255.0).coerceIn(0.0, 1.0)
        val sub = (h.subOsc / 255.0).coerceIn(0.0, 1.0)
        val intervals = hyperIntervals(h.chordBank, h.chord, shift)
        val detunes = doubleArrayOf(-1.0, -0.55, -0.2, 0.0, 0.16, 0.43, 0.78, 1.0)
        var sum = 0.0
        for (i in detunes.indices) {
            val cents = detunes[i] * spreadCents
            val ratio = 2.0.pow((intervals[i % intervals.size] + cents / 100.0) / 12.0)
            val p = (ph * ratio + i * 0.137) % 1.0
            sum += 2.0 * p - 1.0
        }
        if (sub > 0.0) sum += sin(ph * 0.5 * TWO_PI) * sub * 2.0
        return (sum / (8.0 + sub * 2.0)).coerceIn(-1.0, 1.0)
    }

    private fun hyperIntervals(bank: Int, chord: Int, shift: Double): DoubleArray {
        val base = when (bank.coerceIn(0, HyperSynthParams.CHORD_BANK_NAMES.lastIndex)) {
            1 -> doubleArrayOf(0.0, 3.0, 7.0, 10.0)
            2 -> doubleArrayOf(0.0, 2.0, 7.0, 12.0)
            3 -> doubleArrayOf(0.0, 5.0, 7.0, 12.0)
            4 -> doubleArrayOf(0.0, 4.0, 7.0, 10.0)
            5 -> doubleArrayOf(0.0, 4.0, 7.0, 11.0)
            6 -> doubleArrayOf(0.0, 3.0, 7.0, 10.0)
            7 -> doubleArrayOf(0.0, 3.0, 6.0, 9.0)
            8 -> doubleArrayOf(0.0, 4.0, 8.0, 12.0)
            9 -> doubleArrayOf(0.0, 7.0, 12.0, 19.0)
            else -> doubleArrayOf(0.0, 4.0, 7.0, 12.0)
        }
        val octave = ((chord and 0x0F) % 3) * 12.0
        return DoubleArray(4) { i -> base[i] + octave * shift }
    }

    private fun readMacroSynth(track: Int, ph: Double, phInc: Double): Double {
        val m = trackMacroSynths[track]
        val timbre = (m.timbre / 255.0).coerceIn(0.0, 1.0)
        val color = (m.color / 255.0).coerceIn(0.0, 1.0)
        var value = when (m.model.coerceIn(0, MacroSynthParams.MODEL_NAMES.lastIndex)) {
            0 -> { // CSAW: animated saw/pulse blend
                val saw = polySaw(ph, phInc)
                val pulse = polyPulse(ph, phInc, 0.2 + timbre * 0.6)
                saw * (1.0 - color) + pulse * color
            }
            1, 2, 3 -> { // Morph / saw-square / sine-triangle families
                val a = if (m.model == 3) sin(ph * TWO_PI) else polySaw(ph, phInc)
                val b = if (m.model == 3) 4.0 * abs(ph - 0.5) - 1.0 else polyPulse(ph, phInc, 0.5)
                a * (1.0 - timbre) + b * timbre
            }
            5 -> { // SQUARE SUB
                polyPulse(ph, phInc, 0.5) * (0.7 + color * 0.2) + sin(ph * 0.5 * TWO_PI) * (0.15 + timbre * 0.35)
            }
            6 -> { // SAW SUB
                polySaw(ph, phInc) * 0.75 + sin(ph * 0.5 * TWO_PI) * (0.1 + timbre * 0.4)
            }
            9 -> { // TRIPLE SAW
                (polySaw(ph, phInc) + polySaw((ph * 1.005 + 0.17) % 1.0, phInc) + polySaw((ph * 0.995 + 0.31) % 1.0, phInc)) / 3.0
            }
            10 -> { // TRIPLE SQUARE
                (polyPulse(ph, phInc, 0.45) + polyPulse((ph * 1.003 + 0.23) % 1.0, phInc, 0.5) + polyPulse((ph * 0.997 + 0.41) % 1.0, phInc, 0.55)) / 3.0
            }
            in 34..37, 42, 43 -> { // drum/noise-like models
                val bit = ((sin((ph * (97.0 + timbre * 400.0)) * TWO_PI) * 43758.5453) % 1.0)
                (bit * 2.0 - 1.0) * (0.5 + color * 0.5)
            }
            else -> polySaw(ph, phInc) * (1.0 - timbre * 0.5) + sin((ph * (1.0 + color * 3.0)) * TWO_PI) * timbre * 0.5
        }
        if (m.redux > 0) {
            val levels = max(2.0, 256.0 - m.redux.toDouble()).toInt()
            value = kotlin.math.round(value * levels) / levels
        }
        return value.coerceIn(-1.0, 1.0)
    }

    private fun polySaw(ph: Double, phInc: Double): Double {
        val p = ph % 1.0
        return (2.0 * p - 1.0) - polyBlep(p, phInc)
    }

    private fun polyPulse(ph: Double, phInc: Double, pw: Double): Double {
        val p = ph % 1.0
        val width = pw.coerceIn(0.05, 0.95)
        val naive = if (p < width) 1.0 else -1.0
        return naive + polyBlep(p, phInc) - polyBlep((p - width + 1.0) % 1.0, phInc)
    }

    private fun readSample(track: Int, voice: Voice): Double {
        val sample = trackSamples.getOrNull(track) ?: run {
            voice.release()
            return 0.0
        }
        val sampler = trackSamplers[track]
        val startFrame = samplerFrame(sampler.start, sample.frameCount)
        val endFrame = max(startFrame + 1, samplerFrame(sampler.length, sample.frameCount))
            .coerceAtMost(sample.frameCount)
        val loopStart = samplerFrame(sampler.loopStart, sample.frameCount).coerceIn(startFrame, endFrame - 1)
        val looping = sampler.playMode == 2 || sampler.playMode == 3 || sampler.playMode == 4 || sampler.playMode == 5 || sampler.playMode == 6

        if (!voice.sampleInitialized) {
            voice.samplePos = startFrame.toDouble()
            voice.sampleInitialized = true
        }

        if (voice.samplePos >= endFrame) {
            if (looping) {
                val loopLen = max(1.0, endFrame - loopStart.toDouble())
                voice.samplePos = loopStart + ((voice.samplePos - loopStart) % loopLen)
            } else {
                voice.release()
                return 0.0
            }
        }

        val frame = voice.samplePos.toInt().coerceIn(startFrame, endFrame - 1)
        val frac = voice.samplePos - frame
        val nextFrame = when {
            frame + 1 < endFrame -> frame + 1
            looping -> loopStart
            else -> frame
        }
        var value = sampleAt(sample, frame) * (1.0 - frac) + sampleAt(sample, nextFrame) * frac

        if (sampler.degrade > 0) {
            val levels = max(2.0, 256.0 - sampler.degrade.toDouble()).toInt()
            value = kotlin.math.round(value * levels) / levels
        }

        val baseStep = sample.sampleRate.toDouble() / SR
        val noteRatio = (voice.freq / noteToFreq(60)).coerceIn(0.125, 8.0)
        val detuneRatio = 2.0.pow(((sampler.detune - 0x80) / 128.0) / 12.0)
        voice.samplePos += baseStep * noteRatio * detuneRatio

        if (voice.samplePos >= endFrame && looping) {
            val loopLen = max(1.0, endFrame - loopStart.toDouble())
            voice.samplePos = loopStart + ((voice.samplePos - loopStart) % loopLen)
        }

        return value
    }

    private fun samplerFrame(hex: Int, frameCount: Int): Int =
        ((hex.coerceIn(0, 0xFF) / 255.0) * max(0, frameCount - 1)).toInt()

    private fun sampleAt(sample: WavDecoder.DecodedWav, frame: Int): Double {
        val safeFrame = frame.coerceIn(0, sample.frameCount - 1)
        val base = safeFrame * sample.channels
        var value = sample.samples[base].toDouble()
        if (sample.channels == 2) value = (value + sample.samples[base + 1]) * 0.5
        return value
    }

    /** Cheap soft limiter — no transcendentals. Linear below 0.85, cubic squash above. */
    private fun softLimit(x: Double): Double {
        if (x > 0.85) { val d = x - 0.85; return 0.85 + d / (1.0 + d * 6.0) }
        if (x < -0.85) { val d = -x - 0.85; return -(0.85 + d / (1.0 + d * 6.0)) }
        return x
    }
}
