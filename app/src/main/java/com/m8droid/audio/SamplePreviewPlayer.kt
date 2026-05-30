package com.m8droid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.roundToInt

/** Short one-shot player for WAV previews from the SD browser. */
class SamplePreviewPlayer {
    companion object {
        private const val TAG = "SamplePreviewPlayer"
    }

    @Volatile private var worker: Thread? = null
    @Volatile private var audioTrack: AudioTrack? = null

    fun play(sample: WavDecoder.DecodedWav, maxSeconds: Double = 4.0) {
        stop()
        val pcm = SamplePreviewRenderer.render(sample, maxSeconds)
        worker = Thread({
            runCatching {
                val frameCount = pcm.size / 4
                val minBuf = AudioTrack.getMinBufferSize(
                    sample.sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                val bufferBytes = maxOf(minBuf, 2048, (sample.sampleRate * 0.05).roundToInt() * 4)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sample.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                track.play()
                var offset = 0
                while (offset < pcm.size && !Thread.currentThread().isInterrupted) {
                    val written = track.write(pcm, offset, pcm.size - offset)
                    if (written <= 0) break
                    offset += written
                }
                if (!Thread.currentThread().isInterrupted) {
                    val ms = (frameCount * 1000L / sample.sampleRate).coerceAtMost(4500L)
                    Thread.sleep(ms)
                }
            }.onFailure { Log.w(TAG, "preview failed: ${it.message}", it) }
            releaseTrack()
        }, "M8SamplePreview").apply { start() }
    }

    fun stop() {
        worker?.interrupt()
        worker = null
        releaseTrack()
    }

    private fun releaseTrack() {
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }
}
