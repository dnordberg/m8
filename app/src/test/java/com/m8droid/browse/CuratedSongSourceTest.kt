package com.m8droid.browse

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CuratedSongSourceTest {
    @Test
    fun `curated song source exposes downloadable m8s songs`() = runTest {
        val songs = CuratedSongSource().fetchItems()

        assertTrue(songs.isNotEmpty())
        assertTrue(songs.all { it.kind == ContentKind.SONG })
        assertTrue(songs.all { it.fileName.endsWith(".m8s", ignoreCase = true) })
        assertTrue(songs.all { it.downloadUrl.startsWith("https://") })
    }

    @Test
    fun `remote content classifier treats m8s names as songs`() {
        assertEquals(ContentKind.SONG, RemoteContentClassifier.classify("Songs/demo.M8S"))
        assertEquals(ContentKind.INSTRUMENT, RemoteContentClassifier.classify("Bass.m8i"))
        assertEquals(ContentKind.PACK, RemoteContentClassifier.classify("community-pack.7z"))
    }
}
