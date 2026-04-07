package com.m8.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.m8.data.ServerSettings

@Composable
fun SettingsScreen(
    currentSettings: ServerSettings,
    onSave: (ServerSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by remember { mutableStateOf(currentSettings.host) }
    var port by remember { mutableStateOf(currentSettings.port.toString()) }
    var autoConnect by remember { mutableStateOf(currentSettings.autoConnect) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "M8 Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server Host") },
                placeholder = { Text("100.64.0.1 or hostname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("WebSocket Port") },
                placeholder = { Text("8765") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it },
                )
                Text("Auto-connect on launch")
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val portNum = port.toIntOrNull() ?: 8765
                        onSave(ServerSettings(host, portNum, autoConnect))
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}
