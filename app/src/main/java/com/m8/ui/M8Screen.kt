package com.m8.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Renders the M8 display bitmap scaled to fit the available space.
 * The M8 display is 320x240 (4:3 aspect ratio).
 *
 * Uses fillMaxWidth so the display stretches to the full phone width,
 * then aspect ratio determines height. This prevents cropping on
 * portrait phones where height > width.
 */
@Composable
fun M8Screen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    invalidationTick: Int = 0,
) {
    val imageBitmap = remember(invalidationTick) {
        bitmap.asImageBitmap()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(320f / 240f)
    ) {
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(320, 240),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}
