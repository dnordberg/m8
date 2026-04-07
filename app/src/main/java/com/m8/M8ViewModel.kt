package com.m8

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8.audio.AudioState
import com.m8.data.ServerConfig
import com.m8.data.ServerSettings
import com.m8.network.ConnectionManager
import com.m8.network.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    private var displayRefreshJob: kotlinx.coroutines.Job? = null

    fun connect(settings: ServerSettings) {
        connectionManager.connect(settings.host, settings.port, viewModelScope)

        // Wait for connection, then enable display
        viewModelScope.launch {
            connectionManager.connectionState.first { it == ConnectionState.CONNECTED }
            delay(100) // Brief delay for connection to stabilize
            connectionManager.enableDisplay()
            startDisplayRefresh()
        }
    }

    fun disconnect() {
        stopDisplayRefresh()
        connectionManager.disconnect()
    }

    fun sendKeyState(keys: Int) {
        connectionManager.sendKeyState(keys)
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
        disconnect()
    }
}
