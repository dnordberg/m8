package com.m8droid.protocol

import android.util.Log

/**
 * Parses the binary M8 display protocol stream.
 *
 * The M8 sends frames of draw commands. Each frame contains:
 * - Command byte (type of draw operation)
 * - Payload (varies by command type)
 *
 * This parser processes raw bytes from the WebSocket and calls
 * the appropriate draw methods on the display buffer.
 */
class M8Protocol(private val display: M8DisplayBuffer) {

    companion object {
        private const val TAG = "M8Protocol"

        // Slice header: command byte + length
        const val SLIP_END: Byte = 0xC0.toByte()
        const val SLIP_ESC: Byte = 0xDB.toByte()
        const val SLIP_ESC_END: Byte = 0xDC.toByte()
        const val SLIP_ESC_ESC: Byte = 0xDD.toByte()
    }

    private val frameBuffer = ByteArray(8192)
    private var framePos = 0
    private var inEscape = false

    /**
     * Process raw bytes received from the WebSocket.
     * Data is SLIP-encoded: frames are delimited by 0xC0 bytes.
     */
    fun processBytes(data: ByteArray) {
        for (b in data) {
            when {
                b == SLIP_END -> {
                    if (framePos > 0) {
                        processFrame(frameBuffer, framePos)
                        framePos = 0
                    }
                }
                inEscape -> {
                    inEscape = false
                    val decoded = when (b) {
                        SLIP_ESC_END -> SLIP_END
                        SLIP_ESC_ESC -> SLIP_ESC
                        else -> b
                    }
                    if (framePos < frameBuffer.size) {
                        frameBuffer[framePos++] = decoded
                    }
                }
                b == SLIP_ESC -> {
                    inEscape = true
                }
                else -> {
                    if (framePos < frameBuffer.size) {
                        frameBuffer[framePos++] = b
                    }
                }
            }
        }
    }

    private fun processFrame(data: ByteArray, length: Int) {
        if (length < 1) return

        when (data[0]) {
            M8Commands.DRAW_RECT -> processDrawRect(data, length)
            M8Commands.DRAW_CHAR -> processDrawChar(data, length)
            M8Commands.DRAW_WAVEFORM -> processDrawWaveform(data, length)
            M8Commands.SYSTEM_INFO -> processSystemInfo(data, length)
            M8Commands.DRAW_JOYPAD -> { /* joypad state, ignore for now */ }
            else -> {
                // Unknown command, skip
                Log.d(TAG, "Unknown command: 0x${(data[0].toInt() and 0xFF).toString(16)}, len=$length")
            }
        }
    }

    private fun processDrawRect(data: ByteArray, length: Int) {
        // Format: cmd(1) + x(2) + y(2) + w(2) + h(2) + r(1) + g(1) + b(1) = 12 bytes
        if (length < 12) return
        val x = readU16(data, 1)
        val y = readU16(data, 3)
        val w = readU16(data, 5)
        val h = readU16(data, 7)
        val r = data[9].toInt() and 0xFF
        val g = data[10].toInt() and 0xFF
        val b = data[11].toInt() and 0xFF
        display.drawRect(x, y, w, h, colorFromRGB(r, g, b))
    }

    private fun processDrawChar(data: ByteArray, length: Int) {
        // Format: cmd(1) + x(2) + y(2) + char(1) + fg_r(1) + fg_g(1) + fg_b(1) + bg_r(1) + bg_g(1) + bg_b(1) = 12 bytes
        if (length < 12) return
        val x = readU16(data, 1)
        val y = readU16(data, 3)
        val c = (data[5].toInt() and 0xFF).toChar()
        val fgR = data[6].toInt() and 0xFF
        val fgG = data[7].toInt() and 0xFF
        val fgB = data[8].toInt() and 0xFF
        val bgR = data[9].toInt() and 0xFF
        val bgG = data[10].toInt() and 0xFF
        val bgB = data[11].toInt() and 0xFF
        display.drawChar(c, x, y, colorFromRGB(fgR, fgG, fgB), colorFromRGB(bgR, bgG, bgB))
    }

    private fun processDrawWaveform(data: ByteArray, length: Int) {
        // Format: cmd(1) + x(2) + y(2) + r(1) + g(1) + b(1) + wavedata(N) = 8+ bytes
        if (length < 8) return
        val x = readU16(data, 1)
        val y = readU16(data, 3)
        val r = data[5].toInt() and 0xFF
        val g = data[6].toInt() and 0xFF
        val b = data[7].toInt() and 0xFF
        val waveData = data.copyOfRange(8, length)
        display.drawWaveform(x, y, waveData, colorFromRGB(r, g, b))
    }

    private fun processSystemInfo(data: ByteArray, length: Int) {
        if (length >= 6) {
            val fwMajor = data[1].toInt() and 0xFF
            val fwMinor = data[2].toInt() and 0xFF
            val fwPatch = data[3].toInt() and 0xFF
            Log.i(TAG, "M8 firmware version: $fwMajor.$fwMinor.$fwPatch")
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun colorFromRGB(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
