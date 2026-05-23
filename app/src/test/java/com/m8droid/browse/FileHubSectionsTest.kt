package com.m8droid.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class FileHubSectionsTest {
    @Test
    fun `file hub uses exactly three top tabs`() {
        assertEquals(listOf("RECENT", "OPEN DEVICE", "DOWNLOAD"), FileHubTabs.topTabLabels)
        assertEquals("RECENT", FileHubTabs.defaultLabel)
    }

    @Test
    fun `download sources are chips inside download tab not top tabs`() {
        val sources = FileHubTabs.downloadSourceLabels(listOf("Songs", "GitHub", "Patchstorage", "Archive.org"))

        assertEquals(listOf("Songs", "GitHub", "Patchstorage", "Archive.org"), sources)
        assertFalse(FileHubTabs.topTabLabels.contains("Songs"))
        assertFalse(FileHubTabs.topTabLabels.contains("SD"))
        assertFalse(FileHubTabs.topTabLabels.contains("PROJECTS"))
    }

    @Test
    fun `new song is the standalone action above tabs`() {
        assertEquals("+ NEW SONG · clears current", FileHubTabs.newSongBannerLabel)
    }
}
