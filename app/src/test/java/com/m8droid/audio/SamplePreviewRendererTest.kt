package com.m8droid.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SamplePreviewRendererTest {
    @Test
    fun `renders mono sample preview as stereo sixteen bit pcm`() {
        val sample = WavDecoder.DecodedWav(
            sampleRate = 44_100,
            channels = 1,
            samples = floatArrayOf(-1.0f, 0.0f, 0.5f, 1.0f),
        )

        val pcm = SamplePreviewRenderer.render(sample, maxSeconds = 1.0)

        assertEquals(4 * 4, pcm.size)
        assertEquals(-32767, pcm.shortAt(0))
        assertEquals(-32767, pcm.shortAt(2))
        assertEquals(0, pcm.shortAt(4))
        assertEquals(0, pcm.shortAt(6))
        assertTrue(pcm.shortAt(8) > 16_000)
        assertTrue(pcm.shortAt(12) > 32_000 - 100)
    }

    @Test
    fun `limits rendered preview duration`() {
        val samples = FloatArray(88_200) { 0.25f }
        val sample = WavDecoder.DecodedWav(
            sampleRate = 44_100,
            channels = 1,
            samples = samples,
        )

        val pcm = SamplePreviewRenderer.render(sample, maxSeconds = 0.5)

        assertEquals(22_050 * 4, pcm.size)
    }

    private fun ByteArray.shortAt(index: Int): Int {
        val lo = this[index].toInt() and 0xFF
        val hi = this[index + 1].toInt()
        return (hi shl 8) or lo
    }
}
