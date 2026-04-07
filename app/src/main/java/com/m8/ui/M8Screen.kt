package com.m8.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Renders the M8 display bitmap scaled to fit the available space.
 * The M8 display is 320x240 (4:3 aspect ratio).
 */
@Composable
fun M8Screen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    invalidationTick: Int = 0, // Change this to trigger recomposition
) {
    val imageBitmap = remember(invalidationTick) {
        bitmap.asImageBitmap()
    }

    Canvas(
        modifier = modifier
            .aspectRatio(320f / 240f)
            .fillMaxHeight()
    ) {
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(320, 240),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None, // Pixel-perfect scaling
        )
    }
}
