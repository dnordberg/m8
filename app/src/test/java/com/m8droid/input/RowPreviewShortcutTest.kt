package com.m8droid.input

import com.m8droid.emulator.M8Emulator
import com.m8droid.protocol.M8Commands
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RowPreviewShortcutTest {
    @Test
    fun `edit plus play fires once on play press for song chain and phrase screens`() {
        val shortcut = RowPreviewShortcut()
        val chord = M8Commands.KEY_EDIT or M8Commands.KEY_PLAY

        assertTrue(shortcut.consume(chord, M8Emulator.SCREEN_SONG))
        assertFalse(shortcut.consume(chord, M8Emulator.SCREEN_SONG), "held chord must not retrigger")
        assertFalse(shortcut.consume(0, M8Emulator.SCREEN_SONG))

        assertTrue(shortcut.consume(chord, M8Emulator.SCREEN_CHAIN))
        assertFalse(shortcut.consume(chord, M8Emulator.SCREEN_CHAIN), "held chord must not retrigger")
        assertFalse(shortcut.consume(0, M8Emulator.SCREEN_CHAIN))

        assertTrue(shortcut.consume(chord, M8Emulator.SCREEN_PHRASE))
    }

    @Test
    fun `preview shortcut ignores plain play and non song part screens`() {
        val shortcut = RowPreviewShortcut()

        assertFalse(shortcut.consume(M8Commands.KEY_PLAY, M8Emulator.SCREEN_PHRASE))
        assertFalse(shortcut.consume(M8Commands.KEY_EDIT, M8Emulator.SCREEN_PHRASE))
        assertFalse(shortcut.consume(M8Commands.KEY_EDIT or M8Commands.KEY_PLAY, M8Emulator.SCREEN_INSTRUMENT))
        assertFalse(shortcut.consume(M8Commands.KEY_EDIT or M8Commands.KEY_PLAY, M8Emulator.SCREEN_MIXER))
    }
}
