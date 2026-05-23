package com.m8droid.tutorial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TutorialPanelStateTest {
    @Test
    fun `panel cycles through compact half and expanded states`() {
        val state = TutorialPanelState()

        assertEquals(TutorialPanelHeight.HALF, state.height)
        assertEquals(0.34f, state.heightFraction)

        state.toggleHeight()
        assertEquals(TutorialPanelHeight.EXPANDED, state.height)
        assertEquals(0.58f, state.heightFraction)

        state.toggleHeight()
        assertEquals(TutorialPanelHeight.COMPACT, state.height)
        assertEquals(0.18f, state.heightFraction)

        state.toggleHeight()
        assertEquals(TutorialPanelHeight.HALF, state.height)
    }

    @Test
    fun `dragging chooses nearest stable height`() {
        val state = TutorialPanelState()

        state.setHeightFromFraction(0.12f)
        assertEquals(TutorialPanelHeight.COMPACT, state.height)

        state.setHeightFromFraction(0.39f)
        assertEquals(TutorialPanelHeight.HALF, state.height)

        state.setHeightFromFraction(0.80f)
        assertEquals(TutorialPanelHeight.EXPANDED, state.height)
    }

    @Test
    fun `drag snap applies offset to current panel height`() {
        val state = TutorialPanelState()

        state.snapDragged(0.22f)
        assertEquals(TutorialPanelHeight.EXPANDED, state.height)

        state.snapDragged(-0.40f)
        assertEquals(TutorialPanelHeight.COMPACT, state.height)
    }
}
