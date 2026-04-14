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
 * Gamepad-inspired split thumb-zone layout. The M8 screen dominates the top,
 * and the controls split into two clusters at the bottom corners: a 4-way
 * d-pad under the left thumb and a diamond ABXY cluster (OPTION/EDIT/SHIFT/
 * PLAY) under the right thumb. This keeps both thumbs in their natural resting
 * arcs on tall portrait phones and maximises screen real estate for tracker rows.
 */
@Composable
fun M8BestLayout(
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

        // ===== Thumb-zone control area =====
        // Two corner clusters separated by the M8 wordmark.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left thumb cluster: D-pad
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                DPadCluster(view = view, onPress = ::pressKey, onRelease = ::releaseKey)
            }

            // Center: M8 wordmark
            Text(
                text = "M8",
                color = Color(0xFFDDDDE4),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Right thumb cluster: Diamond action buttons
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ActionDiamondCluster(view = view, onPress = ::pressKey, onRelease = ::releaseKey)
            }
        }
    }
}

@Composable
private fun DPadCluster(
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP)) {
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
            BestButton(
                label = null,
                glyph = "\u25B2",
                keyBit = M8Commands.KEY_UP,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = NAV_GLYPH_SIZE,
            )
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP)) {
            BestButton(
                label = null,
                glyph = "\u25C0",
                keyBit = M8Commands.KEY_LEFT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = NAV_GLYPH_SIZE,
            )
            BestButton(
                label = null,
                glyph = "\u25CF",
                keyBit = M8Commands.KEY_DOWN,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = 14.sp,
            )
            BestButton(
                label = null,
                glyph = "\u25B6",
                keyBit = M8Commands.KEY_RIGHT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = NAV_GLYPH_SIZE,
            )
        }
    }
}

@Composable
private fun ActionDiamondCluster(
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    // Diamond layout: OPTION on top, SHIFT left, EDIT right, PLAY bottom.
    // OPTION/EDIT are the most-used "enter values" keys; they sit at the
    // thumb's natural arc peak. PLAY is at the bottom so it's hard to hit
    // accidentally.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP)) {
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
            BestButton(
                label = "OPTION",
                glyph = "\u2315",
                keyBit = M8Commands.KEY_OPTION,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
            )
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP)) {
            BestButton(
                label = "SHIFT",
                glyph = "\u21E7",
                keyBit = M8Commands.KEY_SHIFT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = NAV_GLYPH_SIZE,
            )
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
            BestButton(
                label = "EDIT",
                glyph = "\u2217",
                keyBit = M8Commands.KEY_EDIT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BEST_BUTTON_GAP)) {
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
            BestButton(
                label = "PLAY",
                glyph = "\u25B6",
                keyBit = M8Commands.KEY_PLAY,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                glyphSize = SMALL_PLAY_GLYPH,
            )
            Spacer(Modifier.size(BEST_BUTTON_SIZE))
        }
    }
}

private val BEST_BUTTON_SIZE: Dp = 60.dp
private val BEST_BUTTON_GAP: Dp = 10.dp
private val NAV_GLYPH_SIZE = 24.sp
private val SMALL_PLAY_GLYPH = 11.sp

@Composable
private fun BestButton(
    label: String?,
    glyph: String?,
    keyBit: Int,
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    var pressed by remember { mutableStateOf(false) }

    val baseColor = if (pressed) Color(0xFF1A2740) else Color(0xFF1C1C20)
    val borderColor = if (pressed) Color(0xFF3A6BFF) else Color(0xFF2A2A30)
    val fg = if (pressed) Color(0xFF7FB4FF) else Color(0xFFDDDDE4)

    Box(
        modifier = modifier
            .size(BEST_BUTTON_SIZE)
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
                    fontSize = glyphSize,
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
