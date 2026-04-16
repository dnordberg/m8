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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
            .height(44.dp)
            .background(DawTheme.BgPanel)
            .padding(horizontal = DawTheme.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Title group — clean wordmark with optional context subtitle
        Text(
            text = "M8",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(DawTheme.SpaceMd))
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .width(1.dp)
                    .background(DawTheme.BorderHi),
            )
            Spacer(Modifier.width(DawTheme.SpaceMd))
            Text(
                text = subtitle,
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontBody,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        // Action icons — evenly spaced, no boxes around them
        HeaderIconButton(onClick = onLoad, tint = DawTheme.TextNormal) { c ->
            DawLoadIcon(tint = c, size = 20.dp)
        }
        Spacer(Modifier.width(DawTheme.SpaceLg))
        HeaderIconButton(onClick = onSettings, tint = DawTheme.TextNormal) { c ->
            DawSystemIcon(tint = c, size = 20.dp)
        }
        Spacer(Modifier.width(DawTheme.SpaceLg))
        HeaderIconButton(onClick = onHelp, tint = DawTheme.TextNormal) { c ->
            DawHelpIcon(tint = c, size = 20.dp)
        }
        if (onAcademy != null) {
            Spacer(Modifier.width(DawTheme.SpaceLg))
            HeaderIconButton(onClick = onAcademy, tint = DawTheme.AccentMagenta) { c ->
                Text(
                    text = "♟",
                    color = c,
                    fontSize = DawTheme.FontHeading,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.width(DawTheme.SpaceXl))
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
            .size(36.dp)
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

@Composable
private fun ModeToggleChip(isDawMode: Boolean, onClick: () -> Unit) {
    val label = if (isDawMode) "CLASSIC" else "DAW"
    val accent = if (isDawMode) DawTheme.AccentMagenta else DawTheme.AccentGreen
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .border(1.dp, accent, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceXs),
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}
