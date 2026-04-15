package com.m8droid.input

import android.view.KeyEvent
import com.m8droid.protocol.M8Commands

/**
 * Maps Android keyboard/gamepad keys to M8 button bitmask values
 * and app-level hotkey actions.
 */
object KeyMapper {

    private val keyMap = mapOf(
        // Arrow keys
        KeyEvent.KEYCODE_DPAD_UP to M8Commands.KEY_UP,
        KeyEvent.KEYCODE_DPAD_DOWN to M8Commands.KEY_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to M8Commands.KEY_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to M8Commands.KEY_RIGHT,
        // WASD
        KeyEvent.KEYCODE_W to M8Commands.KEY_UP,
        KeyEvent.KEYCODE_S to M8Commands.KEY_DOWN,
        KeyEvent.KEYCODE_A to M8Commands.KEY_LEFT,
        KeyEvent.KEYCODE_D to M8Commands.KEY_RIGHT,
        // M8 buttons
        KeyEvent.KEYCODE_Z to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_P to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_X to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_E to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_LEFT_BRACKET to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_ALT_LEFT to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_ALT_RIGHT to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_SHIFT_LEFT to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_SPACE to M8Commands.KEY_PLAY,
        KeyEvent.KEYCODE_ENTER to M8Commands.KEY_PLAY,
        KeyEvent.KEYCODE_NUMPAD_ENTER to M8Commands.KEY_PLAY,
        // Gamepad
        KeyEvent.KEYCODE_BUTTON_A to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_BUTTON_B to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_BUTTON_X to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_BUTTON_Y to M8Commands.KEY_EDIT,
        KeyEvent.KEYCODE_BUTTON_L1 to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_BUTTON_L2 to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_BUTTON_R2 to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_BUTTON_START to M8Commands.KEY_PLAY,
        KeyEvent.KEYCODE_BUTTON_R1 to M8Commands.KEY_PLAY,
        KeyEvent.KEYCODE_BUTTON_SELECT to M8Commands.KEY_OPTION,
        KeyEvent.KEYCODE_BUTTON_THUMBL to M8Commands.KEY_SHIFT,
        KeyEvent.KEYCODE_BUTTON_THUMBR to M8Commands.KEY_EDIT,
    )

    /** Map a key to an M8 button bitmask, or null if not an M8 key */
    fun mapKey(keyCode: Int): Int? = keyMap[keyCode]

    // App-level hotkey actions (not M8 buttons)
    const val ACTION_TOGGLE_HOTKEYS = 1
    const val ACTION_SCREEN_1 = 10
    const val ACTION_SCREEN_2 = 11
    const val ACTION_SCREEN_3 = 12
    const val ACTION_SCREEN_4 = 13
    const val ACTION_SCREEN_5 = 14
    const val ACTION_SCREEN_6 = 15
    const val ACTION_SCREEN_7 = 16
    const val ACTION_SCREEN_8 = 17
    const val ACTION_TEMPO_DOWN = 20
    const val ACTION_TEMPO_UP = 21
    const val ACTION_TEMPO_DOWN_10 = 22
    const val ACTION_TEMPO_UP_10 = 23
    const val ACTION_PLAY_FROM_CURSOR = 24
    const val ACTION_TAB_NEXT = 25
    const val ACTION_TAB_PREV = 26
    const val ACTION_DISMISS = 30
    const val ACTION_TOGGLE_TUTORIAL = 31

    /**
     * Map a key event to an app-level hotkey action.
     * Returns null if the key is not an app hotkey.
     */
    fun mapHotkey(keyCode: Int, shiftHeld: Boolean): Int? = when {
        keyCode == KeyEvent.KEYCODE_T -> ACTION_TOGGLE_TUTORIAL
        keyCode == KeyEvent.KEYCODE_H -> ACTION_TOGGLE_HOTKEYS
        keyCode == KeyEvent.KEYCODE_ESCAPE -> ACTION_DISMISS
        keyCode == KeyEvent.KEYCODE_1 -> ACTION_SCREEN_1
        keyCode == KeyEvent.KEYCODE_2 -> ACTION_SCREEN_2
        keyCode == KeyEvent.KEYCODE_3 -> ACTION_SCREEN_3
        keyCode == KeyEvent.KEYCODE_4 -> ACTION_SCREEN_4
        keyCode == KeyEvent.KEYCODE_5 -> ACTION_SCREEN_5
        keyCode == KeyEvent.KEYCODE_6 -> ACTION_SCREEN_6
        keyCode == KeyEvent.KEYCODE_7 -> ACTION_SCREEN_7
        keyCode == KeyEvent.KEYCODE_8 -> ACTION_SCREEN_8
        keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET && shiftHeld -> ACTION_TEMPO_UP_10
        keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ACTION_TEMPO_UP
        keyCode == KeyEvent.KEYCODE_TAB && shiftHeld -> ACTION_TAB_PREV
        keyCode == KeyEvent.KEYCODE_TAB -> ACTION_TAB_NEXT
        else -> null
    }
}
