package com.m8droid.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileHubSectionsTest {
    @Test
    fun `file hub defaults to recent before download sources`() {
        val tabs = FileHubTabs.labels(listOf("Songs", "Github"), includeSd = true, includeProjects = true)

        assertEquals("RECENT", FileHubTabs.defaultLabel)
        assertEquals("RECENT", tabs.first())
        assertTrue(tabs.indexOf("Songs") > tabs.indexOf("RECENT"))
        assertTrue(tabs.indexOf("SD") > tabs.indexOf("Songs"))
        assertTrue(tabs.indexOf("PROJECTS") > tabs.indexOf("SD"))
    }

    @Test
    fun `top file actions are compact phone labels`() {
        assertEquals("NEW", FileHubTabs.newActionLabel)
        assertEquals("OPEN", FileHubTabs.openActionLabel)
    }
}
