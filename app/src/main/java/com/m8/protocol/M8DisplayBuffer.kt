package com.m8.protocol

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Manages the M8 display pixel buffer (320x240).
 * Receives draw commands and renders to a Bitmap.
 */
class M8DisplayBuffer {
    companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
    }

    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint().apply {
        isAntiAlias = false
        typeface = Typeface.MONOSPACE
    }

    // M8 uses a small built-in font, approximate with 8x10 monospace
    private val charWidth = 8
    private val charHeight = 10

    fun clear() {
        canvas.drawColor(Color.BLACK)
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(
            x.toFloat(), y.toFloat(),
            (x + w).toFloat(), (y + h).toFloat(),
            paint
        )
    }

    fun drawChar(c: Char, x: Int, y: Int, fgColor: Int, bgColor: Int) {
        // Draw background
        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(
            x.toFloat(), y.toFloat(),
            (x + charWidth).toFloat(), (y + charHeight).toFloat(),
            paint
        )
        // Draw character
        paint.color = fgColor
        paint.style = Paint.Style.FILL
        paint.textSize = charHeight.toFloat()
        canvas.drawText(c.toString(), x.toFloat(), (y + charHeight - 1).toFloat(), paint)
    }

    fun drawWaveform(x: Int, y: Int, waveData: ByteArray, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
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
}
