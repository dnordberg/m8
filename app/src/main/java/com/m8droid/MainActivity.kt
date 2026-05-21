package com.m8droid

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m8droid.data.ButtonLayout
import com.m8droid.input.KeyMapper
import com.m8droid.academy.AcademyState
import com.m8droid.academy.AcademyViewModel
import com.m8droid.academy.AppMode
import com.m8droid.academy.data.AcademyRepository
import com.m8droid.ui.*
import com.m8droid.ui.academy.AcademyShell
import com.m8droid.ui.academy.QuestOverlay

class MainActivity : ComponentActivity() {

    private val viewModel: M8ViewModel by viewModels()
    private var currentKeyState = 0
    private var showHotkeys = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.startMidi()

        setContent {
            M8Theme {
                M8App(viewModel, showHotkeys)
            }
        }
    }

    override fun onDestroy() {
        viewModel.stopMidi()
        super.onDestroy()
    }

    /** True if the given KeyEvent came from a game controller. */
    private fun isGamepadEvent(event: KeyEvent?): Boolean {
        val src = event?.source ?: return false
        return (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

    /** Check user settings to decide whether this input source is enabled. */
    private fun isInputAllowed(event: KeyEvent?): Boolean {
        val settings = viewModel.serverSettings.value
        return if (isGamepadEvent(event)) settings.gamepadEnabled else settings.keyboardEnabled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isInputAllowed(event)) return super.onKeyDown(keyCode, event)

        val shiftHeld = event?.isShiftPressed == true

        // Check app-level hotkeys first
        val hotkey = KeyMapper.mapHotkey(keyCode, shiftHeld)
        if (hotkey != null) {
            handleHotkey(hotkey)
            return true
        }

        // Then M8 button mapping
        val m8Key = KeyMapper.mapKey(keyCode)
        if (m8Key != null) {
            currentKeyState = currentKeyState or m8Key
            viewModel.setKeyboardKeys(currentKeyState)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isInputAllowed(event)) return super.onKeyUp(keyCode, event)

        val m8Key = KeyMapper.mapKey(keyCode)
        if (m8Key != null) {
            currentKeyState = currentKeyState and m8Key.inv()
            viewModel.setKeyboardKeys(currentKeyState)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleHotkey(action: Int) {
        when (action) {
            KeyMapper.ACTION_TOGGLE_HOTKEYS -> showHotkeys.value = !showHotkeys.value
            KeyMapper.ACTION_DISMISS -> showHotkeys.value = false
            KeyMapper.ACTION_SCREEN_1 -> viewModel.setScreen(0)
            KeyMapper.ACTION_SCREEN_2 -> viewModel.setScreen(1)
            KeyMapper.ACTION_SCREEN_3 -> viewModel.setScreen(2)
            KeyMapper.ACTION_SCREEN_4 -> viewModel.setScreen(3)
            KeyMapper.ACTION_SCREEN_5 -> viewModel.setScreen(4)
            KeyMapper.ACTION_SCREEN_6 -> viewModel.setScreen(5)
            KeyMapper.ACTION_SCREEN_7 -> viewModel.setScreen(6)
            KeyMapper.ACTION_SCREEN_8 -> viewModel.setScreen(7)
            KeyMapper.ACTION_TEMPO_DOWN -> viewModel.adjustTempo(-1)
            KeyMapper.ACTION_TEMPO_UP -> viewModel.adjustTempo(1)
            KeyMapper.ACTION_TEMPO_DOWN_10 -> viewModel.adjustTempo(-10)
            KeyMapper.ACTION_TEMPO_UP_10 -> viewModel.adjustTempo(10)
            KeyMapper.ACTION_PLAY_FROM_CURSOR -> viewModel.playFromCursor()
            KeyMapper.ACTION_TAB_NEXT -> viewModel.nextScreen()
            KeyMapper.ACTION_TAB_PREV -> viewModel.prevScreen()
            KeyMapper.ACTION_TOGGLE_TUTORIAL -> viewModel.toggleTutorial()
        }
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
private fun M8App(viewModel: M8ViewModel, showHotkeys: MutableState<Boolean>) {
    val displayTick by viewModel.displayTick.collectAsState()
    val tutorial = viewModel.tutorial
    var showHelpMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var appMode by remember { mutableStateOf(AppMode.M8) }
    val scope = rememberCoroutineScope()
    val academyRepo = remember { AcademyRepository(viewModel.getApplication()) }
    val academyVm = remember {
        AcademyViewModel(viewModel.emulatorEvents, academyRepo, scope)
    }
    val academyState by academyVm.state.collectAsState()
    val activeQuest by academyVm.activeQuest.collectAsState()
    val lastEval by academyVm.lastEvaluation.collectAsState()
    val serverSettings by viewModel.serverSettings.collectAsState()

    // Track tutorial state changes with displayTick (recomposes at ~30fps)
    val tutorialActive = remember(displayTick) { tutorial.active }
    val tutorialPaused = remember(displayTick) { tutorial.paused }
    val tutorialComplete = remember(displayTick) { tutorial.isComplete }

    // Auto-check tutorial completion on each frame
    LaunchedEffect(displayTick) {
        if (tutorialActive && !tutorialPaused && !tutorialComplete) {
            tutorial.checkCompletion()
        }
    }

    LaunchedEffect(appMode) {
        viewModel.keyInputPaused = appMode == AppMode.ACADEMY
    }

    LaunchedEffect(Unit) {
        viewModel.startLocalEmulator()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            when (appMode) {
                AppMode.ACADEMY -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AppHeaderBar(
                            subtitle = "ACADEMY",
                            onLoad = { showLoadDialog = true },
                            onSettings = { showSettings = true },
                            onHelp = { showHelpMenu = true },
                            onAcademy = null,
                            onClose = { appMode = AppMode.M8 },
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AcademyShell(
                                viewModel = academyVm,
                                onSwitchToM8 = { appMode = AppMode.M8 },
                            )
                        }
                    }
                }
                AppMode.M8 -> {
                    val screen: @Composable () -> Unit = {
                        M8Screen(
                            bitmap = viewModel.connectionManager.display.snapshot(),
                            invalidationTick = displayTick,
                            onScreenTap = { viewModel.setScreen(it) },
                            onDisplayTap = { x, y -> viewModel.handleDisplayTap(x, y) },
                            onSwipeLeft = { viewModel.nextScreen() },
                            onSwipeRight = { viewModel.prevScreen() },
                        )
                    }
                    val onKeys: (Int) -> Unit = { keys -> viewModel.setTouchKeys(keys) }
                    val keyState by viewModel.keyState.collectAsState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        AppHeaderBar(
                            subtitle = if (academyState == AcademyState.QUEST_ACTIVE) "QUEST" else "CLASSIC",
                            onLoad = { showLoadDialog = true },
                            onSettings = { showSettings = true },
                            onHelp = { showHelpMenu = true },
                            onAcademy = { appMode = AppMode.ACADEMY },
                        )
                        // Quest overlay when a quest is active
                        if (academyState == AcademyState.QUEST_ACTIVE && activeQuest != null) {
                            QuestOverlay(
                                quest = activeQuest!!,
                                evaluation = lastEval,
                                failedAttempts = 0,
                                onAbandon = {
                                    academyVm.returnToIdle()
                                },
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (serverSettings.buttonLayout) {
                                ButtonLayout.BEST -> M8BestLayout(onKeyStateChanged = onKeys, screenContent = screen, externalKeyMask = keyState)
                                ButtonLayout.FULL_DEVICE -> M8FullDeviceLayout(onKeyStateChanged = onKeys, screenContent = screen, externalKeyMask = keyState)
                            }
                        }
                    }
                }
            }
        }

        // Browse / download dialog
        if (showLoadDialog) {
            BrowseDialog(
                onDismiss = { showLoadDialog = false },
                slotCount = viewModel.instrumentSlotCount,
                onLoadInstrument = { slot, inst -> viewModel.replaceInstrument(slot, inst) },
                onLoadSong = { parsed -> viewModel.replaceSong(parsed) },
            )
        }

        // Settings dialog
        if (showSettings) {
            SettingsDialog(
                currentSettings = serverSettings,
                onSave = { viewModel.saveSettings(it) },
                onRestartServer = { viewModel.restartServer() },
                onDismiss = { showSettings = false },
            )
        }

        // Help menu modal
        if (showHelpMenu) {
            HelpMenu(
                onDismiss = { showHelpMenu = false },
                onStartTutorial = { viewModel.toggleTutorial() },
                onShowHotkeys = { showHotkeys.value = true },
            )
        }

        // Hotkey overlay
        if (showHotkeys.value) {
            HotkeyOverlay(onDismiss = { showHotkeys.value = false })
        }

        // Tutorial overlay
        if (tutorialActive && !tutorialPaused && !tutorialComplete) {
            TutorialOverlay(
                tutorial = tutorial,
                onPause = { tutorial.pause() },
                onStop = { tutorial.stop() },
                onSkip = { tutorial.skip() },
                onPrevious = { tutorial.previousStep() },
            )
        }

        // Tutorial paused banner (contextual tips)
        if (tutorialActive && tutorialPaused) {
            TutorialPausedBanner(
                currentScreen = viewModel.currentScreen,
                onResume = { tutorial.resume() },
                onResumeHere = {
                    tutorial.jumpToScreen(viewModel.currentScreen)
                    tutorial.resume()
                },
                onStop = { tutorial.stop() },
            )
        }

        // Tutorial complete auto-stop
        if (tutorialActive && tutorialComplete) {
            tutorial.stop()
        }
    }
}