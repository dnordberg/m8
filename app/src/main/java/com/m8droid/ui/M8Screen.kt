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
 * - Tap a rendered field to select it and open its contextual editor.
 * - Long-press provides the same direct-edit behavior.
 * - Swipe left/right anywhere on the display to go to next/prev screen.
 */
@Composable
fun M8Screen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    invalidationTick: Int = 0,
    onDisplayTap: ((Int, Int) -> Unit)? = null,
    onDisplayLongPress: ((Int, Int) -> Unit)? = null,
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
                if (onDisplayTap != null || onDisplayLongPress != null) {
                    Modifier.pointerInput(onDisplayTap, onDisplayLongPress) {
                        fun toM8Coordinates(offset: androidx.compose.ui.geometry.Offset): Pair<Int, Int> =
                            (offset.x / size.width * 320f).toInt() to (offset.y / size.height * 240f).toInt()

                        detectTapGestures(
                            onLongPress = { offset ->
                                val (m8X, m8Y) = toM8Coordinates(offset)
                                onDisplayLongPress?.invoke(m8X, m8Y)
                            },
                            onTap = { offset ->
                                val (m8X, m8Y) = toM8Coordinates(offset)
                                onDisplayTap?.invoke(m8X, m8Y)
                            },
                        )
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
