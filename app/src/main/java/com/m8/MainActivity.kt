package com.m8

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8.data.ServerSettings
import com.m8.input.KeyMapper
import com.m8.network.ConnectionState
import com.m8.ui.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val viewModel: M8ViewModel by viewModels()
    private var currentKeyState = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while using M8
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            M8Theme {
                M8App(viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val m8Key = KeyMapper.mapKey(keyCode)
        if (m8Key != null) {
            currentKeyState = currentKeyState or m8Key
            viewModel.sendKeyState(currentKeyState)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val m8Key = KeyMapper.mapKey(keyCode)
        if (m8Key != null) {
            currentKeyState = currentKeyState and m8Key.inv()
            viewModel.sendKeyState(currentKeyState)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun M8Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00FF00),
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color(0xFF00FF00),
            surface = Color(0xFF111111),
            onSurface = Color(0xFF00FF00),
        ),
        content = content,
    )
}

@Composable
private fun M8App(viewModel: M8ViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    val connectionState by viewModel.connectionState.collectAsState()
    val displayTick by viewModel.displayTick.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = ServerSettings())
    // Auto-connect once on first launch
    LaunchedEffect(Unit) {
        // Wait for settings to load from DataStore, then connect
        kotlinx.coroutines.delay(500)
        val currentSettings = viewModel.settings.first()
        if (currentSettings.autoConnect) {
            viewModel.connect(currentSettings)
        }
    }

    if (showSettings) {
        SettingsScreen(
            currentSettings = settings,
            onSave = { newSettings ->
                viewModel.saveSettings(newSettings)
                viewModel.disconnect()
                viewModel.connect(newSettings)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    } else {
        M8MainScreen(
            viewModel = viewModel,
            connectionState = connectionState,
            displayTick = displayTick,
            onSettingsClick = { showSettings = true },
            onConnectClick = { viewModel.connect(settings) },
            onDisconnectClick = { viewModel.disconnect() },
        )
    }
}

@Composable
private fun M8MainScreen(
    viewModel: M8ViewModel,
    connectionState: ConnectionState,
    displayTick: Int,
    onSettingsClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        // Top bar — compact status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionStatusIndicator(state = connectionState)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isAudioMuted) "[MUTED]" else "[AUDIO]",
                    color = if (isAudioMuted) Color(0xFFFF4444) else Color(0xFF00FF00),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { viewModel.toggleAudioMute() },
                )

                if (connectionState == ConnectionState.DISCONNECTED) {
                    Text(
                        text = "[CONNECT]",
                        color = Color(0xFF00FF00),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { onConnectClick() },
                    )
                } else if (connectionState == ConnectionState.CONNECTED) {
                    Text(
                        text = "[DISCONNECT]",
                        color = Color(0xFFFF4444),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { onDisconnectClick() },
                    )
                }
                Text(
                    text = "[SET]",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onSettingsClick() },
                )
            }
        }

        // M8 Display — fills available space, centered
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            M8Screen(
                bitmap = viewModel.connectionManager.display.snapshot(),
                invalidationTick = displayTick,
            )
        }

        // Touch controls — pinned to bottom
        M8Controls(
            onKeyStateChanged = { keys -> viewModel.sendKeyState(keys) },
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
