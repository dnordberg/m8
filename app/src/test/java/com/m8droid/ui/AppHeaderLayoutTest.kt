package com.m8droid.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppHeaderLayoutTest {
    @Test
    fun `header does not reserve top cutout space`() {
        assertFalse(AppHeaderLayout.reservesTopCutoutSpace)
    }

    @Test
    fun `header omits inline save button to keep actions compact`() {
        assertFalse(AppHeaderLayout.showsInlineSaveButton)
    }

    @Test
    fun `header actions use compact horizontal spacing`() {
        assertTrue(AppHeaderLayout.horizontalPaddingDp <= 12)
        assertTrue(AppHeaderLayout.actionGapDp <= 12)
        assertTrue(AppHeaderLayout.iconHitSizeDp <= 32)
    }
}
