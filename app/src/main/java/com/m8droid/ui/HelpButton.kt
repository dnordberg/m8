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
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(Color(0xFF0A0A1A), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))
                .padding(20.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HELP",
                    color = Color(0xFF00FF00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                CloseButton(onClick = onDismiss)
            }
            Text(
                text = "M8 is a tracker-style music sequencer. Use the touch buttons to navigate. SHIFT + LEFT/RIGHT switches screens. EDIT enters/exits edit mode. OPT is a context modifier.",
                color = Color(0xFFAABBCC),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )

            HelpMenuItem(
                title = "Start Tutorial",
                subtitle = "Interactive walkthrough (also: hold OPT+EDIT+SHIFT)",
                onClick = {
                    onDismiss()
                    onStartTutorial()
                },
            )

            HelpMenuItem(
                title = "Show Hotkeys",
                subtitle = "Keyboard shortcuts reference (also: press H)",
                onClick = {
                    onDismiss()
                    onShowHotkeys()
                },
            )


        }
    }
}

@Composable
private fun HelpMenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111127), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF224466), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF00CC66),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = subtitle,
            color = Color(0xFF8899AA),
            fontSize = 10.sp,
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