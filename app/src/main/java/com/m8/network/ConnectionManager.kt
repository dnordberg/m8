package com.m8.network

import android.graphics.Bitmap
import com.m8.protocol.M8DisplayBuffer
import com.m8.protocol.M8Protocol

/**
 * Manages the M8 display buffer and protocol parser.
 * In local emulator mode, the emulator renders SLIP frames directly
 * through the protocol parser to update the display.
 */
class ConnectionManager(
    val display: M8DisplayBuffer = M8DisplayBuffer(),
) {
    constructor(fontBitmap: Bitmap?) : this(M8DisplayBuffer(fontBitmap))

    val protocol = M8Protocol(display)
}
