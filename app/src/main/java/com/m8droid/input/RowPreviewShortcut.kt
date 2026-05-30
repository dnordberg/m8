package com.m8droid.input

import com.m8droid.emulator.M8Emulator
import com.m8droid.protocol.M8Commands

/**
 * Edge detector for the phone row-preview gesture.
 *
 * EDIT+PLAY auditions the selected row in SONG/CHAIN/PHRASE without toggling
 * sequencer playback. The detector returns true only on the chord's rising edge
 * so touch controls and sticky modifiers do not retrigger every frame while held.
 */
class RowPreviewShortcut {
    private var wasHeld = false

    fun consume(keys: Int, screen: Int): Boolean {
        val supportedScreen = screen == M8Emulator.SCREEN_SONG ||
            screen == M8Emulator.SCREEN_CHAIN ||
            screen == M8Emulator.SCREEN_PHRASE
        val held = supportedScreen &&
            keys and M8Commands.KEY_EDIT != 0 &&
            keys and M8Commands.KEY_PLAY != 0
        val fire = held && !wasHeld
        wasHeld = held
        return fire
    }
}
