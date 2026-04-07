package com.m8.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

/**
 * WebSocket client for the M8 serial bridge.
 * Handles connection lifecycle, reconnection, and message routing.
 */
class M8WebSocketClient(
    private val onBinaryMessage: (ByteArray) -> Unit,
    private val onControlMessage: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "M8WebSocket"
        private const val RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(20))
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var reconnectAttempts = 0
    private var shouldReconnect = false
    private var scope: CoroutineScope? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    fun connect(url: String, coroutineScope: CoroutineScope) {
        serverUrl = url
        scope = coroutineScope
        shouldReconnect = true
        reconnectAttempts = 0
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    fun send(data: ByteArray) {
        webSocket?.send(ByteString.of(*data))
    }

    private fun doConnect() {
        _state.value = if (reconnectAttempts > 0) ConnectionState.RECONNECTING else ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to $serverUrl (attempt ${reconnectAttempts + 1})")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $serverUrl")
                reconnectAttempts = 0
                _state.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onBinaryMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Control messages from bridge (JSON)
                onControlMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $code $reason")
                _state.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}")
                _state.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        val delay = (RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempts, 4)))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++

        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        scope?.launch {
            delay(delay)
            if (shouldReconnect) {
                doConnect()
            }
        }
    }
}
