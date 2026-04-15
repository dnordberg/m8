package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.m8droid.protocol.M8Commands

private val BUTTON_GAP = 8.dp
private val SIDE_MARGIN = 8.dp
private val MAX_BUTTON_SIZE = 96.dp
private val LABEL_AREA = 20.dp
// Matches M8 default theme text color (cText = 100,160,220 in M8Emulator).
private val NEON = Color(0xFF64A0DC)

/**
 * On-screen touch controls for M8 buttons.
 * Outlined neon-pink squares with labels/icons placed outside each button.
 */
@Composable
fun M8Controls(
    onKeyStateChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentKeys by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    fun pressKey(key: Int) {
        currentKeys = currentKeys or key
        onKeyStateChanged(currentKeys)
    }

    fun releaseKey(key: Int) {
        currentKeys = currentKeys and key.inv()
        onKeyStateChanged(currentKeys)
    }

    // Physical M8 layout (3 rows × 4 columns), "." = empty cell:
    //   M8   UP   OPT  EDIT
    //   LT   DN   RT   .
    //   .    SH   PL   .
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SIDE_MARGIN, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val byWidth: Dp = (maxWidth - BUTTON_GAP * 3) / 4
        val rowH: Dp = (maxHeight - BUTTON_GAP * 2) / 3
        val byHeight: Dp = rowH - LABEL_AREA
        val cell: Dp = min(min(byWidth, byHeight), MAX_BUTTON_SIZE)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                M8Logo(Modifier.size(cell))
                M8Button("▲", labelAbove = true, M8Commands.KEY_UP, ::pressKey, ::releaseKey, view, cell)
                M8Button("♪ OPTION", labelAbove = false, M8Commands.KEY_OPTION, ::pressKey, ::releaseKey, view, cell)
                M8Button("✱ EDIT", labelAbove = false, M8Commands.KEY_EDIT, ::pressKey, ::releaseKey, view, cell)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                M8Button("◀", labelAbove = false, M8Commands.KEY_LEFT, ::pressKey, ::releaseKey, view, cell)
                M8Button("▼", labelAbove = false, M8Commands.KEY_DOWN, ::pressKey, ::releaseKey, view, cell)
                M8Button("▶", labelAbove = false, M8Commands.KEY_RIGHT, ::pressKey, ::releaseKey, view, cell)
                EmptyCell(cell)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                EmptyCell(cell)
                M8Button("⇡ SHIFT", labelAbove = false, M8Commands.KEY_SHIFT, ::pressKey, ::releaseKey, view, cell)
                M8Button("▶ PLAY", labelAbove = false, M8Commands.KEY_PLAY, ::pressKey, ::releaseKey, view, cell)
                EmptyCell(cell)
            }
        }
    }
}

@Composable
private fun EmptyCell(cell: Dp) {
    Spacer(Modifier.size(width = cell, height = cell + LABEL_AREA))
}

@Composable
private fun M8Logo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(Dp.Unspecified),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "◣◢",
            color = NEON,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "M8",
            color = NEON,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun M8Button(
    label: String,
    labelAbove: Boolean,
    keyBit: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    view: View,
    cell: Dp,
) {
    var pressed by remember { mutableStateOf(false) }

    val button = @Composable {
        Box(
            modifier = Modifier
                .size(cell)
                .border(2.dp, NEON, RoundedCornerShape(6.dp))
                .background(
                    color = if (pressed) NEON else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                )
                .pointerInput(keyBit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPress(keyBit)
                        waitForUpOrCancellation()?.consume()
                        pressed = false
                        onRelease(keyBit)
                    }
                },
        )
    }

    val text = @Composable {
        Box(
            modifier = Modifier
                .width(cell)
                .height(LABEL_AREA),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = NEON,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (labelAbove) {
            text()
            button()
        } else {
            button()
            text()
        }
    }
}
