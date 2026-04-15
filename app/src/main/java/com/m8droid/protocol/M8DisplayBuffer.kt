package com.m8droid.protocol

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect

/**
 * Manages the M8 display pixel buffer (320x240).
 * Receives draw commands and renders to a Bitmap.
 *
 * Uses a bitmap sprite sheet font (8x10 per character, 16 columns x 8 rows = 128 ASCII chars).
 * Characters are tinted with the foreground color and drawn over a background rectangle.
 *
 * Draw methods are synchronized to prevent concurrent modification
 * from the protocol thread while the UI thread reads via snapshot().
 */
class M8DisplayBuffer(
    private val fontBitmap: Bitmap? = null
) {
    companion object {
        const val WIDTH = 320
        const val HEIGHT = 240

        // Font sprite sheet layout
        private const val FONT_CHAR_WIDTH = 8
        private const val FONT_CHAR_HEIGHT = 10
        private const val FONT_COLS = 16   // chars per row in sprite sheet
    }

    private val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }

    // M8 uses a small built-in font, 8x10 monospace
    private val charWidth = 8
    private val charHeight = 10

    // Reusable Rect objects to avoid allocation in draw loops
    private val srcRect = Rect()
    private val dstRect = Rect()

    // Scratch bitmap for tinting individual characters
    private val charBitmap: Bitmap? = if (fontBitmap != null) {
        Bitmap.createBitmap(FONT_CHAR_WIDTH, FONT_CHAR_HEIGHT, Bitmap.Config.ARGB_8888)
    } else null
    private val charCanvas: Canvas? = if (charBitmap != null) Canvas(charBitmap!!) else null

    // Double-buffered snapshot pool: snapshot() copies into one of these rather
    // than allocating a fresh bitmap each frame. Two buffers let the UI thread
    // read the previous snapshot while the next frame's snapshot is being written.
    private val snapshotBitmaps = arrayOf(
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888),
    )
    private val snapshotCanvases = arrayOf(
        Canvas(snapshotBitmaps[0]),
        Canvas(snapshotBitmaps[1]),
    )
    private var snapshotIndex = 0

    @Synchronized
    fun clear() {
        canvas.drawColor(Color.BLACK)
    }

    @Synchronized
    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.colorFilter = null
        canvas.drawRect(
            x.toFloat(), y.toFloat(),
            (x + w).toFloat(), (y + h).toFloat(),
            paint
        )
    }

    @Synchronized
    fun drawChar(c: Char, x: Int, y: Int, fgColor: Int, bgColor: Int) {
        // Draw background
        paint.color = bgColor
        paint.style = Paint.Style.FILL
        paint.colorFilter = null
        canvas.drawRect(
            x.toFloat(), y.toFloat(),
            (x + charWidth).toFloat(), (y + charHeight).toFloat(),
            paint
        )

        val code = c.code
        if (code < 0 || code > 127) return
        if (code == 32) return  // Space - just background

        if (fontBitmap != null && charCanvas != null && charBitmap != null) {
            // Bitmap font rendering: blit from sprite sheet with color tint
            val srcX = (code % FONT_COLS) * FONT_CHAR_WIDTH
            val srcY = (code / FONT_COLS) * FONT_CHAR_HEIGHT

            srcRect.set(srcX, srcY, srcX + FONT_CHAR_WIDTH, srcY + FONT_CHAR_HEIGHT)
            dstRect.set(x, y, x + FONT_CHAR_WIDTH, y + FONT_CHAR_HEIGHT)

            // Tint the white source pixels to the foreground color
            paint.colorFilter = PorterDuffColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL

            // Draw the character glyph from the font sprite sheet directly with tint
            // We use SRC_IN which keeps the alpha of the source and applies the color
            canvas.drawBitmap(fontBitmap, srcRect, dstRect, paint)
            paint.colorFilter = null
        } else {
            // Fallback: draw text (original behavior)
            paint.color = fgColor
            paint.style = Paint.Style.FILL
            paint.textSize = charHeight.toFloat()
            canvas.drawText(c.toString(), x.toFloat(), (y + charHeight - 1).toFloat(), paint)
        }
    }

    @Synchronized
    fun drawWaveform(x: Int, y: Int, waveData: ByteArray, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.colorFilter = null
        for (i in 0 until waveData.size - 1) {
            val y1 = y + (waveData[i].toInt() and 0xFF)
            val y2 = y + (waveData[i + 1].toInt() and 0xFF)
            canvas.drawLine(
                (x + i).toFloat(), y1.toFloat(),
                (x + i + 1).toFloat(), y2.toFloat(),
                paint
            )
        }
    }

    /**
     * Returns a thread-safe copy of the current display bitmap.
     * Call this from the UI/Compose thread to avoid tearing artifacts
     * from concurrent protocol-thread mutations.
     */
    @Synchronized
    fun snapshot(): Bitmap {
        val idx = snapshotIndex
        snapshotIndex = (idx + 1) and 1
        snapshotCanvases[idx].drawBitmap(bitmap, 0f, 0f, null)
        return snapshotBitmaps[idx]
    }
}
