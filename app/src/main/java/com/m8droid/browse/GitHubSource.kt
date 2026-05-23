package com.m8droid.browse

import android.util.Log

/**
 * Browses a curated set of GitHub repos that host M8 content. Uses the
 * contents API (no auth) to enumerate files and raw.githubusercontent.com
 * for downloads.
 *
 * Curated repo list is hardcoded — GitHub's search API requires auth for
 * meaningful use, and a fixed list gives predictable results.
 */
class GitHubSource(private val http: HttpClient) : ContentSource {

    override val displayName = "GitHub"
    override val description = "Curated M8 content from community GitHub repositories"

    private data class Repo(
        val owner: String,
        val name: String,
        val path: String,
        val defaultBranch: String = "main",
    )

    // Curated list of community repos with M8 content.
    private val repos = listOf(
        Repo("laamaa", "m8i", ""),
        Repo("tobokegao", "m8-tracker-instruments", ""),
        Repo("d-huck", "m8-themes", ""),
    )

    override suspend fun fetchItems(query: String, page: Int): List<RemoteItem> {
        if (page > 1) return emptyList()
        val all = mutableListOf<RemoteItem>()
        for (repo in repos) {
            runCatching { walkRepo(repo) }
                .onSuccess { all.addAll(it) }
                .onFailure { Log.w("GitHubSource", "walk failed: ${repo.owner}/${repo.name}: ${it.message}") }
        }
        return if (query.isBlank()) all
        else all.filter { it.title.contains(query, ignoreCase = true) || it.tags.any { t -> t.contains(query, ignoreCase = true) } }
    }

    private suspend fun walkRepo(repo: Repo): List<RemoteItem> {
        // Use the git tree API for a single recursive listing — cheaper than
        // walking the contents API directory by directory.
        val treeUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/git/trees/${repo.defaultBranch}?recursive=1"
        val json = http.getJsonObject(treeUrl, mapOf("Accept" to "application/vnd.github+json"))
        val tree = json.optJSONArray("tree") ?: return emptyList()

        val out = mutableListOf<RemoteItem>()
        for (i in 0 until tree.length()) {
            val node = tree.getJSONObject(i)
            if (node.optString("type") != "blob") continue
            val path = node.getString("path")
            val kind = RemoteContentClassifier.classify(path).takeIf { it != ContentKind.UNKNOWN } ?: continue
            val rawUrl = "https://raw.githubusercontent.com/${repo.owner}/${repo.name}/${repo.defaultBranch}/$path"
            val name = path.substringAfterLast('/')
            out += RemoteItem(
                id = "${repo.owner}/${repo.name}/$path",
                sourceName = displayName,
                title = name,
                author = repo.owner,
                description = "From ${repo.owner}/${repo.name} — $path",
                tags = listOf(repo.name),
                kind = kind,
                downloadUrl = rawUrl,
                fileName = name,
                sizeBytes = node.optLong("size").takeIf { it > 0 },
                license = null,
                downloadCount = null,
                createdAt = null,
                landingUrl = "https://github.com/${repo.owner}/${repo.name}/blob/${repo.defaultBranch}/$path",
            )
        }
        return out
    }
}