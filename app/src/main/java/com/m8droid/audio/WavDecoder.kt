package com.m8droid.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tiny PCM WAV decoder for the Android sampler path.
 *
 * Scope is deliberately narrow and testable: RIFF/WAVE, PCM format, 16-bit
 * little-endian samples, mono or stereo. The synth layer consumes normalized
 * floats in interleaved channel order.
 */
object WavDecoder {
    data class DecodedWav(
        val sampleRate: Int,
        val channels: Int,
        val samples: FloatArray,
    ) {
        val frameCount: Int get() = samples.size / channels
    }

    fun decode(bytes: ByteArray): DecodedWav {
        require(bytes.size >= 44) { "WAV file too small" }
        require(bytes.ascii(0, 4) == "RIFF") { "Missing RIFF header" }
        require(bytes.ascii(8, 4) == "WAVE") { "Missing WAVE header" }

        var offset = 12
        var audioFormat = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataSize = -1

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.ascii(offset, 4)
            val chunkSize = bytes.intLE(offset + 4)
            val chunkData = offset + 8
            require(chunkSize >= 0 && chunkData + chunkSize <= bytes.size) { "Invalid WAV chunk: $chunkId" }

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid fmt chunk" }
                    audioFormat = bytes.shortLE(chunkData).toInt() and 0xFFFF
                    channels = bytes.shortLE(chunkData + 2).toInt() and 0xFFFF
                    sampleRate = bytes.intLE(chunkData + 4)
                    bitsPerSample = bytes.shortLE(chunkData + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    dataOffset = chunkData
                    dataSize = chunkSize
                }
            }

            offset = chunkData + chunkSize + (chunkSize and 1)
        }

        require(audioFormat == 1) { "Only PCM WAV is supported (format=$audioFormat)" }
        require(channels == 1 || channels == 2) { "Only mono/stereo WAV is supported (channels=$channels)" }
        require(sampleRate > 0) { "Invalid sample rate" }
        require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported (bits=$bitsPerSample)" }
        require(dataOffset >= 0 && dataSize >= 0) { "Missing data chunk" }

        val sampleCount = dataSize / 2
        val out = FloatArray(sampleCount)
        var p = dataOffset
        for (i in 0 until sampleCount) {
            out[i] = bytes.shortLE(p) / 32768f
            p += 2
        }
        return DecodedWav(sampleRate, channels, out)
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.shortLE(offset: Int): Short =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

    private fun ByteArray.intLE(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
