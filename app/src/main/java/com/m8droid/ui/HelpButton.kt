package com.m8droid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8droid.ui.academy.AcademyTheme

@Composable
fun HelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(Color(0xCC0A0A1A), CircleShape)
            .border(1.dp, Color(0xFF00FF00), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = Color(0xFF00FF00),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun HelpMenu(
    onDismiss: () -> Unit,
    onStartTutorial: () -> Unit,
    onShowHotkeys: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(AcademyTheme.BgRoot, RoundedCornerShape(8.dp))
                .border(1.dp, AcademyTheme.BorderDim, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "? M8 HELP",
                    color = AcademyTheme.AccentMagenta,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                CloseButton(onClick = onDismiss, tint = AcademyTheme.TextNormal)
            }
            Text(
                text = "Tracker basics, guided lessons, and shortcuts.",
                color = AcademyTheme.TextNormal,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )

            HelpMenuItem(
                icon = "♟",
                title = "ACADEMY",
                subtitle = "Interactive quests and guided walkthrough",
                badge = "START",
                accent = AcademyTheme.AccentCyan,
                onClick = {
                    onDismiss()
                    onStartTutorial()
                },
            )

            HelpMenuItem(
                icon = "⌘",
                title = "HOTKEYS",
                subtitle = "Keyboard shortcuts and tracker controls",
                badge = "H",
                accent = AcademyTheme.BorderDim,
                onClick = {
                    onDismiss()
                    onShowHotkeys()
                },
            )

            Text(
                text = "SHIFT + LEFT/RIGHT switches screens · EDIT toggles edit mode · OPT is a context modifier",
                color = AcademyTheme.TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun HelpMenuItem(
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AcademyTheme.BgCard, RoundedCornerShape(8.dp))
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            color = AcademyTheme.TextBright,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(36.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = AcademyTheme.TextBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = subtitle,
                color = AcademyTheme.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = badge,
            color = if (accent == AcademyTheme.BorderDim) AcademyTheme.TextNormal else accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00FF00),
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(Color(0x33FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\u2715",
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}