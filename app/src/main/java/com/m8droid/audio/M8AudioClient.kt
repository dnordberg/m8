package com.m8droid.audio

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString

/**
 * Audio stream state.
 */
enum class AudioState {
    STOPPED, CONNECTING, STREAMING, RECONNECTING, ERROR
}

/**
 * WebSocket client for the M8 audio stream.
 *
 * Connects to the audio bridge server on port 8766 and receives
 * either raw PCM or Opus-encoded audio frames. Decoded PCM is
 * written to an M8AudioPlayer for low-latency playback.
 *
 * Supports reconnection with exponential backoff, matching the
 * pattern in M8WebSocketClient.
 */
class M8AudioClient {

    companion object {
        private const val TAG = "M8AudioClient"
        private const val RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        // Protocol: first byte of each binary message indicates format
        // 0x01 = raw PCM, 0x02 = Opus encoded
        // If no header byte, treat entire message as raw PCM
        const val FORMAT_RAW_PCM: Byte = 0x01
        const val FORMAT_OPUS: Byte = 0x02
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(20))
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var reconnectAttempts = 0
    private var shouldReconnect = false
    private var scope: CoroutineScope? = null

    val player = M8AudioPlayer()
    private val opusDecoder = OpusDecoder()
    private var useOpus = false

    private val _state = MutableStateFlow(AudioState.STOPPED)
    val state: StateFlow<AudioState> = _state

    // Track volume level for UI meters (0.0 - 1.0)
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    /**
     * Connect to the audio stream server and start playback.
     * @param host Server hostname/IP
     * @param port Audio server port (default 8766)
     * @param coroutineScope Scope for reconnection coroutines
     */
    fun connect(host: String, port: Int = 8766, coroutineScope: CoroutineScope) {
        serverUrl = "ws://$host:$port"
        scope = coroutineScope
        shouldReconnect = true
        reconnectAttempts = 0

        // Try to initialize Opus decoder
        useOpus = opusDecoder.isOpusSupported() && opusDecoder.initialize()
        if (useOpus) {
            Log.i(TAG, "Opus decoder available, will decode Opus frames")
        } else {
            Log.i(TAG, "Opus decoder not available, expecting raw PCM from server")
        }

        player.start()
        doConnect()
    }

    /**
     * Disconnect from the audio server and stop playback.
     */
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        player.stop()
        opusDecoder.release()
        useOpus = false
        _state.value = AudioState.STOPPED
        _audioLevel.value = 0f
    }

    /**
     * Mute/unmute audio (pause/resume playback without disconnecting).
     */
    fun setMuted(muted: Boolean) {
        if (muted) {
            player.pause()
        } else {
            player.resume()
        }
    }

    private fun doConnect() {
        _state.value = if (reconnectAttempts > 0) AudioState.RECONNECTING else AudioState.CONNECTING
        Log.i(TAG, "Connecting to $serverUrl (attempt ${reconnectAttempts + 1})")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Audio stream connected to $serverUrl")
                reconnectAttempts = 0
                _state.value = AudioState.STREAMING

                // Ensure player is started
                if (!player.isPlaying) {
                    player.start()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleAudioData(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Control messages from audio bridge (e.g., format info)
                Log.d(TAG, "Audio control message: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Audio stream closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Audio stream closed: $code $reason")
                _state.value = AudioState.STOPPED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Audio stream failed: ${t.message}")
                _state.value = AudioState.ERROR
                scheduleReconnect()
            }
        })
    }

    /**
     * Handle incoming audio data from WebSocket.
     * Data may be raw PCM or Opus-encoded with a format header byte.
     */
    private fun handleAudioData(data: ByteArray) {
        if (data.isEmpty()) return

        // Check for format header byte
        when {
            data.size > 1 && data[0] == FORMAT_OPUS && useOpus -> {
                // Opus encoded: skip header byte, decode
                val opusFrame = data.copyOfRange(1, data.size)
                val pcm = opusDecoder.decode(opusFrame)
                if (pcm != null) {
                    player.write(pcm)
                    updateAudioLevel(pcm)
                }
            }
            data.size > 1 && data[0] == FORMAT_RAW_PCM -> {
                // Raw PCM with header: skip header byte
                val pcm = data.copyOfRange(1, data.size)
                player.write(pcm)
                updateAudioLevel(pcm)
            }
            else -> {
                // No header or unrecognized: treat entire message as raw PCM
                player.write(data)
                updateAudioLevel(data)
            }
        }
    }

    /**
     * Compute a simple RMS audio level for UI display.
     */
    private fun updateAudioLevel(pcm: ByteArray) {
        if (pcm.size < 4) return

        // Sample a subset for performance (every 64th sample)
        var sumSquares = 0L
        var count = 0
        var i = 0
        while (i < pcm.size - 1) {
            // 16-bit little-endian sample
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val signed = if (sample > 32767) sample - 65536 else sample
            sumSquares += signed.toLong() * signed.toLong()
            count++
            i += 64 // Skip samples for performance
        }

        if (count > 0) {
            val rms = Math.sqrt(sumSquares.toDouble() / count)
            // Normalize to 0.0 - 1.0 range (32768 = max for 16-bit)
            _audioLevel.value = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        val delay = (RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempts, 4)))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++

        Log.i(TAG, "Audio reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        scope?.launch {
            delay(delay)
            if (shouldReconnect) {
                doConnect()
            }
        }
    }
}
