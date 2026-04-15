package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8droid.protocol.M8Commands

/**
 * "Best" layout: screen fills available space, M8Controls sits below.
 */
@Composable
fun M8BestLayout(
    onKeyStateChanged: (Int) -> Unit,
    screenContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            screenContent()
        }
        M8Controls(
            onKeyStateChanged = onKeyStateChanged,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
        )
    }
}

/**
 * Full-screen M8 device layout. The screen content dominates the vertical
 * space; a fixed 4-column button grid sits at the bottom:
 *
 *     [ M8   UP   OPT  EDIT ]
 *     [ LT   DN   RT    -  ]
 *     [  -   SH   PL    -  ]
 */
@Composable
fun M8FullDeviceLayout(
    onKeyStateChanged: (Int) -> Unit,
    screenContent: @Composable () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            screenContent()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                Box(
                    modifier = Modifier.size(DEVICE_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "M8",
                        color = Color(0xFFDDDDE4),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                DeviceButton(null, "\u25B2", M8Commands.KEY_UP, view, ::pressKey, ::releaseKey)
                DeviceButton("OPTION", "\u2315", M8Commands.KEY_OPTION, view, ::pressKey, ::releaseKey)
                DeviceButton("EDIT", "\u2217", M8Commands.KEY_EDIT, view, ::pressKey, ::releaseKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                DeviceButton(null, "\u25C0", M8Commands.KEY_LEFT, view, ::pressKey, ::releaseKey)
                DeviceButton(null, "\u25BC", M8Commands.KEY_DOWN, view, ::pressKey, ::releaseKey)
                DeviceButton(null, "\u25B6", M8Commands.KEY_RIGHT, view, ::pressKey, ::releaseKey)
                Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
                DeviceButton("SHIFT", null, M8Commands.KEY_SHIFT, view, ::pressKey, ::releaseKey)
                DeviceButton("PLAY", "\u25B6", M8Commands.KEY_PLAY, view, ::pressKey, ::releaseKey)
                Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
            }
        }
    }
}

private val DEVICE_BUTTON_SIZE: Dp = 60.dp
private val DEVICE_BUTTON_GAP: Dp = 10.dp

@Composable
private fun DeviceButton(
    label: String?,
    glyph: String?,
    keyBit: Int,
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val baseColor = if (pressed) Color(0xFF1A2740) else Color(0xFF1C1C20)
    val borderColor = if (pressed) Color(0xFF3A6BFF) else Color(0xFF2A2A30)
    val fg = if (pressed) Color(0xFF7FB4FF) else Color(0xFFDDDDE4)

    Box(
        modifier = modifier
            .size(DEVICE_BUTTON_SIZE)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(baseColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(keyBit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPress(keyBit)
                        tryAwaitRelease()
                        pressed = false
                        onRelease(keyBit)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (glyph != null) {
                Text(
                    text = glyph,
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    color = if (pressed) Color(0xFF7FB4FF) else Color(0xFF9999A2),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
