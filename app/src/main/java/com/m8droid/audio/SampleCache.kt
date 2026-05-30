package com.m8droid.audio

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes WAV samples from the app's virtual M8 SD card and keeps decoded PCM
 * out of the audio render path. Paths are M8-style labels such as
 * `/Samples/Kicks/punch.wav`, not arbitrary filesystem paths.
 */
class SampleCache(private val sdRoot: File) {
    private val cache = ConcurrentHashMap<String, WavDecoder.DecodedWav>()
    private val canonicalRoot: File by lazy { sdRoot.canonicalFile }

    fun load(sdPath: String): WavDecoder.DecodedWav? {
        val file = resolve(sdPath) ?: return null
        val key = file.absolutePath
        return cache[key] ?: runCatching {
            WavDecoder.decode(file.readBytes())
        }.getOrNull()?.also { decoded ->
            cache[key] = decoded
        }
    }

    fun clear() = cache.clear()

    private fun resolve(sdPath: String): File? {
        val normalized = sdPath.trim().replace('\\', '/')
            .removePrefix("/")
            .takeIf { it.isNotBlank() } ?: return null
        val file = File(canonicalRoot, normalized).canonicalFile
        return if (file.path.startsWith(canonicalRoot.path) && file.isFile) file else null
    }
}
