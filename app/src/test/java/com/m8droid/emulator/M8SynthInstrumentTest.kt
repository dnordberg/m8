package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M8SynthInstrumentTest {
    @Test
    fun `applyInstrument changes rendered tone for the same note`() {
        val synth = M8Synth()
        val row = arrayOf(IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 }, IntArray(3) { 0 })
        row[0][0] = 60
        row[0][2] = 220

        synth.applyInstrument(
            0,
            M8Instrument("SINE", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SINE
                filter.cutoff = 0xFF
                filter.resonance = 0x00
                amp.amp = 0xFF
                amp.pan = 0x80
                amp.delaySend = 0x00
                modulation.env1 = Envelope(attack = 0x00, decay = 0xFF, sustain = 0xFF, release = 0x40)
            },
        )
        synth.triggerRow(row)
        val sine = synth.generateChunk().copyOf()

        synth.allNotesOff()
        synth.applyInstrument(
            0,
            M8Instrument("NOISE", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.NOISE
                filter.cutoff = 0xFF
                filter.resonance = 0x00
                amp.amp = 0xFF
                amp.pan = 0x80
                amp.delaySend = 0x00
                modulation.env1 = Envelope(attack = 0x00, decay = 0xFF, sustain = 0xFF, release = 0x40)
            },
        )
        synth.triggerRow(row)
        val noise = synth.generateChunk().copyOf()

        assertNotEquals(sine.toList(), noise.toList())
    }

    @Test
    fun `configured pan controls stereo balance`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            M8Instrument("LEFT", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SINE
                filter.cutoff = 0xFF
                amp.amp = 0xFF
                amp.pan = 0x00
                amp.delaySend = 0x00
                modulation.env1 = Envelope(attack = 0x00, decay = 0xFF, sustain = 0xFF, release = 0x40)
            },
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 69
        row[0][2] = 255
        synth.triggerRow(row)
        val pcm = synth.generateChunk()

        val leftPeak = peakChannel(pcm, 0)
        val rightPeak = peakChannel(pcm, 1)

        assertTrue(leftPeak > rightPeak * 3, "expected left-heavy output, L=$leftPeak R=$rightPeak")
    }

    @Test
    fun `releaseTrack releases active voice for tick fx kill`() {
        val synth = M8Synth()
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255
        synth.triggerRow(row)
        assertTrue(synth.isVoiceActive(0))

        synth.releaseTrack(0)

        assertTrue(!synth.isVoiceActive(0))
    }

    @Test
    fun `track indices outside eight voices are ignored`() {
        val synth = M8Synth()
        synth.applyInstrument(99, M8Instrument("IGNORED"))
        assertEquals(0.0, synth.getVoiceFreq(99))
    }

    @Test
    fun `sampler instrument renders loaded wav sample data`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            samplerInstrument(),
        )
        synth.loadSample(
            0,
            com.m8droid.audio.WavDecoder.DecodedWav(
                sampleRate = M8Synth.SAMPLE_RATE,
                channels = 1,
                samples = FloatArray(2_000) { if (it % 2 == 0) 0.8f else -0.8f },
            ),
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255

        synth.triggerRow(row)
        val pcm = synth.generateChunk()

        assertTrue(peakChannel(pcm, 0) > 0)
    }

    @Test
    fun `sampler pitch follows played note instead of fixed file rate`() {
        val synth = M8Synth()
        synth.applyInstrument(0, samplerInstrument())
        synth.loadSample(
            0,
            com.m8droid.audio.WavDecoder.DecodedWav(
                sampleRate = M8Synth.SAMPLE_RATE,
                channels = 1,
                samples = FloatArray(8_000) { 0.5f },
            ),
        )
        val row = Array(8) { IntArray(3) }
        row[0][2] = 255

        row[0][0] = 60
        synth.triggerRow(row)
        synth.generateChunk()
        val c4Pos = synth.getSamplePosition(0)

        synth.allNotesOff()
        row[0][0] = 72
        synth.triggerRow(row)
        synth.generateChunk()
        val c5Pos = synth.getSamplePosition(0)

        assertTrue(c5Pos > c4Pos * 1.9, "C5 should advance sample playback about twice as fast as C4; C4=$c4Pos C5=$c5Pos")
    }

    @Test
    fun `sampler forward loop wraps between loop start and length`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            samplerInstrument().apply {
                sampler.playMode = 2 // FWDLOOP
                sampler.loopStart = 0x40
                sampler.length = 0x80
            },
        )
        synth.loadSample(
            0,
            com.m8droid.audio.WavDecoder.DecodedWav(
                sampleRate = M8Synth.SAMPLE_RATE,
                channels = 1,
                samples = FloatArray(1_000) { 0.4f },
            ),
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255

        synth.triggerRow(row)
        repeat(4) { synth.generateChunk() }

        assertTrue(synth.isVoiceActive(0), "looped sampler voice should stay active after passing sample end")
        assertTrue(synth.getSamplePosition(0) in 250.0..510.0, "loop should wrap inside loop window, pos=${synth.getSamplePosition(0)}")
    }

    @Test
    fun `hypersynth renders distinct detuned oscillator stack`() {
        val saw = renderInstrument(
            stableInstrument("SAW", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SAW
            },
        )
        val hyper = renderInstrument(
            stableInstrument("HYPER", InstrumentType.HYPERSYNTH).apply {
                hyperSynth.swarm = 0x90
                hyperSynth.width = 0xFF
                hyperSynth.subOsc = 0x40
            },
        )

        assertNotEquals(saw.toList(), hyper.toList())
        assertTrue(peakChannel(hyper, 0) > 0)
        assertTrue(peakChannel(hyper, 1) > 0)
    }

    @Test
    fun `macrosynth model changes rendered output`() {
        val csaw = renderInstrument(
            stableInstrument("CSAW", InstrumentType.MACROSYNTH).apply {
                macroSynth.model = 0
                macroSynth.timbre = 0x40
                macroSynth.color = 0xC0
            },
        )
        val squareSub = renderInstrument(
            stableInstrument("SQUARE_SUB", InstrumentType.MACROSYNTH).apply {
                macroSynth.model = 5
                macroSynth.timbre = 0x40
                macroSynth.color = 0xC0
            },
        )

        assertNotEquals(csaw.toList(), squareSub.toList())
    }

    @Test
    fun `runtime pan override controls stereo balance without replacing instrument`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            stableInstrument("CENTER", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SINE
                amp.pan = 0x80
            },
        )
        synth.setRuntimeTrackPan(0, 0x00)
        val row = Array(8) { IntArray(3) }
        row[0][0] = 69
        row[0][2] = 255
        synth.triggerRow(row)
        val pcm = synth.generateChunk()

        val leftPeak = peakChannel(pcm, 0)
        val rightPeak = peakChannel(pcm, 1)

        assertTrue(leftPeak > rightPeak * 3, "expected runtime pan override to make output left-heavy, L=$leftPeak R=$rightPeak")
    }

    @Test
    fun `envelope modulation opens filter cutoff after note trigger`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            stableInstrument("ENV CUTOFF", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SAW
                filter.cutoff = 0x20
                modulation.env2 = Envelope(attack = 0x00, decay = 0x40, sustain = 0x00, release = 0x10, dest = ModDestination.CUTOFF, amount = 0xF0)
            },
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255

        synth.triggerRow(row)
        val opened = synth.debugModulatedCutoff(0, 0)
        synth.generateChunk()
        val decayed = synth.debugModulatedCutoff(0, 0)

        assertTrue(opened > 0.6, "envelope should push cutoff open at trigger, cutoff=$opened")
        assertTrue(decayed < opened, "envelope modulation should decay over time, opened=$opened decayed=$decayed")
    }

    @Test
    fun `lfo modulation moves pitch across chunks`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            stableInstrument("LFO PITCH", InstrumentType.WAVSYNTH).apply {
                modulation.lfo1 = Lfo(LfoShape.SINE, speed = 0xFF, amount = 0xF0, dest = ModDestination.PITCH)
            },
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255

        synth.triggerRow(row)
        val first = synth.debugModulatedFrequency(0, 0)
        repeat(4) { synth.generateChunk() }
        val later = synth.debugModulatedFrequency(0, 0)

        assertNotEquals(first, later)
        assertTrue(later > first, "fast sine LFO should bend pitch upward after several chunks, first=$first later=$later")
    }

    @Test
    fun `amp modulation changes generated level over time`() {
        val synth = M8Synth()
        synth.applyInstrument(
            0,
            stableInstrument("AMP LFO", InstrumentType.WAVSYNTH).apply {
                wavSynth.shape = WavShape.SINE
                modulation.lfo1 = Lfo(LfoShape.SINE, speed = 0xFF, amount = 0xF0, dest = ModDestination.AMP)
            },
        )
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255

        synth.triggerRow(row)
        val first = peakChannel(synth.generateChunk(), 0)
        repeat(4) { synth.generateChunk() }
        val later = peakChannel(synth.generateChunk(), 0)

        assertNotEquals(first, later)
    }

    private fun samplerInstrument() = M8Instrument("SAMPLE", InstrumentType.SAMPLER).apply {
        amp.amp = 0xFF
        amp.pan = 0x80
        amp.delaySend = 0x00
        filter.cutoff = 0xFF
        sampler.start = 0x00
        sampler.length = 0xFF
        sampler.detune = 0x80
        modulation.env1 = Envelope(attack = 0x00, decay = 0xFF, sustain = 0xFF, release = 0x40)
    }

    private fun renderInstrument(instrument: M8Instrument): ByteArray {
        val synth = M8Synth()
        val row = Array(8) { IntArray(3) }
        row[0][0] = 60
        row[0][2] = 255
        synth.applyInstrument(0, instrument)
        synth.triggerRow(row)
        return synth.generateChunk().copyOf()
    }

    private fun stableInstrument(name: String, type: InstrumentType) = M8Instrument(name, type).apply {
        amp.amp = 0xFF
        amp.pan = 0x80
        amp.delaySend = 0x00
        filter.cutoff = 0xFF
        filter.resonance = 0x00
        modulation.env1 = Envelope(attack = 0x00, decay = 0xFF, sustain = 0xFF, release = 0x40)
    }

    private fun peakChannel(pcm: ByteArray, channel: Int): Int {
        var peak = 0
        var i = channel * 2
        while (i + 1 < pcm.size) {
            val v = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            peak = maxOf(peak, kotlin.math.abs(v))
            i += 4
        }
        return peak
    }
}
