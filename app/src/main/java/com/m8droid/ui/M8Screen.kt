package com.m8droid.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
 * Touch navigation:
 * - Tap the top quarter of the display to switch screens (mapped to
 *   8 tab zones across the width).
 * - Swipe left/right anywhere on the display to go to next/prev screen.
 */
@Composable
fun M8Screen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    invalidationTick: Int = 0,
    onScreenTap: ((Int) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
) {
    val imageBitmap = remember(invalidationTick) {
        bitmap.asImageBitmap()
    }

    var swipeDelta by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(320f / 240f)
            .then(
                if (onScreenTap != null) {
                    Modifier.pointerInput(onScreenTap) {
                        detectTapGestures { offset ->
                            val m8X = (offset.x / size.width * 320f).toInt()
                            val m8Y = (offset.y / size.height * 240f).toInt()
                            // Top quarter of display = tap zone for tabs
                            if (m8Y < 60) {
                                val tabIndex = (m8X / 40).coerceIn(0, 7)
                                onScreenTap(tabIndex)
                            }
                        }
                    }
                } else Modifier
            )
            .then(
                if (onSwipeLeft != null || onSwipeRight != null) {
                    Modifier.pointerInput(onSwipeLeft, onSwipeRight) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeDelta = 0f },
                            onDragEnd = {
                                if (swipeDelta < -80f) onSwipeLeft?.invoke()
                                else if (swipeDelta > 80f) onSwipeRight?.invoke()
                                swipeDelta = 0f
                            },
                            onDragCancel = { swipeDelta = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                swipeDelta += dragAmount
                            },
                        )
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
