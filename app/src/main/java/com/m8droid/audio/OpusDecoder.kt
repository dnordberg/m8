package com.m8droid.audio

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

/**
 * Opus decoder using Android's built-in MediaCodec.
 *
 * Attempts to use hardware/software Opus decoder via MediaCodec.
 * The audio server can also stream raw PCM as a fallback, in which
 * case this decoder is bypassed entirely.
 *
 * Input: Opus-encoded byte arrays
 * Output: PCM 16-bit samples at 48000Hz stereo
 */
class OpusDecoder {

    companion object {
        private const val TAG = "OpusDecoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS
        private const val SAMPLE_RATE = M8AudioPlayer.SAMPLE_RATE
        private const val CHANNELS = M8AudioPlayer.CHANNELS
        // Timeout for codec operations in microseconds
        private const val CODEC_TIMEOUT_US = 10_000L
    }

    private var codec: MediaCodec? = null
    @Volatile
    var isInitialized = false
        private set

    /**
     * Check if the device supports Opus decoding via MediaCodec.
     */
    fun isOpusSupported(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE, ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Opus support: ${e.message}")
            false
        }
    }

    /**
     * Initialize the Opus MediaCodec decoder.
     * @param codecSpecificData0 Opus identification header (CSD-0), or null for default
     * @param codecSpecificData1 Opus pre-skip header (CSD-1), or null for default
     * @param codecSpecificData2 Opus seek pre-roll header (CSD-2), or null for default
     * @return true if initialization succeeded
     */
    fun initialize(
        codecSpecificData0: ByteArray? = null,
        codecSpecificData1: ByteArray? = null,
        codecSpecificData2: ByteArray? = null
    ): Boolean {
        try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNELS)

            // Opus requires CSD (Codec Specific Data) buffers
            // CSD-0: Opus identification header
            // CSD-1: Pre-skip in nanoseconds (usually 0)
            // CSD-2: Seek pre-roll in nanoseconds (usually 80000000 = 80ms)
            val csd0 = codecSpecificData0 ?: createDefaultOpusHeader()
            val csd1 = codecSpecificData1 ?: createPreSkipBuffer(0)
            val csd2 = codecSpecificData2 ?: createPreRollBuffer(80_000_000L)

            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(csd2))

            codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec?.configure(format, null, null, 0)
            codec?.start()
            isInitialized = true
            Log.i(TAG, "Opus decoder initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Opus decoder: ${e.message}", e)
            release()
            return false
        }
    }

    /**
     * Decode an Opus packet to PCM.
     * @param opusData Encoded Opus frame
     * @return Decoded PCM data, or null on failure
     */
    fun decode(opusData: ByteArray): ByteArray? {
        val codec = codec ?: return null
        if (!isInitialized) return null

        try {
            // Queue input buffer
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                Log.w(TAG, "No input buffer available")
                return null
            }

            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return null
            inputBuffer.clear()
            inputBuffer.put(opusData)
            codec.queueInputBuffer(inputIndex, 0, opusData.size, 0, 0)

            // Dequeue output buffer
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: return null
                val pcmData = ByteArray(bufferInfo.size)
                outputBuffer.get(pcmData)
                codec.releaseOutputBuffer(outputIndex, false)
                return pcmData
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                Log.i(TAG, "Output format changed: $newFormat")
                // Try again to get actual output
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Opus: ${e.message}")
        }

        return null
    }

    /**
     * Release decoder resources.
     */
    fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing codec: ${e.message}")
        }
        codec = null
        isInitialized = false
    }

    /**
     * Create a minimal Opus identification header.
     * See RFC 7845, Section 5.1
     */
    private fun createDefaultOpusHeader(): ByteArray {
        val header = ByteArray(19)
        // Magic signature: "OpusHead"
        "OpusHead".toByteArray().copyInto(header, 0)
        // Version (1)
        header[8] = 1
        // Channel count
        header[9] = CHANNELS.toByte()
        // Pre-skip (little-endian, 312 samples is typical)
        header[10] = 0x38
        header[11] = 0x01
        // Input sample rate (little-endian, 48000)
        header[12] = 0x80.toByte()
        header[13] = 0xBB.toByte()
        header[14] = 0x00
        header[15] = 0x00
        // Output gain (0)
        header[16] = 0x00
        header[17] = 0x00
        // Channel mapping family (0 = single stream)
        header[18] = 0x00
        return header
    }

    /**
     * Create pre-skip buffer (CSD-1): 64-bit nanosecond value, little-endian.
     */
    private fun createPreSkipBuffer(preSkipNs: Long): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putLong(preSkipNs)
        return buf.array()
    }

    /**
     * Create seek pre-roll buffer (CSD-2): 64-bit nanosecond value, little-endian.
     */
    private fun createPreRollBuffer(preRollNs: Long): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putLong(preRollNs)
        return buf.array()
    }
}
