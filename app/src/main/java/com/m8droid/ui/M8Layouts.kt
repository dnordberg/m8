package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.m8droid.protocol.M8Commands

/**
 * "Best" layout: screen fills available space, M8Controls sits below.
 */
object M8MainLayout {
    const val centersDisplayAndControls: Boolean = false
    const val controlsAnchor: String = "Top"
    const val quickActionTopFraction: Float = 0.42f
    // Restored: the pills latch OPT/EDIT/SHIFT into the key mask and sit on top
    // of direct tap-to-edit instead of replacing it.
    const val showsStickyModifierBar: Boolean = true
    // The control section is no longer capped: it takes its share of the height
    // and the buttons expand to fill it, width and height.
    const val displayWeight: Float = 1.2f
    const val controlsWeight: Float = 1f
}

@Composable
fun M8BestLayout(
    onKeyStateChanged: (Int) -> Unit,
    screenContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    externalKeyMask: Int = 0,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(M8MainLayout.displayWeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            screenContent()
        }
        M8Controls(
            onKeyStateChanged = onKeyStateChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(M8MainLayout.controlsWeight)
                .padding(bottom = 8.dp),
            externalKeyMask = externalKeyMask,
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
                .weight(M8MainLayout.displayWeight),
            contentAlignment = Alignment.Center,
        ) {
            screenContent()
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(M8MainLayout.controlsWeight),
            contentAlignment = Alignment.Center,
        ) {
            // Buttons fill the whole bottom section: four across, three down.
            val cell: Dp = min(
                (maxWidth - DEVICE_BUTTON_GAP * 3) / 4,
                (maxHeight - DEVICE_BUTTON_GAP * 2) / 3,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    Box(
                        modifier = Modifier.size(cell),
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
                    DeviceButton(null, "\u25B2", M8Commands.KEY_UP, view, ::pressKey, ::releaseKey, displayMask, cell)
                    DeviceButton("OPTION", "\u2315", M8Commands.KEY_OPTION, view, ::pressKey, ::releaseKey, displayMask, cell)
                    DeviceButton("EDIT", "\u2217", M8Commands.KEY_EDIT, view, ::pressKey, ::releaseKey, displayMask, cell)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    DeviceButton(null, "\u25C0", M8Commands.KEY_LEFT, view, ::pressKey, ::releaseKey, displayMask, cell)
                    DeviceButton(null, "\u25BC", M8Commands.KEY_DOWN, view, ::pressKey, ::releaseKey, displayMask, cell)
                    DeviceButton(null, "\u25B6", M8Commands.KEY_RIGHT, view, ::pressKey, ::releaseKey, displayMask, cell)
                    Spacer(Modifier.size(cell))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DEVICE_BUTTON_GAP)) {
                    Spacer(Modifier.size(cell))
                    DeviceButton("SHIFT", null, M8Commands.KEY_SHIFT, view, ::pressKey, ::releaseKey, displayMask, cell)
                    DeviceButton("PLAY", "\u25B6", M8Commands.KEY_PLAY, view, ::pressKey, ::releaseKey, displayMask, cell)
                    Spacer(Modifier.size(cell))
                }
            }
        }
    }
}

private val DEVICE_BUTTON_GAP: Dp = 6.dp

@Composable
private fun DeviceButton(
    label: String?,
    glyph: String?,
    keyBit: Int,
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    keyMask: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val pressed = (keyMask and keyBit) != 0
    val baseColor = if (pressed) Color(0xFF1A2740) else Color(0xFF1C1C20)
    val borderColor = if (pressed) Color(0xFF3A6BFF) else Color(0xFF2A2A30)
    val fg = if (pressed) Color(0xFF7FB4FF) else Color(0xFFDDDDE4)

    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(baseColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
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

// NOTE: there was previously a ScreenTabBar composable here that added a
// tappable tab row above the emulator display. This is NOT a real M8 firmware
// element and has been removed — view navigation on the real M8 is SHIFT+arrow
// only. Do not re-add a tab bar to the top of the UI without explicit user
// approval.
