package com.m8.input

import android.view.KeyEvent
import com.m8.protocol.M8Commands

/**
 * Maps Android keyboard/gamepad keys to M8 button bitmask values.
 */
object KeyMapper {

    private val keyMap = mapOf(
        // Keyboard
        KeyEvent.KEYCODE_DPAD_UP to M8Commands.KEY_UP,
        KeyEvent.KEYCODE_DPAD_DOWN to M8Commands.KEY_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to M8Commands.KEY_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to M8Commands.KEY_RIGHT,
        KeyEvent.KEYCODE_Z to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_X to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_SHIFT_LEFT to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_SPACE to M8Commands.KEY_PLAY,
        // Arrow keys (some keyboards)
        KeyEvent.KEYCODE_W to M8Commands.KEY_UP,
        KeyEvent.KEYCODE_S to M8Commands.KEY_DOWN,
        KeyEvent.KEYCODE_A to M8Commands.KEY_LEFT,
        KeyEvent.KEYCODE_D to M8Commands.KEY_RIGHT,
        // Gamepad
        KeyEvent.KEYCODE_BUTTON_A to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_BUTTON_B to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_BUTTON_L1 to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_BUTTON_START to M8Commands.KEY_PLAY,
        KeyEvent.KEYCODE_BUTTON_R1 to M8Commands.KEY_PLAY,
    )

    fun mapKey(keyCode: Int): Int? = keyMap[keyCode]
}
