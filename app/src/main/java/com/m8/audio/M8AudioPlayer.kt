// app/src/main/java/com/m8/audio/M8AudioPlayer.kt
package com.m8.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Audio playback engine using a dedicated high-priority drain thread
 * and a lock-free producer-consumer queue.
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

        private const val QUEUE_CAPACITY = 8  // Small queue: synth thread blocks when full, keeping it paced to real-time
        private const val POLL_TIMEOUT_MS = 100L
    }

    private var audioTrack: AudioTrack? = null
    private var drainThread: Thread? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)

    @Volatile
    var isPlaying = false
        private set

    fun start() {
        if (isPlaying) return

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            if (minBufferSize <= 0) {
                Log.e(TAG, "Invalid min buffer size: $minBufferSize")
                return
            }

            // Use ~200ms buffer for scheduling jitter tolerance
            val desiredBuffer = SAMPLE_RATE * FRAME_SIZE * 200 / 1000
            val bufferSize = maxOf(minBufferSize, desiredBuffer)

            Log.i(TAG, "Creating AudioTrack: rate=$SAMPLE_RATE, minBuf=$minBufferSize, buf=$bufferSize")

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialize! State: ${track.state}")
                track.release()
                return
            }

            // Pre-fill with silence so the track has data before play()
            val silenceSize = minOf(bufferSize / 2, SAMPLE_RATE * FRAME_SIZE * 50 / 1000)
            val silence = ByteArray(silenceSize)
            track.write(silence, 0, silence.size)

            track.play()

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.e(TAG, "AudioTrack failed to enter PLAYING state: ${track.playState}")
                track.release()
                return
            }

            audioTrack = track
            isPlaying = true
            audioQueue.clear()

            startDrainThread()

            Log.i(TAG, "AudioTrack started successfully (buffer=${bufferSize}B)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}", e)
            release()
        }
    }

    /**
     * Write a PCM chunk for playback. BLOCKS if the queue is full,
     * which naturally paces the synth thread to real-time playback rate.
     * This is critical for timing accuracy — without backpressure the
     * synth runs faster than real-time and the sequencer drifts.
     */
    fun write(pcmData: ByteArray, offset: Int = 0, size: Int = pcmData.size) {
        if (!isPlaying) return

        val chunk = if (offset == 0 && size == pcmData.size) {
            pcmData
        } else {
            pcmData.copyOfRange(offset, offset + size)
        }

        try {
            audioQueue.put(chunk) // Blocks when queue is full — this is the timing mechanism
        } catch (_: InterruptedException) {
            // Thread is shutting down
        }
    }

    fun stop() {
        isPlaying = false
        // Clear the queue first — this unblocks any thread stuck on put()
        audioQueue.clear()
        stopDrainThread()

        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
        }

        release()
        Log.i(TAG, "AudioTrack stopped")
    }

    fun pause() {
        try {
            audioTrack?.pause()
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    fun resume() {
        try {
            audioTrack?.play()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming AudioTrack: ${e.message}")
        }
    }

    private fun startDrainThread() {
        drainThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.i(TAG, "Audio drain thread started")

            val track = audioTrack ?: return@Thread
            var chunksWritten = 0L

            while (isPlaying) {
                try {
                    val chunk = audioQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        val written = track.write(chunk, 0, chunk.size)
                        if (written < 0) {
                            Log.e(TAG, "AudioTrack.write error: $written (DEAD_OBJECT=-6, INVALID=-3)")
                            if (written == AudioTrack.ERROR_DEAD_OBJECT) {
                                Log.e(TAG, "AudioTrack died, stopping")
                                break
                            }
                        } else {
                            chunksWritten++
                            if (chunksWritten % 500 == 0L) {
                                Log.d(TAG, "Drain: $chunksWritten chunks written, queue=${audioQueue.size}")
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Drain thread error: ${e.message}")
                }
            }

            Log.i(TAG, "Audio drain thread exiting after $chunksWritten chunks")
        }, "M8AudioDrain").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopDrainThread() {
        drainThread?.let { thread ->
            thread.interrupt()
            try { thread.join(500) } catch (_: InterruptedException) {}
        }
        drainThread = null
        audioQueue.clear()
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
