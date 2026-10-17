package com.m8droid

import com.m8droid.audio.WavDecoder
import com.m8droid.emulator.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

/**
 * Headless wiring guards plus real Kotlin PCM checks. These deliberately do not
 * instantiate Android AudioTrack or claim device playback. The source guards
 * cover the ViewModel boundary that engine-only tests missed when JNI bypassed it.
 */
class AudioWiringRegressionTest {
    private val source: String by lazy {
        val relative = "src/main/java/com/m8droid/M8ViewModel.kt"
        listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
    }

    private fun body(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue(start >= 0, "missing function $name")
        val open = source.indexOf('{', start)
        var depth = 1
        var end = open + 1
        while (depth > 0) {
            when (source[end++]) { '{' -> depth++; '}' -> depth-- }
        }
        return source.substring(open + 1, end - 1)
    }

    @Test
    fun `view model has one Kotlin engine for configuration output FX and meters`() {
        assertFalse(source.contains("NativeSynth"), "JNI cannot carry instrument or sample configuration")
        assertTrue(source.contains("private val synth = M8Synth()"))
        val configure = body("configureTrackInstrument")
        assertTrue(configure.contains("synth.configureVoice(track, inst)"))
        assertTrue(configure.contains("synth.loadSample(track, sampleCache.load(inst.sampler.samplePath))"))
        assertTrue(configure.contains("synth.loadSample(track, null)"))
        val start = body("startLocalEmulator")
        listOf("synth.mixerSettings = song.mixer", "synth.fxEngine = fxEngine",
            "val pcm = synth.generateChunk()", "localAudioPlayer.write(pcm)",
            "localAudioPlayer.write(synth.generateChunk())", "synth.getWaveformData()",
            "synth.trackLevels.clone()", "synth.masterLevelL", "synth.masterLevelR"
        ).forEach { assertTrue(start.contains(it), "missing audio route: $it") }
        assertFalse(start.contains("localAudioPlayer.write(ByteArray"), "stopped output must not discard auditions")
        listOf("triggerCurrentRow", "processRuntimeFxTick").forEach { name ->
            listOf("synth.setRuntimeTrackAmp", "synth.setRuntimeTrackPan", "synth.setRuntimeTrackDelaySend")
                .forEach { assertTrue(body(name).contains(it), "$name must update the audible engine: $it") }
        }
    }

    @Test
    fun `sequencer row audition and delayed notes configure before Kotlin trigger`() {
        listOf("triggerCurrentRow", "previewRowAtCursor", "triggerRuntimeTrack").forEach { name ->
            val body = body(name)
            val configure = body.indexOf("configureTrackInstrument(")
            val trigger = body.indexOf("synth.triggerRow(")
            assertTrue(configure >= 0 && trigger > configure, "$name must configure then trigger the same synth")
        }
        assertTrue(body("previewNote").contains("triggerRuntimeTrack(track, safeNote, safeInst, velocity)"))
        assertTrue(body("handleMidiNoteOn").contains("synth.triggerRow("))
        assertTrue(body("handleMidiNoteOff").contains("synth.triggerRow("))
        assertTrue(body("handleMidiNoteOff").contains("M8Synth.NOTE_OFF"))
    }

    @Test
    fun `stop lifecycle and tick releases target the audible engine`() {
        assertTrue(body("releaseRuntimeTrack").contains("synth.releaseTrack(track)"))
        assertTrue(body("stopPlayback").contains("stopAudioRequested = true"))
        val start = body("startLocalEmulator")
        assertTrue(start.contains("if (stopAudioRequested)"))
        assertTrue(start.contains("synth.allNotesOff()"))
        assertTrue(start.contains("releaseAllMidiOut()"))
        assertTrue(body("stopEmulator").contains("synth.allNotesOff()"))
        assertTrue(body("stopEmulator").contains("localAudioPlayer.stop()"))
        assertTrue(body("onCleared").contains("stopEmulator()"))
    }

    @Test
    fun `selected instrument slot changes the PCM sent by the Kotlin route`() {
        fun render(shape: WavShape): ByteArray {
            val instruments = Array(128) { instrument() }
            instruments[42].wavSynth.shape = shape
            val synth = M8Synth()
            val row = row(instrument = 42)
            synth.configureVoice(0, instruments[row[0][1]])
            synth.triggerRow(row)
            return synth.generateChunk().copyOf()
        }
        val sine = render(WavShape.SINE)
        val saw = render(WavShape.SAW)
        assertTrue(peak(sine, 0) > 0)
        assertTrue(peak(saw, 0) > 0)
        assertFalse(sine.contentEquals(saw))
    }

    @Test
    fun `loaded sampler data changes output on the configured audible voice`() {
        fun render(samples: FloatArray): ByteArray {
            val synth = M8Synth()
            synth.configureVoice(0, instrument().apply {
                type = InstrumentType.SAMPLER
                sampler.length = 0xFF
                sampler.detune = 0x80
            })
            synth.loadSample(0, WavDecoder.DecodedWav(M8Synth.SAMPLE_RATE, 1, samples))
            synth.triggerRow(row())
            return synth.generateChunk().copyOf()
        }
        val silent = render(FloatArray(4000))
        val sample = render(FloatArray(4000) { if (it % 80 < 40) 0.7f else -0.7f })
        assertEquals(0, peak(silent, 0))
        assertTrue(peak(sample, 0) > 0)
        assertFalse(silent.contentEquals(sample))
    }

    @Test
    fun `row FX reaches PCM and meters not an unused engine`() {
        val synth = M8Synth()
        val fx = M8FxEngine()
        synth.fxEngine = fx
        val result = fx.processStepFx(0, PhraseStep(note = 60,
            fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0), 0, 60)
        synth.setRuntimeTrackPan(0, result.panOverride)
        // Match the ViewModel ordering: FX before instrument configuration.
        synth.configureVoice(0, instrument())
        synth.triggerRow(row())
        val pcm = synth.generateChunk()
        assertTrue(peak(pcm, 0) > 0)
        assertEquals(0, peak(pcm, 1))
        assertTrue(synth.masterLevelL > 0)
        assertEquals(0.0, synth.masterLevelR)
        assertTrue(synth.trackLevels[0] > 0)
        assertTrue(synth.getWaveformData().any { (it.toInt() and 255) != 128 })
    }

    @Test
    fun `note off tick kill and stop silence the rendered voice`() {
        val synth = M8Synth()
        synth.configureVoice(0, instrument().apply { modulation.env1.release = 0 })
        synth.triggerRow(row())
        assertTrue(peak(synth.generateChunk(), 0) > 0)
        synth.triggerRow(row(note = M8Synth.NOTE_OFF))
        repeat(4) { synth.generateChunk() }
        assertTrue(synth.generateChunk().all { it == 0.toByte() })
        synth.triggerRow(row())
        synth.releaseTrack(0)
        assertTrue(synth.generateChunk().all { it == 0.toByte() })
        synth.triggerRow(row())
        synth.generateChunk()
        synth.allNotesOff()
        assertTrue(synth.generateChunk().all { it == 0.toByte() })
        assertEquals(0.0, synth.masterLevelL)
        assertTrue(synth.trackLevels.all { it == 0.0 })
    }

    private fun instrument() = M8Instrument("WIRING", InstrumentType.WAVSYNTH).apply {
        wavSynth.shape = WavShape.SINE
        filter.cutoff = 0xFF
        filter.resonance = 0
        amp.amp = 0xFF
        amp.pan = 0x80
        amp.delaySend = 0
        modulation.env1 = Envelope(attack = 0, decay = 0xFF, sustain = 0xFF, release = 0)
    }

    private fun row(note: Int = 60, instrument: Int = 0) = Array(8) { track ->
        if (track == 0) intArrayOf(note, instrument, 255, 0, 0) else intArrayOf(0, track, 0, 0, 0)
    }

    private fun peak(pcm: ByteArray, channel: Int): Int =
        (channel * 2 until pcm.size - 1 step 4).maxOf { i ->
            abs(((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort().toInt())
        }
}
