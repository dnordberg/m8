package com.m8droid.browse

import android.util.Log

/**
 * Patchstorage hosts community-uploaded M8 instruments, themes, and
 * occasionally songs under the Dirtywave M8 platform. Public REST API,
 * no auth required.
 *
 * Docs: https://patchstorage.com/docs/
 * Platform id for Dirtywave M8 is resolved dynamically on first call.
 */
class PatchstorageSource(private val http: HttpClient) : ContentSource {

    override val displayName = "Patchstorage"
    override val description = "Community M8 instruments, themes, and songs from patchstorage.com"

    private var cachedPlatformId: Int? = null

    override suspend fun fetchItems(query: String, page: Int): List<RemoteItem> {
        val platformId = resolvePlatformId() ?: return emptyList()
        val q = if (query.isBlank()) "" else "&search=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val url = "https://patchstorage.com/api/beta/patches?platforms=$platformId&page=$page&per_page=30$q"
        val arr = http.getJsonArray(url)

        val out = mutableListOf<RemoteItem>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val id = p.optInt("id").toString()
            val title = p.optString("title").ifEmpty { "Untitled" }
            val author = p.optJSONObject("author")?.optString("slug")
                ?: p.optJSONObject("author")?.optString("name")
            val desc = p.optString("excerpt").ifEmpty { p.optString("content") }
                .replace(Regex("<[^>]+>"), "")
                .trim()
                .take(600)
            val tagsArr = p.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) for (j in 0 until tagsArr.length()) {
                    val t = tagsArr.getJSONObject(j).optString("slug")
                    if (t.isNotEmpty()) add(t)
                }
            }
            val filesArr = p.optJSONArray("files") ?: continue
            if (filesArr.length() == 0) continue
            // Patchstorage sometimes attaches multiple files per patch. Emit one
            // RemoteItem per file so the user can pick which one to grab.
            for (j in 0 until filesArr.length()) {
                val f = filesArr.getJSONObject(j)
                val fileUrl = f.optString("url")
                if (fileUrl.isEmpty()) continue
                val fileName = f.optString("filename").ifEmpty { fileUrl.substringAfterLast('/') }
                val kind = RemoteContentClassifier.classify(fileName)
                out += RemoteItem(
                    id = "$id-$j",
                    sourceName = displayName,
                    title = if (filesArr.length() > 1) "$title ($fileName)" else title,
                    author = author,
                    description = desc,
                    tags = tags,
                    kind = kind,
                    downloadUrl = fileUrl,
                    fileName = fileName,
                    sizeBytes = f.optLong("filesize").takeIf { it > 0 },
                    license = p.optJSONObject("license")?.optString("name"),
                    downloadCount = p.optInt("download_count").takeIf { it > 0 },
                    createdAt = p.optString("created_at"),
                    landingUrl = p.optString("url").ifEmpty { null },
                )
            }
        }
        return out
    }

    private suspend fun resolvePlatformId(): Int? {
        cachedPlatformId?.let { return it }
        return runCatching {
            val arr = http.getJsonArray("https://patchstorage.com/api/beta/platforms?per_page=100")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name") + " " + o.optString("slug")
                if (name.contains("m8", ignoreCase = true)) {
                    return@runCatching o.getInt("id").also { cachedPlatformId = it }
                }
            }
            null
        }.onFailure { Log.w("PatchstorageSource", "platform lookup failed: ${it.message}") }
            .getOrNull()
    }
}
