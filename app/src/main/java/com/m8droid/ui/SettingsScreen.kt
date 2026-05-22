package com.m8droid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8droid.data.ButtonLayout
import com.m8droid.data.ServerSettings

/**
 * Overlay-style settings dialog. Matches the HelpMenu visual language
 * (full-screen scrim + dark-navy panel with neon-green borders) and lets
 * the user tweak host/port, pick a button layout, and kick a server restart.
 */
@Composable
fun SettingsDialog(
    currentSettings: ServerSettings,
    onSave: (ServerSettings) -> Unit,
    onRestartServer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by remember(currentSettings) { mutableStateOf(currentSettings.host) }
    var port by remember(currentSettings) { mutableStateOf(currentSettings.port.toString()) }
    var autoConnect by remember(currentSettings) { mutableStateOf(currentSettings.autoConnect) }
    var layout by remember(currentSettings) { mutableStateOf(currentSettings.buttonLayout) }
    var gamepad by remember(currentSettings) { mutableStateOf(currentSettings.gamepadEnabled) }
    var keyboard by remember(currentSettings) { mutableStateOf(currentSettings.keyboardEnabled) }
    var hexEditor by remember(currentSettings) { mutableStateOf(currentSettings.hexEditorEnabled) }

    fun buildSettings(): ServerSettings = ServerSettings(
        host = host.trim(),
        port = port.toIntOrNull() ?: currentSettings.port,
        autoConnect = autoConnect,
        buttonLayout = layout,
        gamepadEnabled = gamepad,
        keyboardEnabled = keyboard,
        hexEditorEnabled = hexEditor,
    )

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SETTINGS",
                    color = Color(0xFF00FF00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                CloseButton(onClick = onDismiss)
            }

            LabeledField(label = "HOST", value = host, onValueChange = { host = it })
            LabeledField(
                label = "PORT",
                value = port,
                onValueChange = { new -> port = new.filter { c -> c.isDigit() }.take(5) },
                keyboardType = KeyboardType.Number,
            )

            ToggleRow(
                label = "AUTO-CONNECT",
                checked = autoConnect,
                onChange = { autoConnect = it },
            )

            ToggleRow(
                label = "GAMEPAD INPUT",
                checked = gamepad,
                onChange = { gamepad = it },
            )

            ToggleRow(
                label = "KEYBOARD INPUT",
                checked = keyboard,
                onChange = { keyboard = it },
            )

            ToggleRow(
                label = "TOUCH HEX EDITOR",
                checked = hexEditor,
                onChange = { hexEditor = it },
            )

            Text(
                text = "BUTTON LAYOUT",
                color = Color(0xFF8899AA),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tap-to-apply: picking a chip persists immediately so the user
                // sees the new layout without having to tap Save.
                fun pickLayout(l: ButtonLayout) {
                    layout = l
                    onSave(buildSettings())
                }
                LayoutChip(
                    label = "BEST",
                    selected = layout == ButtonLayout.BEST,
                    onClick = { pickLayout(ButtonLayout.BEST) },
                    modifier = Modifier.weight(1f),
                )
                LayoutChip(
                    label = "DEVICE",
                    selected = layout == ButtonLayout.FULL_DEVICE,
                    onClick = { pickLayout(ButtonLayout.FULL_DEVICE) },
                    modifier = Modifier.weight(1f),
                )
            }

            DialogButton(
                title = "Restart Server",
                subtitle = "Save and restart the local emulator",
                onClick = {
                    onSave(buildSettings())
                    onRestartServer()
                    onDismiss()
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogButton(
                    title = "Save",
                    subtitle = null,
                    onClick = {
                        onSave(buildSettings())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    title = "Cancel",
                    subtitle = null,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Tap anywhere outside to close",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color(0xFF8899AA),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111127), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF224466), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF00FF00),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(Color(0xFF00FF00)),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111127), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF224466), RoundedCornerShape(6.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF8899AA),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (checked) "[ON]" else "[OFF]",
            color = if (checked) Color(0xFF00FF00) else Color(0xFF555555),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LayoutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) Color(0xFF00FF00) else Color(0xFF224466)
    val fg = if (selected) Color(0xFF00FF00) else Color(0xFF8899AA)
    Box(
        modifier = modifier
            .background(Color(0xFF111127), RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DialogButton(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color(0xFF8899AA),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}