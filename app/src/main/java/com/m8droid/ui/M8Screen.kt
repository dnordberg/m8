package com.m8droid.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Renders the M8 display bitmap scaled to fit the available space.
 * The M8 display is 320x240 (4:3 aspect ratio).
 *
 * Taps on the top header strip (the screen tab row) are detected and
 * mapped to screen indices (0-7) via [onScreenTap]. Each tab occupies
 * 40px of the 320px-wide display; only taps in the top ~12px (scaled)
 * trigger navigation.
 */
@Composable
fun M8Screen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    invalidationTick: Int = 0,
    onScreenTap: ((Int) -> Unit)? = null,
) {
    val imageBitmap = remember(invalidationTick) {
        bitmap.asImageBitmap()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(320f / 240f)
            .then(
                if (onScreenTap != null) {
                    Modifier.pointerInput(onScreenTap) {
                        detectTapGestures { offset ->
                            // Map tap position to M8 display coordinates
                            val m8X = (offset.x / size.width * 320f).toInt()
                            val m8Y = (offset.y / size.height * 240f).toInt()
                            // Header strip is the top 12 pixels, 8 tabs × 40px each
                            if (m8Y < 14) {
                                val tabIndex = (m8X / 40).coerceIn(0, 7)
                                onScreenTap(tabIndex)
                            }
                        }
                    }
                } else Modifier
            )
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
