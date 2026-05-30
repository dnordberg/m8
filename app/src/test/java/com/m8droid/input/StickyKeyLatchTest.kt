package com.m8droid.input

import com.m8droid.protocol.M8Commands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StickyKeyLatchTest {
    @Test
    fun togglesOnlyModifierKeysIntoEffectiveMask() {
        val latch = StickyKeyLatch()

        assertEquals(M8Commands.KEY_SHIFT, latch.toggle(M8Commands.KEY_SHIFT))
        assertEquals(M8Commands.KEY_SHIFT or M8Commands.KEY_RIGHT, latch.applyTo(M8Commands.KEY_RIGHT))

        assertEquals(M8Commands.KEY_SHIFT, latch.toggle(M8Commands.KEY_PLAY))
        assertEquals(M8Commands.KEY_SHIFT, latch.mask)
    }

    @Test
    fun togglingSameModifierClearsIt() {
        val latch = StickyKeyLatch()

        latch.toggle(M8Commands.KEY_OPTION)
        assertEquals(M8Commands.KEY_OPTION, latch.mask)

        latch.toggle(M8Commands.KEY_OPTION)
        assertEquals(0, latch.mask)
    }

    @Test
    fun clearRemovesAllStickyModifiers() {
        val latch = StickyKeyLatch()

        latch.toggle(M8Commands.KEY_OPTION)
        latch.toggle(M8Commands.KEY_EDIT)
        latch.toggle(M8Commands.KEY_SHIFT)

        assertEquals(M8Commands.KEY_OPTION or M8Commands.KEY_EDIT or M8Commands.KEY_SHIFT, latch.mask)
        latch.clear()
        assertEquals(0, latch.mask)
    }
}
