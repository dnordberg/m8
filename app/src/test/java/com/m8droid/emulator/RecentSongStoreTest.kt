package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RecentSongStoreTest {
    @Test
    fun recordsLoadedFilesNewestFirstAndDeduplicatesByPath() {
        val dir = Files.createTempDirectory("m8-recents-").toFile()
        try {
            val store = RecentSongStore(File(dir, "recent.json"), maxEntries = 3)

            store.record(RecentSongStore.Entry("/songs/one.m8s", "ONE", RecentSongStore.Kind.SONG, loadedAt = 100))
            store.record(RecentSongStore.Entry("/songs/two.m8s", "TWO", RecentSongStore.Kind.SONG, loadedAt = 200))
            store.record(RecentSongStore.Entry("/songs/one.m8s", "ONE AGAIN", RecentSongStore.Kind.SONG, loadedAt = 300))

            val recent = store.list()
            assertEquals(listOf("ONE AGAIN", "TWO"), recent.map { it.title })
            assertEquals(listOf(300L, 200L), recent.map { it.loadedAt })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun lastLoadedReturnsMostRecentSongOrProject() {
        val dir = Files.createTempDirectory("m8-recents-").toFile()
        try {
            val store = RecentSongStore(File(dir, "recent.json"))

            store.record(RecentSongStore.Entry("/songs/old.m8s", "OLD", RecentSongStore.Kind.SONG, loadedAt = 100))
            store.record(RecentSongStore.Entry("/projects/current.m8droid", "CURRENT", RecentSongStore.Kind.PROJECT, loadedAt = 500))

            assertEquals("/projects/current.m8droid", store.lastLoaded()?.location)
            assertEquals(RecentSongStore.Kind.PROJECT, store.lastLoaded()?.kind)
        } finally {
            dir.deleteRecursively()
        }
    }
}
