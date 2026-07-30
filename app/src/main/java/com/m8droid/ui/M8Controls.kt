package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

private val BUTTON_GAP = 6.dp
private val SIDE_MARGIN = 8.dp
private val MAX_BUTTON_SIZE = M8MainLayout.maxControlButtonDp.dp
private val LABEL_AREA = 16.dp
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
    externalKeyMask: Int = 0,
) {
    var currentKeys by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val displayMask = currentKeys or externalKeyMask

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
            .padding(start = SIDE_MARGIN, end = SIDE_MARGIN, top = 0.dp, bottom = 8.dp),
        contentAlignment = Alignment.TopCenter,
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
                M8Button("▲", labelAbove = true, M8Commands.KEY_UP, ::pressKey, ::releaseKey, view, cell, displayMask)
                M8Button("♪ OPTION", labelAbove = false, M8Commands.KEY_OPTION, ::pressKey, ::releaseKey, view, cell, displayMask)
                M8Button("✱ EDIT", labelAbove = false, M8Commands.KEY_EDIT, ::pressKey, ::releaseKey, view, cell, displayMask)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                M8Button("◀", labelAbove = false, M8Commands.KEY_LEFT, ::pressKey, ::releaseKey, view, cell, displayMask)
                M8Button("▼", labelAbove = false, M8Commands.KEY_DOWN, ::pressKey, ::releaseKey, view, cell, displayMask)
                M8Button("▶", labelAbove = false, M8Commands.KEY_RIGHT, ::pressKey, ::releaseKey, view, cell, displayMask)
                EmptyCell(cell)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                EmptyCell(cell)
                M8Button("⇡ SHIFT", labelAbove = false, M8Commands.KEY_SHIFT, ::pressKey, ::releaseKey, view, cell, displayMask)
                M8Button("▶ PLAY", labelAbove = false, M8Commands.KEY_PLAY, ::pressKey, ::releaseKey, view, cell, displayMask)
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "M8",
            color = NEON,
            fontSize = 20.sp,
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
    keyMask: Int,
) {
    val pressed = (keyMask and keyBit) != 0

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
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPress(keyBit)
                        waitForUpOrCancellation()?.consume()
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
                fontSize = 9.sp,
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

@Composable
fun TrackerQuickActionBar(
    status: String,
    onInsert: () -> Unit,
    onClear: () -> Unit,
    onDuplicate: () -> Unit,
    onTransposeDown: () -> Unit,
    onTransposeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, NEON, RoundedCornerShape(8.dp))
            .background(Color(0xDD020812), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = status,
            color = NEON,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TrackerQuickButton("INS", onInsert)
            TrackerQuickButton("CLR", onClear)
            TrackerQuickButton("DUP", onDuplicate)
            TrackerQuickButton("-", onTransposeDown)
            TrackerQuickButton("+", onTransposeUp)
        }
    }
}

@Composable
private fun TrackerQuickButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 38.dp)
            .border(1.dp, NEON, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NEON,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}
