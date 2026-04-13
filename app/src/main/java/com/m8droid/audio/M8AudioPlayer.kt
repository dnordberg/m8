package com.m8droid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Audio output via AudioTrack in MODE_STREAM.
 *
 * No queue, no drain thread. The synth thread calls write() directly.
 * AudioTrack.write() blocks when the internal buffer is full — this IS
 * the backpressure that paces the synth to real-time. Every working
 * Android synth uses this pattern.
 */
class M8AudioPlayer {

    companion object {
        private const val TAG = "M8AudioPlayer"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val FRAME_SIZE = CHANNELS * BYTES_PER_SAMPLE
    }

    private var audioTrack: AudioTrack? = null

    @Volatile
    var isPlaying = false
        private set

    fun start() {
        if (isPlaying) return

        try {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBuf <= 0) {
                Log.e(TAG, "Invalid min buffer size: $minBuf")
                return
            }

            // Use minBuf or ~100ms, whichever is larger
            val bufferSize = maxOf(minBuf, SAMPLE_RATE * FRAME_SIZE / 10)

            Log.i(TAG, "Creating AudioTrack: rate=$SAMPLE_RATE, minBuf=$minBuf, buf=$bufferSize")

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized, state=${track.state}")
                track.release()
                return
            }

            track.play()
            audioTrack = track
            isPlaying = true
            Log.i(TAG, "AudioTrack playing (buf=${bufferSize}B)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start: ${e.message}", e)
            release()
        }
    }

    /**
     * Write PCM data directly to AudioTrack. This BLOCKS when the
     * internal buffer is full, which paces the caller to real-time.
     * Call this from a dedicated audio thread — never from the UI thread.
     */
    fun write(pcmData: ByteArray, offset: Int = 0, size: Int = pcmData.size) {
        if (!isPlaying) return
        val track = audioTrack ?: return
        val written = track.write(pcmData, offset, size)
        if (written < 0) {
            Log.e(TAG, "write error: $written")
        }
    }

    fun stop() {
        isPlaying = false
        try { audioTrack?.stop() } catch (_: Exception) {}
        release()
        Log.i(TAG, "Stopped")
    }

    fun pause() {
        try { audioTrack?.pause(); isPlaying = false } catch (_: Exception) {}
    }

    fun resume() {
        try { audioTrack?.play(); isPlaying = true } catch (_: Exception) {}
    }

    private fun release() {
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        isPlaying = false
    }
}
