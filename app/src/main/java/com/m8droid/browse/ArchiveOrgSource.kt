package com.m8droid.browse

/**
 * Archive.org hosts the M8 Community SD-card Starter Pack and similar
 * bundles. We enumerate files inside known items via the metadata API
 * and download individual files rather than the whole archive.
 *
 * Metadata API returns files as a JSON array with name, format, size.
 */
class ArchiveOrgSource(private val http: HttpClient) : ContentSource {

    override val displayName = "Archive.org"
    override val description = "M8 Community SD-card Starter Pack and mirrored bundles"

    private data class Item(val identifier: String, val label: String)

    // Known archive.org items with M8 content. Add more here as we find them.
    private val items = listOf(
        Item("ChipmusicResources", "Chipmusic Resources"),
    )

    override suspend fun fetchItems(query: String, page: Int): List<RemoteItem> {
        if (page > 1) return emptyList()
        val out = mutableListOf<RemoteItem>()
        for (item in items) {
            runCatching { enumerate(item) }.getOrNull()?.let(out::addAll)
        }
        return if (query.isBlank()) out
        else out.filter { it.title.contains(query, ignoreCase = true) }
    }

    private suspend fun enumerate(item: Item): List<RemoteItem> {
        val meta = http.getJsonObject("https://archive.org/metadata/${item.identifier}")
        val files = meta.optJSONArray("files") ?: return emptyList()
        val out = mutableListOf<RemoteItem>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name")
            if (name.isEmpty()) continue
            val lower = name.lowercase()
            // Only surface M8-relevant files. .7z/.zip stays so users can grab the starter pack.
            val kind = RemoteContentClassifier.classify(name)
            if (kind == ContentKind.UNKNOWN || (kind == ContentKind.PACK && !lower.contains("m8"))) continue
            val size = f.optString("size").toLongOrNull()
            out += RemoteItem(
                id = "${item.identifier}/$name",
                sourceName = displayName,
                title = name.substringAfterLast('/'),
                author = meta.optJSONObject("metadata")?.optString("uploader"),
                description = meta.optJSONObject("metadata")?.optString("description")
                    ?.replace(Regex("<[^>]+>"), "")?.take(600),
                tags = listOfNotNull(item.label),
                kind = kind,
                downloadUrl = "https://archive.org/download/${item.identifier}/$name",
                fileName = name.substringAfterLast('/'),
                sizeBytes = size,
                license = meta.optJSONObject("metadata")?.optString("licenseurl"),
                downloadCount = null,
                createdAt = meta.optJSONObject("metadata")?.optString("publicdate"),
                landingUrl = "https://archive.org/details/${item.identifier}",
            )
        }
        return out
    }
}
