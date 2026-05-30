package com.m8droid.ui

import com.m8droid.AppWindowLayout
import com.m8droid.browse.FileHubLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModalOverlayLayoutTest {
    @Test
    fun `app content draws behind the status bar instead of reserving notch height`() {
        assertTrue(AppWindowLayout.drawsBehindSystemBars)
        assertTrue(AppWindowLayout.statusBarTransparent)
        assertTrue(AppHeaderLayout.reservesTopCutoutSpace.not())
    }

    @Test
    fun `main m8 layout keeps display and controls centered`() {
        assertTrue(M8MainLayout.centersDisplayAndControls)
    }

    @Test
    fun `file hub uses the same centered modal position as help and settings`() {
        assertEquals("Center", FileHubLayout.panelAlignment)
        assertEquals(0.88f, SettingsDialogLayout.panelWidthFraction)
    }
}
