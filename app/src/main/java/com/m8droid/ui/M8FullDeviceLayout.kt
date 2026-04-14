package com.m8droid.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * Skeuomorphic "full device" M8 layout. The entire phone screen becomes a
 * faux anthracite chassis with a recessed 4:3 OLED bezel up top, an "M8"
 * wordmark, a rounded d-pad bottom-left, and a circular action cluster
 * (OPTION / EDIT / SHIFT / PLAY) bottom-right, plus corner screws.
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

    // Chassis gradient: top highlight -> mid anthracite -> deeper bottom.
    val chassisBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2A2A30),
            Color(0xFF1A1A1F),
            Color(0xFF111114),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050506))
            .padding(10.dp)
            .shadow(12.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(chassisBrush)
            .border(1.dp, Color(0xFF3A3A42), RoundedCornerShape(26.dp)),
    ) {
        // Corner screws
        Screw(Modifier.align(Alignment.TopStart).padding(10.dp))
        Screw(Modifier.align(Alignment.TopEnd).padding(10.dp))
        Screw(Modifier.align(Alignment.BottomStart).padding(10.dp))
        Screw(Modifier.align(Alignment.BottomEnd).padding(10.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ===== Recessed screen bezel =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    // outer dark rim (bottom shadow line)
                    .background(Color(0xFF050507), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3C3C44), RoundedCornerShape(10.dp))
                    .padding(6.dp)
                    // inner bezel recess
                    .background(Color(0xFF000000), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF0E0E12), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(320f / 240f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    screenContent()
                }
            }

            // ===== M8 wordmark row with subtle PCB accent lines =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFF2A2A32)),
                )
                Text(
                    text = "M8",
                    color = Color(0xFF9A9AA4),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFF2A2A32)),
                )
            }

            Spacer(Modifier.weight(1f))

            // ===== Button cluster: d-pad left, action cluster right =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                DPad(
                    view = view,
                    onPress = ::pressKey,
                    onRelease = ::releaseKey,
                )

                ActionCluster(
                    view = view,
                    onPress = ::pressKey,
                    onRelease = ::releaseKey,
                )
            }
        }
    }
}

// ===================================================================
// D-pad
// ===================================================================

private val DPAD_SIZE: Dp = 150.dp
private val DPAD_BTN: Dp = 46.dp

@Composable
private fun DPad(
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DPAD_SIZE)
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF232328), Color(0xFF14141A)),
                ),
                RoundedCornerShape(22.dp),
            )
            .border(1.dp, Color(0xFF3A3A44), RoundedCornerShape(22.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // UP
        ChassisButton(
            glyph = "\u25B2",
            keyBit = M8Commands.KEY_UP,
            view = view,
            onPress = onPress,
            onRelease = onRelease,
            size = DPAD_BTN,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // LEFT
        ChassisButton(
            glyph = "\u25C0",
            keyBit = M8Commands.KEY_LEFT,
            view = view,
            onPress = onPress,
            onRelease = onRelease,
            size = DPAD_BTN,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        // RIGHT
        ChassisButton(
            glyph = "\u25B6",
            keyBit = M8Commands.KEY_RIGHT,
            view = view,
            onPress = onPress,
            onRelease = onRelease,
            size = DPAD_BTN,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        // DOWN
        ChassisButton(
            glyph = "\u25BC",
            keyBit = M8Commands.KEY_DOWN,
            view = view,
            onPress = onPress,
            onRelease = onRelease,
            size = DPAD_BTN,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // Center dot - decorative only, not pressable
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFF3A6BFF).copy(alpha = 0.45f), CircleShape)
                .border(1.dp, Color(0xFF1A2740), CircleShape),
        )
    }
}

// ===================================================================
// Action cluster (OPTION / EDIT / SHIFT / PLAY)
// ===================================================================

private val ACTION_BIG: Dp = 56.dp
private val ACTION_SMALL: Dp = 46.dp // matches DPAD_BTN so SHIFT lines up with the arrows

@Composable
private fun ActionCluster(
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Big action row: OPTION + EDIT
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChassisButton(
                glyph = "\u2315", // OPTION
                label = "OPT",
                keyBit = M8Commands.KEY_OPTION,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                size = ACTION_BIG,
                glyphSize = 22.sp,
            )
            ChassisButton(
                glyph = "\u2217", // EDIT
                label = "EDIT",
                keyBit = M8Commands.KEY_EDIT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                size = ACTION_BIG,
                glyphSize = 22.sp,
            )
        }
        // Small transport row: SHIFT + PLAY
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChassisButton(
                glyph = "\u25BC",
                label = "SHIFT",
                keyBit = M8Commands.KEY_SHIFT,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                size = ACTION_SMALL,
                glyphSize = 18.sp,
            )
            ChassisButton(
                glyph = "\u25B6",
                label = "PLAY",
                keyBit = M8Commands.KEY_PLAY,
                view = view,
                onPress = onPress,
                onRelease = onRelease,
                size = ACTION_SMALL,
                glyphSize = 14.sp,
            )
        }
    }
}

// ===================================================================
// Circular chassis button
// ===================================================================

@Composable
private fun ChassisButton(
    glyph: String,
    keyBit: Int,
    view: View,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    glyphSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    var pressed by remember { mutableStateOf(false) }

    val baseBrush = if (pressed) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1A2740), Color(0xFF0E1828)),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF27272E), Color(0xFF16161B)),
        )
    }
    val borderColor = if (pressed) Color(0xFF3A6BFF) else Color(0xFF3A3A44)
    val fg = if (pressed) Color(0xFF7FB4FF) else Color(0xFFDDDDE4)
    val labelColor = if (pressed) Color(0xFF7FB4FF) else Color(0xFF8A8A94)

    Box(
        modifier = modifier
            .size(size)
            .shadow(if (pressed) 2.dp else 5.dp, CircleShape)
            .background(baseBrush, CircleShape)
            .border(1.dp, borderColor, CircleShape)
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
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = glyph,
                color = fg,
                fontSize = glyphSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (label != null) {
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ===================================================================
// Decorative corner screw
// ===================================================================

@Composable
private fun Screw(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .shadow(1.dp, CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF3A3A42), Color(0xFF0C0C10)),
                ),
                CircleShape,
            )
            .border(1.dp, Color(0xFF0A0A0C), CircleShape),
    ) {
        // Slot highlight
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 6.dp, height = 1.dp)
                .background(Color(0xFF56565E)),
        )
    }
}
