package com.m8droid.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class SampleCacheTest {
    @Test
    fun `loads wav from virtual sd sample path and caches decoded result`() {
        val root = Files.createTempDirectory("m8sd").toFile()
        val sampleDir = root.resolve("Samples/Kicks").apply { mkdirs() }
        sampleDir.resolve("punch.wav").writeBytes(
            wavFile(channels = 1, sampleRate = 22_050, samples = shortArrayOf(1000, -1000, 2000, -2000)),
        )
        val cache = SampleCache(root)

        val first = cache.load("/Samples/Kicks/punch.wav")
        val second = cache.load("Samples/Kicks/punch.wav")

        assertEquals(22_050, first?.sampleRate)
        assertEquals(4, first?.frameCount)
        assertSame(first, second, "same decoded WAV instance should be reused from cache")
    }

    @Test
    fun `refuses virtual sd paths that escape the sd root`() {
        val root = Files.createTempDirectory("m8sd").toFile()
        val cache = SampleCache(root)

        val loaded = cache.load("/Samples/../../secret.wav")

        assertTrue(loaded == null)
    }

    private fun wavFile(
        channels: Int,
        sampleRate: Int,
        samples: ShortArray,
    ): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream()
        out.writeAscii("RIFF")
        out.writeIntLE(36 + dataBytes)
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeIntLE(16)
        out.writeShortLE(1)
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
