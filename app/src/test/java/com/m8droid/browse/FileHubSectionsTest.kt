package com.m8droid.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileHubSectionsTest {
    @Test
    fun `file hub uses exactly three top tabs`() {
        assertEquals(listOf("RECENT", "OPEN DEVICE", "DOWNLOAD"), FileHubTabs.topTabLabels)
        assertEquals("RECENT", FileHubTabs.defaultLabel)
    }

    @Test
    fun `download sources expose only implemented providers`() {
        val sources = DownloadSources.displayNames()

        assertEquals(listOf("Songs", "GitHub", "Archive.org"), sources)
        assertFalse(FileHubTabs.topTabLabels.contains("Songs"))
        assertFalse(FileHubTabs.topTabLabels.contains("SD"))
        assertFalse(FileHubTabs.topTabLabels.contains("PROJECTS"))
    }

    @Test
    fun `new song is the standalone action above tabs`() {
        assertEquals("+ NEW SONG · clears current", FileHubTabs.newSongBannerLabel)
    }

    @Test
    fun `file dialog keeps border inside phone screen`() {
        assertTrue(FileHubLayout.dialogWidthFraction <= 0.94f)
        assertTrue(FileHubLayout.dialogHeightFraction <= 0.90f)
        assertTrue(FileHubLayout.edgePaddingDp >= 20)
    }

    @Test
    fun `download source labels are compact enough to avoid chip wrapping`() {
        assertEquals(listOf("Songs", "GitHub", "Archive"), FileHubTabs.compactDownloadSourceLabels(DownloadSources.displayNames()))
        FileHubTabs.compactDownloadSourceLabels(DownloadSources.displayNames()).forEach { label ->
            assertTrue(label.length <= 7, "$label should fit in a phone chip without wrapping")
        }
    }
}
