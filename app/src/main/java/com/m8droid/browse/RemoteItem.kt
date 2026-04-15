package com.m8droid.browse

enum class ContentKind {
    SONG,        // .m8s
    INSTRUMENT,  // .m8i
    THEME,       // .m8t
    SCALE,       // .m8n
    SAMPLE,      // .wav
    PACK,        // .zip / .7z bundle
    UNKNOWN,
}

/**
 * A downloadable item surfaced from a ContentSource.
 *
 * All fields except id/title/downloadUrl/sourceName are best-effort —
 * sources populate what they have and leave the rest null/empty.
 */
data class RemoteItem(
    val id: String,
    val sourceName: String,
    val title: String,
    val author: String?,
    val description: String?,
    val tags: List<String>,
    val kind: ContentKind,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long?,
    val license: String?,
    val downloadCount: Int?,
    val createdAt: String?,
    val landingUrl: String?,
)
