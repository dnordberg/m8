package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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

private val BUTTON_GAP = 4.dp
private val SIDE_MARGIN = 0.dp
private val MAX_BUTTON_SIZE = 96.dp

/**
 * On-screen touch controls for M8 buttons.
 * Sends key state changes via the callback.
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
    //   .    UP   OPT  EDIT
    //   LT   DN   RT   .
    //   .    SH   PL   .
    //
    // Cells use weight(1f) so the 4-column grid stretches to fill the full
    // width (minus tiny side margins), producing square buttons sized by the
    // container.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SIDE_MARGIN, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val byWidth: Dp = (maxWidth - BUTTON_GAP * 3) / 4
        val byHeight: Dp = (maxHeight - BUTTON_GAP * 2) / 3
        val cell: Dp = min(min(byWidth, byHeight), MAX_BUTTON_SIZE)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                Spacer(Modifier.size(cell))
                M8Button("UP", M8Commands.KEY_UP, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                M8Button("OPT", M8Commands.KEY_OPTION, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                M8Button("EDIT", M8Commands.KEY_EDIT, ::pressKey, ::releaseKey, view, Modifier.size(cell))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                M8Button("LT", M8Commands.KEY_LEFT, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                M8Button("DN", M8Commands.KEY_DOWN, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                M8Button("RT", M8Commands.KEY_RIGHT, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                Spacer(Modifier.size(cell))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                Spacer(Modifier.size(cell))
                M8Button("SHIFT", M8Commands.KEY_SHIFT, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                M8Button("PLAY", M8Commands.KEY_PLAY, ::pressKey, ::releaseKey, view, Modifier.size(cell))
                Spacer(Modifier.size(cell))
            }
        }
    }
}

@Composable
private fun M8Button(
    label: String,
    keyBit: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    view: View,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(
                color = if (pressed) Color(0xFF00FF00) else Color(0xFF333333),
                shape = RoundedCornerShape(8.dp),
            )
            .pointerInput(keyBit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPress(keyBit)
                        tryAwaitRelease()
                        pressed = false
                        onRelease(keyBit)
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color.Black else Color(0xFF00FF00),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
