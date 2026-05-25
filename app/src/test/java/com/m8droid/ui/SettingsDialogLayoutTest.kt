package com.m8droid.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsDialogLayoutTest {
    @Test
    fun `settings panel uses help and file dialog dimensions`() {
        assertEquals(0.88f, SettingsDialogLayout.panelWidthFraction)
        assertEquals(12, SettingsDialogLayout.panelPaddingDp)
        assertEquals(8, SettingsDialogLayout.cardCornerDp)
    }
}
