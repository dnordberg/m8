package com.m8.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8.protocol.M8Commands

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
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // D-Pad (left side)
        DPad(
            onPress = ::pressKey,
            onRelease = ::releaseKey,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Action buttons (right side)
        ActionButtons(
            onPress = ::pressKey,
            onRelease = ::releaseKey,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DPad(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        M8Button("UP", M8Commands.KEY_UP, onPress, onRelease)
        Row {
            M8Button("LT", M8Commands.KEY_LEFT, onPress, onRelease)
            Spacer(modifier = Modifier.width(48.dp))
            M8Button("RT", M8Commands.KEY_RIGHT, onPress, onRelease)
        }
        M8Button("DN", M8Commands.KEY_DOWN, onPress, onRelease)
    }
}

@Composable
private fun ActionButtons(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            M8Button("OPT", M8Commands.KEY_OPTION, onPress, onRelease)
            M8Button("EDIT", M8Commands.KEY_EDIT, onPress, onRelease)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            M8Button("SHIFT", M8Commands.KEY_SHIFT, onPress, onRelease)
            M8Button("PLAY", M8Commands.KEY_PLAY, onPress, onRelease)
        }
    }
}

@Composable
private fun M8Button(
    label: String,
    keyBit: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(56.dp)
            .background(
                color = if (pressed) Color(0xFF00FF00) else Color(0xFF333333),
                shape = RoundedCornerShape(8.dp),
            )
            .pointerInput(keyBit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
