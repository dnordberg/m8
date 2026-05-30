package com.m8droid.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ServerSettingsTest {
    @Test
    fun `hex editor is disabled by default`() {
        assertFalse(ServerSettings().hexEditorEnabled)
    }
}
