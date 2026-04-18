package com.m8droid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared neon/cyberpunk visual tokens for the app.
 */
object AppTheme {
    // Backgrounds — near-black with a faint purple tint
    val BgRoot = Color(0xFF08070C)
    val BgPanel = Color(0xFF0E0D14)
    val BgCard = Color(0xFF14131C)
    val BgCardHi = Color(0xFF1E1C2A)

    // Borders
    val BorderDim = Color(0xFF2A2738)
    val BorderHi = Color(0xFF5A5488)

    // Accents — neon green primary, magenta secondary, cyan tertiary
    val AccentGreen = Color(0xFF00FF7A)
    val AccentGreenDim = Color(0xFF16894A)
    val AccentMagenta = Color(0xFFFF2EC8)
    val AccentCyan = Color(0xFF00D4FF)
    val AccentYellow = Color(0xFFFFE14F)
    val AccentRed = Color(0xFFFF3860)

    // Text
    val TextBright = Color(0xFFE8E5F2)
    val TextNormal = Color(0xFFB4AED0)
    val TextDim = Color(0xFF6B6684)
    val TextLabel = Color(0xFFFF2EC8) // uppercase section labels

    // Typography sizes
    val FontTitle = 18.sp
    val FontHeading = 14.sp
    val FontLabel = 10.sp
    val FontBody = 12.sp
    val FontMono = 11.sp

    // Spacing
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp

    val CornerSm = 4.dp
    val CornerMd = 8.dp
    val CornerLg = 12.dp
}
