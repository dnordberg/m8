package com.m8.network

import com.m8.protocol.M8Commands
import com.m8.protocol.M8DisplayBuffer
import com.m8.protocol.M8Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the connection to the M8 bridge and coordinates
 * protocol parsing with display rendering.
 */
class ConnectionManager(
    val display: M8DisplayBuffer = M8DisplayBuffer(),
) {
    val protocol = M8Protocol(display)

    val wsClient = M8WebSocketClient(
        onBinaryMessage = { data ->
            protocol.processBytes(data)
        },
        onControlMessage = { message ->
            // Handle bridge control messages (serial_connected, serial_disconnected)
        }
    )

    val connectionState: StateFlow<ConnectionState>
        get() = wsClient.state

    fun connect(host: String, port: Int, scope: CoroutineScope) {
        val url = "ws://$host:$port"
        wsClient.connect(url, scope)
    }

    fun disconnect() {
        wsClient.send(M8Commands.disconnect())
        wsClient.disconnect()
    }

    fun enableDisplay() {
        wsClient.send(M8Commands.enableAndResetDisplay())
    }

    fun sendKeyState(keys: Int) {
        wsClient.send(M8Commands.keyState(keys))
    }
}
