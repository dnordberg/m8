package com.m8.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8.protocol.M8Commands

private val BUTTON_SIZE = 52.dp
private val BUTTON_GAP = 6.dp

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // D-Pad (left side) — fixed size, not weighted
        DPad(
            onPress = ::pressKey,
            onRelease = ::releaseKey,
            view = view,
        )

        // Action buttons (right side) — fixed size, not weighted
        ActionButtons(
            onPress = ::pressKey,
            onRelease = ::releaseKey,
            view = view,
        )
    }
}

@Composable
private fun DPad(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    view: View,
    modifier: Modifier = Modifier,
) {
    // Use a Box with explicit positioning for a perfect symmetric cross
    val totalSize = BUTTON_SIZE * 3 + BUTTON_GAP * 2
    Box(
        modifier = modifier.size(totalSize),
    ) {
        // UP — top center
        M8Button(
            "UP", M8Commands.KEY_UP, onPress, onRelease, view,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // LEFT — center left
        M8Button(
            "LT", M8Commands.KEY_LEFT, onPress, onRelease, view,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        // RIGHT — center right
        M8Button(
            "RT", M8Commands.KEY_RIGHT, onPress, onRelease, view,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        // DOWN — bottom center
        M8Button(
            "DN", M8Commands.KEY_DOWN, onPress, onRelease, view,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ActionButtons(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    view: View,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            M8Button("OPT", M8Commands.KEY_OPTION, onPress, onRelease, view)
            M8Button("EDIT", M8Commands.KEY_EDIT, onPress, onRelease, view)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            M8Button("SHIFT", M8Commands.KEY_SHIFT, onPress, onRelease, view)
            M8Button("PLAY", M8Commands.KEY_PLAY, onPress, onRelease, view)
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
            .size(BUTTON_SIZE)
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
