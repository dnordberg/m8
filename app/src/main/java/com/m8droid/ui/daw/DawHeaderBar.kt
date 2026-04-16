package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared cyberpunk header bar used by both the DAW shell and the classic M8
 * device view. Contains the title, a free-form subtitle slot, and a row of
 * action chips (load / settings / help) plus the mode toggle.
 *
 * Consolidating both modes' chrome under one header keeps the top-of-screen
 * controls identical regardless of which body view is active.
 */
@Composable
fun DawHeaderBar(
    subtitle: String,
    isDawMode: Boolean,
    onToggleMode: () -> Unit,
    onLoad: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    onAcademy: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DawTheme.BgPanel)
            .border(1.dp, DawTheme.BorderDim)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ">_",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(DawTheme.SpaceSm))
        Column {
            Text(
                text = "TRACKER_OS_V1",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = DawTheme.TextDim,
                    fontSize = DawTheme.FontLabel,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        HeaderIconButton(onClick = onLoad, tint = DawTheme.AccentCyan) { c ->
            DawLoadIcon(tint = c, size = 18.dp)
        }
        Spacer(Modifier.width(DawTheme.SpaceSm))
        HeaderIconButton(onClick = onSettings, tint = DawTheme.AccentCyan) { c ->
            DawSystemIcon(tint = c, size = 18.dp)
        }
        Spacer(Modifier.width(DawTheme.SpaceSm))
        HeaderIconButton(onClick = onHelp, tint = DawTheme.AccentCyan) { c ->
            DawHelpIcon(tint = c, size = 18.dp)
        }
        if (onAcademy != null) {
            Spacer(Modifier.width(DawTheme.SpaceSm))
            HeaderIconButton(onClick = onAcademy, tint = Color(0xFFFF00FF)) { c ->
                Text(
                    text = "♟",
                    color = c,
                    fontSize = DawTheme.FontHeading,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.width(DawTheme.SpaceMd))
        ModeToggleChip(isDawMode = isDawMode, onClick = onToggleMode)
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    tint: Color,
    content: @Composable (Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

@Composable
private fun ModeToggleChip(isDawMode: Boolean, onClick: () -> Unit) {
    val label = if (isDawMode) "M8 CLASSIC" else "DAW"
    val border = if (isDawMode) DawTheme.AccentMagenta else DawTheme.AccentGreen
    val fg = if (isDawMode) DawTheme.AccentMagenta else DawTheme.AccentGreen
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCardHi)
            .border(1.dp, border, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceXs),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
