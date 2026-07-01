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
    fun `main m8 layout pulls controls into the available gap under the display`() {
        assertTrue(M8MainLayout.centersDisplayAndControls.not())
        assertEquals("Top", M8MainLayout.controlsAnchor)
        assertTrue(M8MainLayout.quickActionTopFraction in 0.35f..0.50f)
    }

    @Test
    fun `file hub uses the same centered modal position as help and settings`() {
        assertEquals("Center", FileHubLayout.panelAlignment)
        assertEquals(ModalStyle.panelWidthFraction, FileHubLayout.dialogWidthFraction)
        assertEquals(0.88f, SettingsDialogLayout.panelWidthFraction)
        assertEquals(ModalStyle.panelWidthFraction, SettingsDialogLayout.panelWidthFraction)
        assertEquals(12, ModalStyle.panelPaddingDp)
        assertEquals(8, ModalStyle.cardCornerDp)
        assertEquals(0.80f, ModalStyle.scrimAlpha)
    }
}
