package com.m8droid.browse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A virtual M8 SD card. Persists downloaded content in the same folder
 * layout the real M8 hardware uses on its microSD card:
 *
 *     filesDir/m8sd/
 *       Songs/        *.m8s
 *       Instruments/  *.m8i
 *       Samples/      *.wav
 *       Themes/       *.m8t
 *       Scales/       *.m8n
 *       Packs/        *.zip / *.7z (community bundles, unpacked on demand)
 *
 * A JSON index at m8sd/index.json records metadata per entry so the browse
 * dialog can show what's been saved and where, and so future parser code
 * can enumerate the virtual SD like the real M8 firmware does.
 */
class DownloadStore(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, "m8sd"))

    private val indexFile: File = File(root, "index.json")

    init {
        root.mkdirs()
        // Pre-create the M8 SD layout so the directories exist even before
        // any downloads land — matches how a freshly-formatted M8 SD starts.
        for (kind in ContentKind.values()) {
            File(root, folderFor(kind)).mkdirs()
        }
    }

    val rootPath: String get() = root.absolutePath

    fun folderFor(kind: ContentKind): String = when (kind) {
        ContentKind.SONG -> "Songs"
        ContentKind.INSTRUMENT -> "Instruments"
        ContentKind.SAMPLE -> "Samples"
        ContentKind.THEME -> "Themes"
        ContentKind.SCALE -> "Scales"
        ContentKind.PACK -> "Packs"
        ContentKind.UNKNOWN -> "Other"
    }

    data class Entry(
        val id: String,
        val sourceName: String,
        val title: String,
        val author: String?,
        val kind: ContentKind,
        val localPath: String,
        val sdPath: String,
        val sizeBytes: Long,
        val downloadedAt: Long,
        val originalUrl: String,
        val license: String?,
    )

    data class DownloadedState(
        val item: RemoteItem,
        val entry: Entry?,
    ) {
        val isDownloaded: Boolean get() = entry != null
    }

    @Synchronized
    fun findExisting(item: RemoteItem): Entry? = list().firstOrNull { entry ->
        entry.id == item.id &&
            entry.sourceName == item.sourceName &&
            entry.kind == item.kind &&
            File(entry.localPath).exists()
    }

    @Synchronized
    fun save(item: RemoteItem, bytes: ByteArray): Entry {
        findExisting(item)?.let { return it }
        val kindDir = File(root, folderFor(item.kind)).apply { mkdirs() }
        val safeName = sanitize(item.fileName).ifEmpty { "${item.id}.bin" }
        val target = uniqueFile(kindDir, safeName)
        target.writeBytes(bytes)

        // Virtual M8-SD path: "/Instruments/foo.m8i" — what the M8 firmware
        // would see on the real device. Not a filesystem path; a label.
        val sdPath = "/${folderFor(item.kind)}/${target.name}"

        val entry = Entry(
            id = item.id,
            sourceName = item.sourceName,
            title = item.title,
            author = item.author,
            kind = item.kind,
            localPath = target.absolutePath,
            sdPath = sdPath,
            sizeBytes = bytes.size.toLong(),
            downloadedAt = System.currentTimeMillis(),
            originalUrl = item.downloadUrl,
            license = item.license,
        )
        appendEntry(entry)
        return entry
    }

    private fun uniqueFile(dir: File, name: String): File {
        val base = File(dir, name)
        if (!base.exists()) return base
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (true) {
            val f = File(dir, "${stem}_$i$ext")
            if (!f.exists()) return f
            i++
        }
    }

    @Synchronized
    fun list(): List<Entry> {
        if (!indexFile.exists()) return emptyList()
        val text = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                sourceName = o.getString("sourceName"),
                title = o.getString("title"),
                author = o.optString("author").ifEmpty { null },
                kind = runCatching { ContentKind.valueOf(o.getString("kind")) }
                    .getOrDefault(ContentKind.UNKNOWN),
                localPath = o.getString("localPath"),
                sdPath = o.optString("sdPath").ifEmpty { "/Other/${o.getString("title")}" },
                sizeBytes = o.getLong("sizeBytes"),
                downloadedAt = o.getLong("downloadedAt"),
                originalUrl = o.getString("originalUrl"),
                license = o.optString("license").ifEmpty { null },
            )
        }
    }

    private fun appendEntry(entry: Entry) {
        val existing = list().toMutableList()
        existing.removeAll { it.id == entry.id && it.sourceName == entry.sourceName && it.kind == entry.kind }
        existing.add(entry)
        val arr = JSONArray()
        existing.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("sourceName", e.sourceName)
            o.put("title", e.title)
            o.put("author", e.author ?: "")
            o.put("kind", e.kind.name)
            o.put("localPath", e.localPath)
            o.put("sdPath", e.sdPath)
            o.put("sizeBytes", e.sizeBytes)
            o.put("downloadedAt", e.downloadedAt)
            o.put("originalUrl", e.originalUrl)
            o.put("license", e.license ?: "")
            arr.put(o)
        }
        indexFile.writeText(arr.toString())
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    companion object {
        fun markDownloaded(items: List<RemoteItem>, entries: List<Entry>): List<DownloadedState> = items.map { item ->
            val entry = entries.firstOrNull { entry ->
                entry.id == item.id &&
                    entry.sourceName == item.sourceName &&
                    entry.kind == item.kind &&
                    File(entry.localPath).exists()
            }
            DownloadedState(item, entry)
        }
    }
}
