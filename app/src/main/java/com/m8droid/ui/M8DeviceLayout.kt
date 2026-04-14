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
 * Full-screen M8 device layout. The screen content dominates the vertical
 * space; a fixed 4-column button grid sits at the bottom:
 *
 *     [ M8   UP   OPT  EDIT ]
 *     [ LT   DN   RT    -  ]
 *     [  -   SH   PL    -  ]
 */
@Composable
fun M8DeviceLayout(
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
        // ===== Screen area: fills as much vertical space as possible =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            screenContent()
        }

        // ===== Button area =====
        // M8 logo floats at the left of the area; the 3-column button grid
        // (UP col 0, DOWN col 1, RIGHT col 2) is centered. UP / LEFT / SHIFT
        // all sit in column 0, matching the physical device.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "M8",
                color = Color(0xFFDDDDE4),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )

            // 4-column grid. UP/DOWN/SHIFT all sit in column 1;
            // OPTION/RIGHT/PLAY all sit in column 2; LEFT in col 0; EDIT in col 3.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Row 1: [spacer] | UP | OPTION | EDIT
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
                    DeviceButton(
                        label = null,
                        caption = null,
                        glyph = "\u25B2", // UP
                        keyBit = M8Commands.KEY_UP,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    DeviceButton(
                        label = "OPTION",
                        caption = null,
                        glyph = "\u2315",
                        keyBit = M8Commands.KEY_OPTION,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    DeviceButton(
                        label = "EDIT",
                        caption = null,
                        glyph = "\u2217",
                        keyBit = M8Commands.KEY_EDIT,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                }

                // Row 2: LEFT | DOWN | RIGHT | [spacer]
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    DeviceButton(
                        label = null,
                        caption = null,
                        glyph = "\u25C0", // LEFT
                        keyBit = M8Commands.KEY_LEFT,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    DeviceButton(
                        label = null,
                        caption = null,
                        glyph = "\u25BC", // DOWN
                        keyBit = M8Commands.KEY_DOWN,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    DeviceButton(
                        label = null,
                        caption = null,
                        glyph = "\u25B6", // RIGHT
                        keyBit = M8Commands.KEY_RIGHT,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
                }

                // Row 3: [spacer] | SHIFT | PLAY | [spacer]
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
                    DeviceButton(
                        label = "SHIFT",
                        caption = null,
                        glyph = null,
                        keyBit = M8Commands.KEY_SHIFT,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    DeviceButton(
                        label = "PLAY",
                        caption = null,
                        glyph = "\u25B6",
                        keyBit = M8Commands.KEY_PLAY,
                        view = view,
                        onPress = ::pressKey,
                        onRelease = ::releaseKey,
                    )
                    Spacer(Modifier.size(DEVICE_BUTTON_SIZE))
                }
            }
        }
    }
}

private val DEVICE_BUTTON_SIZE: Dp = 60.dp
private val DEVICE_BUTTON_GAP: Dp = 10.dp

@Composable
private fun DeviceButton(
    label: String?,
    caption: String?,
    glyph: String?,
    keyBit: Int,
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    // Soft neon-blue underglow when pressed, matching the M8 device accent.
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
            if (caption != null) {
                Text(
                    text = caption,
                    color = Color(0xFF666670),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
