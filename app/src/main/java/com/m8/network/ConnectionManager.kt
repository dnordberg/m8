package com.m8.network

import android.graphics.Bitmap
import android.util.Log
import com.m8.audio.AudioState
import com.m8.audio.M8AudioClient
import com.m8.protocol.M8Commands
import com.m8.protocol.M8DisplayBuffer
import com.m8.protocol.M8Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Manages the connection to the M8 bridge and coordinates
 * protocol parsing with display rendering and audio playback.
 */
class ConnectionManager(
    val display: M8DisplayBuffer = M8DisplayBuffer(),
) {
    constructor(fontBitmap: Bitmap?) : this(M8DisplayBuffer(fontBitmap))

    companion object {
        private const val TAG = "ConnectionManager"
        const val DEFAULT_DISPLAY_PORT = 8765
        const val DEFAULT_AUDIO_PORT = 8766
    }

    val protocol = M8Protocol(display)

    /** Tracks whether the bridge has an active serial connection to the M8 device. */
    private val _serialConnected = MutableStateFlow(false)
    val serialConnected: StateFlow<Boolean> = _serialConnected.asStateFlow()

    val wsClient = M8WebSocketClient(
        onBinaryMessage = { data ->
            protocol.processBytes(data)
        },
        onControlMessage = { message ->
            handleControlMessage(message)
        }
    )

    val audioClient = M8AudioClient()

    val connectionState: StateFlow<ConnectionState>
        get() = wsClient.state

    val audioState: StateFlow<AudioState>
        get() = audioClient.state

    val audioLevel: StateFlow<Float>
        get() = audioClient.audioLevel

    fun connect(host: String, port: Int, scope: CoroutineScope) {
        val url = "ws://$host:$port"
        wsClient.connect(url, scope)

        // Connect audio stream on the audio port
        audioClient.connect(host, DEFAULT_AUDIO_PORT, scope)
    }

    fun disconnect() {
        wsClient.send(M8Commands.disconnect())
        wsClient.disconnect()
        audioClient.disconnect()
        _serialConnected.value = false
    }

    fun enableDisplay() {
        wsClient.send(M8Commands.enableAndResetDisplay())
    }

    fun sendKeyState(keys: Int) {
        wsClient.send(M8Commands.keyState(keys))
    }

    fun setAudioMuted(muted: Boolean) {
        audioClient.setMuted(muted)
    }

    /**
     * Parses JSON control messages from bridge.py and updates state.
     * Expected messages:
     *   {"event": "serial_connected"}
     *   {"event": "serial_disconnected"}
     */
    private fun handleControlMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("event")) {
                "serial_connected" -> {
                    Log.i(TAG, "M8 serial device connected")
                    _serialConnected.value = true
                }
                "serial_disconnected" -> {
                    Log.i(TAG, "M8 serial device disconnected")
                    _serialConnected.value = false
                    display.clear()
                }
                else -> {
                    Log.w(TAG, "Unknown control message: $message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse control message: $message", e)
        }
    }
}
