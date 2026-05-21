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
    fun `track indices outside eight voices are ignored`() {
        val synth = M8Synth()
        synth.applyInstrument(99, M8Instrument("IGNORED"))
        assertEquals(0.0, synth.getVoiceFreq(99))
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
