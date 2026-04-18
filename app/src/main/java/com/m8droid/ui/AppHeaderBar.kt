package com.m8droid.ui

import androidx.compose.foundation.background
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
fun AppHeaderBar(
    subtitle: String,
    onLoad: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    onAcademy: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(AppTheme.BgPanel)
            .padding(horizontal = AppTheme.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Title group — clean wordmark with optional context subtitle
        Text(
            text = "M8",
            color = AppTheme.AccentGreen,
            fontSize = AppTheme.FontTitle,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(AppTheme.SpaceMd))
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .width(1.dp)
                    .background(AppTheme.BorderHi),
            )
            Spacer(Modifier.width(AppTheme.SpaceMd))
            Text(
                text = subtitle,
                color = AppTheme.TextDim,
                fontSize = AppTheme.FontBody,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        // Action icons — evenly spaced
        HeaderIconButton(onClick = onLoad, tint = AppTheme.TextNormal) { c ->
            AppLoadIcon(tint = c, size = 20.dp)
        }
        Spacer(Modifier.width(AppTheme.SpaceLg))
        HeaderIconButton(onClick = onSettings, tint = AppTheme.TextNormal) { c ->
            AppSystemIcon(tint = c, size = 20.dp)
        }
        Spacer(Modifier.width(AppTheme.SpaceLg))
        HeaderIconButton(onClick = onHelp, tint = AppTheme.TextNormal) { c ->
            AppHelpIcon(tint = c, size = 20.dp)
        }
        if (onAcademy != null) {
            Spacer(Modifier.width(AppTheme.SpaceLg))
            HeaderIconButton(onClick = onAcademy, tint = AppTheme.AccentMagenta) { c ->
                Text(
                    text = "♟",
                    color = c,
                    fontSize = AppTheme.FontHeading,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (onClose != null) {
            Spacer(Modifier.width(AppTheme.SpaceLg))
            HeaderIconButton(onClick = onClose, tint = AppTheme.TextDim) { c ->
                Text(
                    text = "✕",
                    color = c,
                    fontSize = AppTheme.FontHeading,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
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
            .clip(RoundedCornerShape(AppTheme.CornerSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}