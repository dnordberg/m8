package com.m8droid.browse

object DownloadSources {
    fun create(http: HttpClient): List<ContentSource> = listOf(
        CuratedSongSource(),
        GitHubSource(http),
        ArchiveOrgSource(http),
    )

    fun displayNames(): List<String> = listOf(
        "Songs",
        "GitHub",
        "Archive.org",
    )
}
