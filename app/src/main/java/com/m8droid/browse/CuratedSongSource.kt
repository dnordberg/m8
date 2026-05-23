package com.m8droid.browse

/**
 * Network-light song source for known downloadable .m8s files.
 *
 * It gives the File/Download view an immediate Songs subsection without relying
 * on GitHub code search auth. The URLs are raw GitHub assets so the existing
 * download pipeline still saves them into m8sd/Songs and loads via M8sParser.
 */
class CuratedSongSource : ContentSource {
    override val displayName = "Songs"
    override val description = "Curated downloadable .m8s songs and starter files"

    private data class Song(
        val id: String,
        val title: String,
        val author: String,
        val fileName: String,
        val url: String,
        val description: String,
    )

    private val songs = listOf(
        Song(
            id = "m8droid-v4empty",
            title = "V4 Empty Starter",
            author = "Dirtywave / m8droid fixture",
            fileName = "V4EMPTY.m8s",
            url = "https://raw.githubusercontent.com/dnordberg/m8/feat/note-phrase-m8s-sampler-synth/app/src/test/resources/m8songs/V4EMPTY.m8s",
            description = "Clean M8 v4 starter song for testing loading, saving, and editing flows.",
        ),
        Song(
            id = "m8droid-v41empty",
            title = "V4.1 Empty Starter",
            author = "Dirtywave / m8droid fixture",
            fileName = "V4-1EMPTY.m8s",
            url = "https://raw.githubusercontent.com/dnordberg/m8/feat/note-phrase-m8s-sampler-synth/app/src/test/resources/m8songs/V4-1EMPTY.m8s",
            description = "M8 v4.1-compatible empty song fixture; useful as a known-good blank remote load.",
        ),
        Song(
            id = "m8droid-command-mapping",
            title = "Command Mapping Fixture",
            author = "Dirtywave / m8droid fixture",
            fileName = "CMDMAPPING_4_0.m8s",
            url = "https://raw.githubusercontent.com/dnordberg/m8/feat/note-phrase-m8s-sampler-synth/app/src/test/resources/m8songs/CMDMAPPING_4_0.m8s",
            description = "Small song fixture with M8 command data for parser/playback regression checks.",
        ),
    )

    override suspend fun fetchItems(query: String, page: Int): List<RemoteItem> {
        if (page > 1) return emptyList()
        val filtered = if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
        }
        return filtered.map { song ->
            RemoteItem(
                id = song.id,
                sourceName = displayName,
                title = song.title,
                author = song.author,
                description = song.description,
                tags = listOf("song", "m8s", "starter"),
                kind = RemoteContentClassifier.classify(song.fileName),
                downloadUrl = song.url,
                fileName = song.fileName,
                sizeBytes = null,
                license = null,
                downloadCount = null,
                createdAt = null,
                landingUrl = song.url.replace("raw.githubusercontent.com", "github.com").replace("/feat/note-phrase-m8s-sampler-synth/", "/blob/feat/note-phrase-m8s-sampler-synth/"),
            )
        }
    }
}
