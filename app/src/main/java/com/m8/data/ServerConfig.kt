package com.m8.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "m8_settings")

data class ServerSettings(
    val host: String = "100.65.234.75",
    val port: Int = 8765,
    val autoConnect: Boolean = true,
)

class ServerConfig(private val context: Context) {

    companion object {
        private val HOST = stringPreferencesKey("server_host")
        private val PORT = intPreferencesKey("server_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    }

    val settings: Flow<ServerSettings> = context.dataStore.data.map { prefs ->
        ServerSettings(
            host = prefs[HOST] ?: "100.65.234.75",
            port = prefs[PORT] ?: 8765,
            autoConnect = prefs[AUTO_CONNECT] ?: true,
        )
    }

    suspend fun save(settings: ServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[HOST] = settings.host
            prefs[PORT] = settings.port
            prefs[AUTO_CONNECT] = settings.autoConnect
        }
    }
}
