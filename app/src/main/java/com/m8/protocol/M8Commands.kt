package com.m8.protocol

/**
 * M8 serial protocol command constants.
 * Based on M8WebDisplay js/serial.js
 */
object M8Commands {
    // Commands TO M8
    const val CMD_KEY_STATE: Byte = 0x43
    const val CMD_DISCONNECT: Byte = 0x44
    const val CMD_ENABLE_DISPLAY: Byte = 0x45
    const val CMD_RESET_DISPLAY: Byte = 0x52
    const val CMD_MIDI_NOTE: Byte = 0x4B

    // Key bitmask bits
    const val KEY_UP: Int = 0x01
    const val KEY_DOWN: Int = 0x02
    const val KEY_LEFT: Int = 0x04
    const val KEY_RIGHT: Int = 0x08
    const val KEY_OPTION: Int = 0x10
    const val KEY_EDIT: Int = 0x20
    const val KEY_SHIFT: Int = 0x40
    const val KEY_PLAY: Int = 0x80

    // Commands FROM M8 (display data types)
    const val DRAW_RECT: Byte = 0xFE.toByte()
    const val DRAW_CHAR: Byte = 0xFD.toByte()
    const val DRAW_WAVEFORM: Byte = 0xFC.toByte()
    const val DRAW_JOYPAD: Byte = 0xFB.toByte()
    const val SYSTEM_INFO: Byte = 0xFF.toByte()

    fun enableAndResetDisplay(): ByteArray =
        byteArrayOf(CMD_ENABLE_DISPLAY, CMD_RESET_DISPLAY)

    fun disconnect(): ByteArray =
        byteArrayOf(CMD_DISCONNECT)

    fun keyState(keys: Int): ByteArray =
        byteArrayOf(CMD_KEY_STATE, keys.toByte())
}
