package com.m8droid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "m8_settings")

enum class ButtonLayout {
    COMPACT,  // original 3x4 grid view
    DEVICE,   // full M8 tracker device look
}

data class ServerSettings(
    val host: String = "100.65.234.75",
    val port: Int = 8765,
    val autoConnect: Boolean = true,
    val buttonLayout: ButtonLayout = ButtonLayout.DEVICE,
    val gamepadEnabled: Boolean = true,
    val keyboardEnabled: Boolean = true,
)

class ServerConfig(private val context: Context) {

    companion object {
        private val HOST = stringPreferencesKey("server_host")
        private val PORT = intPreferencesKey("server_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val BUTTON_LAYOUT = stringPreferencesKey("button_layout")
        private val GAMEPAD_ENABLED = booleanPreferencesKey("gamepad_enabled")
        private val KEYBOARD_ENABLED = booleanPreferencesKey("keyboard_enabled")
    }

    val settings: Flow<ServerSettings> = context.dataStore.data.map { prefs ->
        ServerSettings(
            host = prefs[HOST] ?: "100.65.234.75",
            port = prefs[PORT] ?: 8765,
            autoConnect = prefs[AUTO_CONNECT] ?: true,
            buttonLayout = prefs[BUTTON_LAYOUT]
                ?.let { runCatching { ButtonLayout.valueOf(it) }.getOrNull() }
                ?: ButtonLayout.DEVICE,
            gamepadEnabled = prefs[GAMEPAD_ENABLED] ?: true,
            keyboardEnabled = prefs[KEYBOARD_ENABLED] ?: true,
        )
    }

    suspend fun save(settings: ServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[HOST] = settings.host
            prefs[PORT] = settings.port
            prefs[AUTO_CONNECT] = settings.autoConnect
            prefs[BUTTON_LAYOUT] = settings.buttonLayout.name
            prefs[GAMEPAD_ENABLED] = settings.gamepadEnabled
            prefs[KEYBOARD_ENABLED] = settings.keyboardEnabled
        }
    }
}
