package com.m8.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Low-latency audio playback using Android AudioTrack.
 * Configured for 44100Hz, 16-bit, stereo PCM.
 */
class M8AudioPlayer {

    companion object {
        private const val TAG = "M8AudioPlayer"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2 // 16-bit
        const val FRAME_SIZE = CHANNELS * BYTES_PER_SAMPLE // 4 bytes per stereo frame
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    var isPlaying = false
        private set

    /**
     * Initialize and start the AudioTrack for playback.
     */
    fun start() {
        if (isPlaying) return

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            // Use a small buffer for low latency but at least the minimum
            // Aim for ~40ms of buffer: 48000 * 4 bytes * 0.040 = 7680 bytes
            val desiredBuffer = (SAMPLE_RATE * FRAME_SIZE * 0.040).toInt()
            val bufferSize = maxOf(minBufferSize, desiredBuffer)

            Log.i(TAG, "Creating AudioTrack: rate=$SAMPLE_RATE, minBuf=$minBufferSize, buf=$bufferSize")

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            audioTrack?.play()
            isPlaying = true
            Log.i(TAG, "AudioTrack started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}", e)
            release()
        }
    }

    /**
     * Write PCM audio data to the AudioTrack.
     * @param pcmData Raw PCM 16-bit stereo samples
     * @param offset Start offset in the array
     * @param size Number of bytes to write
     */
    fun write(pcmData: ByteArray, offset: Int = 0, size: Int = pcmData.size) {
        val track = audioTrack ?: return
        if (!isPlaying) return

        try {
            val written = track.write(pcmData, offset, size)
            if (written < 0) {
                Log.w(TAG, "AudioTrack write error: $written")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to AudioTrack: ${e.message}")
        }
    }

    /**
     * Pause audio playback.
     */
    fun pause() {
        try {
            audioTrack?.pause()
            isPlaying = false
            Log.i(TAG, "AudioTrack paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    /**
     * Resume audio playback after pause.
     */
    fun resume() {
        try {
            audioTrack?.play()
            isPlaying = true
            Log.i(TAG, "AudioTrack resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming AudioTrack: ${e.message}")
        }
    }

    /**
     * Stop and release the AudioTrack.
     */
    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            Log.i(TAG, "AudioTrack stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        release()
    }

    private fun release() {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
        isPlaying = false
    }
}
