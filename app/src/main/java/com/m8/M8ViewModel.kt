package com.m8

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8.audio.AudioState
import com.m8.data.ServerConfig
import com.m8.data.ServerSettings
import com.m8.emulator.M8Emulator
import com.m8.network.ConnectionManager
import com.m8.network.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Supports two modes:
 * - Remote: connects to bridge.py/m8_emulator.py over WebSocket
 * - Local: runs M8Emulator in-process, zero network needed
 */
class M8ViewModel(application: Application) : AndroidViewModel(application) {

    private val serverConfig = ServerConfig(application)
    private val fontBitmap = try {
        application.assets.open("m8_font.png").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
    val connectionManager = ConnectionManager(fontBitmap)

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val audioState: StateFlow<AudioState> = connectionManager.audioState
    val audioLevel: StateFlow<Float> = connectionManager.audioLevel
    val settings: Flow<ServerSettings> = serverConfig.settings

    // Tick counter to trigger display recomposition (~30fps)
    private val _displayTick = MutableStateFlow(0)
    val displayTick: StateFlow<Int> = _displayTick

    // Audio mute state
    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted

    // Local emulator mode
    private val _isLocalMode = MutableStateFlow(true) // Default to local
    val isLocalMode: StateFlow<Boolean> = _isLocalMode

    private val emulator = M8Emulator()
    private var displayRefreshJob: Job? = null
    private var emulatorRenderJob: Job? = null

    // --- Remote mode ---

    fun connect(settings: ServerSettings) {
        _isLocalMode.value = false
        stopEmulator()
        connectionManager.connect(settings.host, settings.port, viewModelScope)

        viewModelScope.launch {
            connectionManager.connectionState.first { it == ConnectionState.CONNECTED }
            delay(100)
            connectionManager.enableDisplay()
            startDisplayRefresh()
        }
    }

    fun disconnect() {
        stopDisplayRefresh()
        stopEmulator()
        connectionManager.disconnect()
    }

    // --- Local emulator mode ---

    fun startLocalEmulator() {
        _isLocalMode.value = true
        stopDisplayRefresh()

        emulatorRenderJob?.cancel()
        emulatorRenderJob = viewModelScope.launch {
            // Feed system info first so the protocol knows we're "connected"
            while (true) {
                val frameData = emulator.renderFrame()
                // Feed directly into the protocol parser
                connectionManager.protocol.processBytes(frameData)
                _displayTick.value++
                delay(33) // ~30fps
            }
        }
    }

    fun stopEmulator() {
        emulatorRenderJob?.cancel()
        emulatorRenderJob = null
    }

    // --- Shared ---

    fun sendKeyState(keys: Int) {
        if (_isLocalMode.value) {
            emulator.handleKeyState(keys)
        } else {
            connectionManager.sendKeyState(keys)
        }
    }

    fun toggleLocalMode() {
        if (_isLocalMode.value) {
            // Switch to remote — stop emulator, try connecting
            stopEmulator()
            viewModelScope.launch {
                val s = settings.first()
                connect(s)
            }
        } else {
            // Switch to local — disconnect remote, start emulator
            disconnect()
            startLocalEmulator()
        }
    }

    fun toggleAudioMute() {
        val newMuted = !_isAudioMuted.value
        _isAudioMuted.value = newMuted
        connectionManager.setAudioMuted(newMuted)
    }

    fun saveSettings(settings: ServerSettings) {
        viewModelScope.launch {
            serverConfig.save(settings)
        }
    }

    private fun startDisplayRefresh() {
        stopDisplayRefresh()
        displayRefreshJob = viewModelScope.launch {
            while (true) {
                delay(33) // ~30fps
                _displayTick.value++
            }
        }
    }

    private fun stopDisplayRefresh() {
        displayRefreshJob?.cancel()
        displayRefreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulator()
        disconnect()
    }
}
