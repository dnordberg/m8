package com.m8droid.emulator

import java.io.File

/**
 * Small file-backed MRU list for songs/projects loaded into the app.
 * Stores enough metadata for the File/Open view and startup restore.
 */
class RecentSongStore(
    private val file: File,
    private val maxEntries: Int = 12,
) {
    enum class Kind { SONG, PROJECT }

    data class Entry(
        val location: String,
        val title: String,
        val kind: Kind,
        val loadedAt: Long = System.currentTimeMillis(),
    )

    @Synchronized
    fun record(entry: Entry) {
        val updated = list()
            .filterNot { it.location == entry.location }
            .toMutableList()
        updated.add(0, entry)
        write(updated.take(maxEntries))
    }

    @Synchronized
    fun list(): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .mapNotNull { decodeLine(it) }
                .sortedByDescending { it.loadedAt }
                .take(maxEntries)
        }.getOrDefault(emptyList())
    }

    fun lastLoaded(): Entry? = list().firstOrNull()

    private fun write(entries: List<Entry>) {
        file.parentFile?.mkdirs()
        file.writeText(entries.joinToString("\n") { encodeLine(it) })
    }

    private fun encodeLine(entry: Entry): String = listOf(
        entry.loadedAt.toString(),
        entry.kind.name,
        escape(entry.location),
        escape(entry.title),
    ).joinToString("\t")

    private fun decodeLine(line: String): Entry? {
        val parts = line.split('\t')
        if (parts.size != 4) return null
        val loadedAt = parts[0].toLongOrNull() ?: return null
        val kind = runCatching { Kind.valueOf(parts[1]) }.getOrNull() ?: return null
        return Entry(
            location = unescape(parts[2]),
            title = unescape(parts[3]),
            kind = kind,
            loadedAt = loadedAt,
        )
    }

    private fun escape(value: String): String = value
        .replace("%", "%25")
        .replace("\t", "%09")
        .replace("\n", "%0A")

    private fun unescape(value: String): String = value
        .replace("%0A", "\n")
        .replace("%09", "\t")
        .replace("%25", "%")
}
