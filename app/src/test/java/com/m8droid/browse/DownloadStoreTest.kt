package com.m8droid.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DownloadStoreTest {
    @Test
    fun `save reuses existing download for same source item instead of duplicating files`(@TempDir dir: File) {
        val store = DownloadStore(dir)
        val item = songItem(id = "starter", fileName = "starter.m8s")

        val first = store.save(item, byteArrayOf(1, 2, 3))
        val second = store.save(item, byteArrayOf(4, 5, 6))

        assertEquals(first.localPath, second.localPath)
        assertEquals("/Songs/starter.m8s", second.sdPath)
        assertEquals(1, File(dir, "Songs").listFiles { f -> f.extension == "m8s" }!!.size)
        assertEquals(listOf("starter"), store.list().map { it.id })
        assertEquals(3L, first.sizeBytes)
        assertEquals(3L, second.sizeBytes, "existing download should be reused without replacing file bytes")
    }

    @Test
    fun `findExisting returns downloaded entry only when indexed file still exists`(@TempDir dir: File) {
        val store = DownloadStore(dir)
        val item = songItem(id = "starter", fileName = "starter.m8s")
        val entry = store.save(item, byteArrayOf(1, 2, 3))

        assertEquals(entry.localPath, store.findExisting(item)?.localPath)

        File(entry.localPath).delete()
        assertEquals(null, store.findExisting(item))
    }

    @Test
    fun `downloaded flags mark matching remote items`(@TempDir dir: File) {
        val store = DownloadStore(dir)
        val downloaded = songItem(id = "starter", fileName = "starter.m8s")
        val fresh = songItem(id = "other", fileName = "other.m8s")
        store.save(downloaded, byteArrayOf(1, 2, 3))

        val states = DownloadStore.markDownloaded(listOf(downloaded, fresh), store.list())

        assertTrue(states.first { it.item.id == "starter" }.isDownloaded)
        assertNotNull(states.first { it.item.id == "starter" }.entry)
        assertTrue(!states.first { it.item.id == "other" }.isDownloaded)
    }

    @Test
    fun `downloaded flags do not cross match same id from another source`(@TempDir dir: File) {
        val store = DownloadStore(dir)
        val downloaded = songItem(id = "starter", fileName = "starter.m8s", sourceName = "Songs")
        val sameIdOtherSource = songItem(id = "starter", fileName = "starter.m8s", sourceName = "GitHub")
        store.save(downloaded, byteArrayOf(1, 2, 3))

        val states = DownloadStore.markDownloaded(listOf(downloaded, sameIdOtherSource), store.list())

        assertTrue(states.first { it.item.sourceName == "Songs" }.isDownloaded)
        assertTrue(!states.first { it.item.sourceName == "GitHub" }.isDownloaded)
    }

    @Test
    fun `downloaded flags do not cross match same id and source with another kind`(@TempDir dir: File) {
        val store = DownloadStore(dir)
        val downloadedSong = songItem(id = "starter", fileName = "starter.m8s", sourceName = "Songs")
        val sameIdInstrument = songItem(id = "starter", fileName = "starter.m8i", sourceName = "Songs").copy(
            kind = ContentKind.INSTRUMENT,
        )
        store.save(downloadedSong, byteArrayOf(1, 2, 3))

        val states = DownloadStore.markDownloaded(listOf(downloadedSong, sameIdInstrument), store.list())

        assertTrue(states.first { it.item.kind == ContentKind.SONG }.isDownloaded)
        assertTrue(!states.first { it.item.kind == ContentKind.INSTRUMENT }.isDownloaded)
        assertEquals(null, store.findExisting(sameIdInstrument))
    }

    private fun songItem(
        id: String,
        fileName: String,
        sourceName: String = "Songs",
    ): RemoteItem = RemoteItem(
        id = id,
        sourceName = sourceName,
        title = fileName.removeSuffix(".m8s"),
        author = "m8",
        description = null,
        tags = emptyList(),
        kind = ContentKind.SONG,
        downloadUrl = "https://example.com/$fileName",
        fileName = fileName,
        sizeBytes = null,
        license = null,
        downloadCount = null,
        createdAt = null,
        landingUrl = null,
    )
}
