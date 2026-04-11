package com.m8.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
fun HotkeyOverlay(
    onDismiss: () -> Unit,
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
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .background(Color(0xFF0A0A1A), RoundedCornerShape(8.dp))
                .padding(16.dp)
                .clickable(enabled = false) {} // prevent dismiss on content click
        ) {
            Text(
                text = "HOTKEYS",
                color = Color(0xFF00FF00),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Press H to close",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                HotkeySection("NAVIGATION")
                HotkeyRow("Arrow keys / WASD", "Move cursor")
                HotkeyRow("Z", "Option (next screen)")
                HotkeyRow("X", "Edit (prev screen)")
                HotkeyRow("Space", "Play / Stop")
                HotkeyRow("Left Shift", "Shift (cycle octave)")
                HotkeyRow("Tab", "Next screen")
                HotkeyRow("Shift+Tab", "Previous screen")

                HotkeySection("EDITING (Phrase screen)")
                HotkeyRow("Shift + X", "Toggle edit mode")
                HotkeyRow("Shift + Up/Down", "Note +/- semitone")
                HotkeyRow("Shift + Left/Right", "Note +/- octave")

                HotkeySection("PLAYBACK")
                HotkeyRow("Space", "Play / Stop")
                HotkeyRow("Enter", "Play from cursor")
                HotkeyRow("[ / ]", "Tempo -1 / +1")
                HotkeyRow("Shift + [ / ]", "Tempo -10 / +10")

                HotkeySection("SCREENS")
                HotkeyRow("1", "Song")
                HotkeyRow("2", "Chain")
                HotkeyRow("3", "Phrase")
                HotkeyRow("4", "Instrument")
                HotkeyRow("5", "Table")
                HotkeyRow("6", "Mixer")
                HotkeyRow("7", "FX")
                HotkeyRow("8", "Config")

                HotkeySection("GENERAL")
                HotkeyRow("H", "Show / hide hotkeys")
                HotkeyRow("Esc", "Close modal / cancel")

                HotkeySection("GAMEPAD")
                HotkeyRow("A", "Option")
                HotkeyRow("B", "Edit")
                HotkeyRow("L1", "Shift")
                HotkeyRow("Start / R1", "Play")
                HotkeyRow("D-Pad", "Navigate")
            }
        }
    }
}

@Composable
private fun HotkeySection(title: String) {
    Text(
        text = title,
        color = Color(0xFF4488CC),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun HotkeyRow(key: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = key,
            color = Color(0xFF00CC66),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            color = Color(0xFFAABBCC),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1.2f),
        )
    }
}
