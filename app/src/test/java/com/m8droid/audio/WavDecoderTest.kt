package com.m8droid.audio

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavDecoderTest {
    @Test
    fun `decodes mono sixteen bit PCM into normalized samples`() {
        val wav = wavFile(
            channels = 1,
            sampleRate = 22_050,
            samples = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE),
        )

        val decoded = WavDecoder.decode(wav)

        assertEquals(1, decoded.channels)
        assertEquals(22_050, decoded.sampleRate)
        assertEquals(3, decoded.frameCount)
        assertEquals(-1.0f, decoded.samples[0], 0.0001f)
        assertEquals(0.0f, decoded.samples[1], 0.0001f)
        assertEquals(Short.MAX_VALUE / 32768f, decoded.samples[2], 0.0001f)
    }

    @Test
    fun `decodes stereo sixteen bit PCM preserving interleaved channel order`() {
        val wav = wavFile(
            channels = 2,
            sampleRate = 44_100,
            samples = shortArrayOf(1000, -1000, 2000, -2000),
        )

        val decoded = WavDecoder.decode(wav)

        assertEquals(2, decoded.channels)
        assertEquals(2, decoded.frameCount)
        assertArrayEquals(
            floatArrayOf(1000 / 32768f, -1000 / 32768f, 2000 / 32768f, -2000 / 32768f),
            decoded.samples,
            0.0001f,
        )
    }

    @Test
    fun `rejects compressed wav formats`() {
        val wav = wavFile(channels = 1, sampleRate = 44_100, samples = shortArrayOf(1), audioFormat = 3)

        assertThrows(IllegalArgumentException::class.java) {
            WavDecoder.decode(wav)
        }
    }

    private fun wavFile(
        channels: Int,
        sampleRate: Int,
        samples: ShortArray,
        audioFormat: Short = 1,
    ): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream()
        out.writeAscii("RIFF")
        out.writeIntLE(36 + dataBytes)
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeIntLE(16)
        out.writeShortLE(audioFormat)
        out.writeShortLE(channels.toShort())
        out.writeIntLE(sampleRate)
        out.writeIntLE(sampleRate * channels * 2)
        out.writeShortLE((channels * 2).toShort())
        out.writeShortLE(16)
        out.writeAscii("data")
        out.writeIntLE(dataBytes)
        samples.forEach { out.writeShortLE(it) }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
    private fun ByteArrayOutputStream.writeIntLE(v: Int) = write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    private fun ByteArrayOutputStream.writeShortLE(v: Short) = write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array())
}
