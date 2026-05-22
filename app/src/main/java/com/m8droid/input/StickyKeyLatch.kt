package com.m8droid.input

import com.m8droid.protocol.M8Commands

/**
 * Phone-friendly latch for M8 modifier buttons.
 *
 * Keeps real M8 semantics intact: the latch only contributes to the key mask
 * that is sent to the emulator. Non-modifier buttons are never latched.
 */
class StickyKeyLatch {
    var mask: Int = 0
        private set

    fun toggle(key: Int): Int {
        if (key and MODIFIER_MASK == 0) return mask
        mask = mask xor (key and MODIFIER_MASK)
        return mask
    }

    fun clear() {
        mask = 0
    }

    fun applyTo(keys: Int): Int = keys or mask

    companion object {
        const val MODIFIER_MASK: Int = M8Commands.KEY_OPTION or M8Commands.KEY_EDIT or M8Commands.KEY_SHIFT
    }
}
