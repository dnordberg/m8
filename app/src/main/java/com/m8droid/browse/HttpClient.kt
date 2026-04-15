package com.m8droid.browse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin coroutine wrapper over the OkHttp client already in the project.
 * Only GETs — we never POST to content sources. All network work is pinned
 * to the IO dispatcher.
 */
class HttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
                resp.body?.bytes() ?: error("Empty body for $url")
            }
        }

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
                resp.body?.string() ?: error("Empty body for $url")
            }
        }

    suspend fun getJsonObject(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(getString(url, headers))

    suspend fun getJsonArray(url: String, headers: Map<String, String> = emptyMap()): JSONArray =
        JSONArray(getString(url, headers))

    companion object {
        private const val USER_AGENT = "m8droid/0.1 (+https://github.com/m8droid)"
    }
}
