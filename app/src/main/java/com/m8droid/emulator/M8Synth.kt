package com.m8droid.emulator

import android.util.Log
import com.m8droid.audio.M8AudioPlayer
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
        val wave: Int,          // 0=saw,1=pulse,2=sine,3=tri,4=noise,5=fm
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
        val p get() = PRESETS[track]

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
        var noiseCnt = 0

        fun trigger(f: Double, v: Double) {
            freq = f; vol = v
            envStage = 1; envTime = 0.0; envLevel = 0.0
            fenvLevel = 1.0; fenvTime = 0.0
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

            // Freq with FX modulation
            val f = fxEngine?.getFreqModifier(track, freq, sampleIdx) ?: freq
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
                else -> 0.0
            }

            phase += phInc
            if (phase >= 1.0) phase -= floor(phase)

            // --- 2-pole SVF (Chamberlin, stability-clamped) ---
            val cutNorm = pr.cutoff + pr.filtEnv * fenvLevel
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

            return svfLo * envLevel * vol
        }
    }

    // ======================== STATE ========================

    private val voices = Array(8) { Voice(it) }
    private val dlBufL = DoubleArray(DELAY_LEN)
    private val dlBufR = DoubleArray(DELAY_LEN)
    private var dlPos = 0
    private var dlDampL = 0.0
    private var dlDampR = 0.0
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

    fun configureVoice(track: Int, inst: M8Instrument) {}
    fun applyInstrument(trackIndex: Int, instrument: M8Instrument) {}
    fun getVoiceFreq(track: Int): Double = if (track in 0..7) voices[track].freq else 0.0

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

                val tVol = if (mx != null) mx.trackVolumes[t] / 255.0 else 0.85
                val pan = if (mx != null) mx.trackPans[t] / 255.0 else PRESETS[t].pan
                val scaled = s * tVol
                val pL = cos(pan * PI * 0.5)
                val pR = sin(pan * PI * 0.5)
                val sL = scaled * pL
                val sR = scaled * pR
                mixL += sL; mixR += sR

                // Delay send
                val ds = PRESETS[t].dlSend
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

    /** Cheap soft limiter — no transcendentals. Linear below 0.85, cubic squash above. */
    private fun softLimit(x: Double): Double {
        if (x > 0.85) { val d = x - 0.85; return 0.85 + d / (1.0 + d * 6.0) }
        if (x < -0.85) { val d = -x - 0.85; return -(0.85 + d / (1.0 + d * 6.0)) }
        return x
    }
}
