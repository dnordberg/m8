package com.m8droid.browse

/** Shared extension classifier for remote and local-download browser entries. */
object RemoteContentClassifier {
    fun classify(nameOrPath: String): ContentKind {
        val lower = nameOrPath.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".m8s") -> ContentKind.SONG
            lower.endsWith(".m8i") -> ContentKind.INSTRUMENT
            lower.endsWith(".m8t") -> ContentKind.THEME
            lower.endsWith(".m8n") -> ContentKind.SCALE
            lower.endsWith(".wav") -> ContentKind.SAMPLE
            lower.endsWith(".zip") || lower.endsWith(".7z") -> ContentKind.PACK
            else -> ContentKind.UNKNOWN
        }
    }
}
