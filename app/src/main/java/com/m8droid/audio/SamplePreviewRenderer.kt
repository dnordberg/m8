package com.m8droid.audio

import kotlin.math.roundToInt

object SamplePreviewRenderer {
    fun render(sample: WavDecoder.DecodedWav, maxSeconds: Double = 4.0): ByteArray {
        val maxFrames = (sample.sampleRate * maxSeconds).roundToInt().coerceAtLeast(1)
        val frames = sample.frameCount.coerceAtMost(maxFrames)
        val out = ByteArray(frames * 4) // stereo 16-bit LE
        var outIdx = 0
        for (frame in 0 until frames) {
            val left: Float
            val right: Float
            if (sample.channels == 1) {
                val v = sample.samples.getOrElse(frame) { 0f }
                left = v
                right = v
            } else {
                val base = frame * sample.channels
                left = sample.samples.getOrElse(base) { 0f }
                right = sample.samples.getOrElse(base + 1) { left }
            }
            outIdx = out.writeShortLE(outIdx, floatToShort(left))
            outIdx = out.writeShortLE(outIdx, floatToShort(right))
        }
        return out
    }

    private fun floatToShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

    private fun ByteArray.writeShortLE(index: Int, value: Short): Int {
        this[index] = (value.toInt() and 0xFF).toByte()
        this[index + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        return index + 2
    }
}
