package com.m8droid

import android.os.Bundle
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.m8droid.data.ButtonLayout
import com.m8droid.input.KeyMapper
import com.m8droid.input.StickyKeyLatch
import com.m8droid.protocol.M8Commands
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
    val isSongDirty by viewModel.isSongDirty.collectAsState()
    val projectSaveStatus by viewModel.projectSaveStatus.collectAsState()
    val hapticView = LocalView.current

    fun performNavigationHaptic() {
        hapticView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun performEditHaptic() {
        hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    // Tutorial fields are Compose state; read them directly so overlay controls
    // refresh immediately even when the display tick is paused or unchanged.
    val tutorialActive = tutorial.active
    val tutorialPaused = tutorial.paused
    val tutorialComplete = tutorial.isComplete

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
                            onScreenTap = {
                                viewModel.setScreen(it)
                                performNavigationHaptic()
                            },
                            onDisplayTap = { x, y -> viewModel.handleDisplayTap(x, y) },
                            onDisplayLongPress = { x, y ->
                                if (viewModel.handleDisplayLongPress(x, y)) performEditHaptic()
                            },
                            onSwipeLeft = {
                                viewModel.nextScreen()
                                performNavigationHaptic()
                            },
                            onSwipeRight = {
                                viewModel.prevScreen()
                                performNavigationHaptic()
                            },
                        )
                    }
                    val onKeys: (Int) -> Unit = { keys -> viewModel.setTouchKeys(keys) }
                    val keyState by viewModel.keyState.collectAsState()
                    val stickyKeyState by viewModel.stickyKeyState.collectAsState()
                    val showHexEntry = remember(displayTick, serverSettings.hexEditorEnabled) {
                        serverSettings.hexEditorEnabled && viewModel.isEditMode && viewModel.canEnterHexDigit
                    }
                    val showNotePicker = remember(displayTick) {
                        viewModel.isEditMode && viewModel.canEnterNoteFromPicker
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        AppHeaderBar(
                            subtitle = if (academyState == AcademyState.QUEST_ACTIVE) "QUEST" else "CLASSIC",
                            onLoad = { showLoadDialog = true },
                            onSave = { viewModel.saveCurrentSong() },
                            dirty = isSongDirty,
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
                            StickyModifierBar(
                                stickyMask = stickyKeyState,
                                onToggle = { viewModel.toggleStickyTouchKey(it) },
                                onClear = { viewModel.clearStickyTouchKeys() },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                            )
                            if (showHexEntry) {
                                HexEntryPad(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    onDigit = {
                                        if (viewModel.enterHexDigit(it)) performEditHaptic()
                                    },
                                )
                            }
                            if (showNotePicker) {
                                MiniPianoPad(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    onNote = {
                                        if (viewModel.enterNoteFromPicker(it)) performEditHaptic()
                                    },
                                )
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
                shouldConfirmSongReplace = { viewModel.shouldConfirmBeforeReplacingSong() },
                onSaveCurrentSong = { viewModel.saveCurrentSong() },
                saveStatus = projectSaveStatus,
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
                onPressHint = { tutorial.completeCurrentStepFromOverlay() },
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

@Composable
private fun HexEntryPad(
    modifier: Modifier = Modifier,
    onDigit: (Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xEE05080C),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFF3B5268), MaterialTheme.shapes.medium)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("HEX", color = Color(0xFF9BB7D0), fontSize = 11.sp)
            listOf("0123", "4567", "89AB", "CDEF").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { ch ->
                        val digit = ch.digitToInt(16)
                        Button(
                            onClick = { onDigit(digit) },
                            modifier = Modifier.size(width = 42.dp, height = 34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF172332),
                                contentColor = Color(0xFFE9F5FF),
                            ),
                        ) {
                            Text(ch.toString(), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPianoPad(
    modifier: Modifier = Modifier,
    onNote: (Int) -> Unit,
) {
    val notes = listOf(
        "C" to 0,
        "C#" to 1,
        "D" to 2,
        "D#" to 3,
        "E" to 4,
        "F" to 5,
        "F#" to 6,
        "G" to 7,
        "G#" to 8,
        "A" to 9,
        "A#" to 10,
        "B" to 11,
    )
    Surface(
        modifier = modifier,
        color = Color(0xEE05080C),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFF3B5268), MaterialTheme.shapes.medium)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("NOTE", color = Color(0xFF9BB7D0), fontSize = 11.sp)
            notes.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { (label, semitone) ->
                        Button(
                            onClick = { onNote(semitone) },
                            modifier = Modifier.size(width = 42.dp, height = 34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (label.contains('#')) Color(0xFF1A2436) else Color(0xFF233245),
                                contentColor = Color(0xFFE9F5FF),
                            ),
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickyModifierBar(
    stickyMask: Int,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC05080C),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Color(0xFF304458), MaterialTheme.shapes.small)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StickyModifierButton("OPT", M8Commands.KEY_OPTION, stickyMask, onToggle)
            StickyModifierButton("EDIT", M8Commands.KEY_EDIT, stickyMask, onToggle)
            StickyModifierButton("SHIFT", M8Commands.KEY_SHIFT, stickyMask, onToggle)
            if (stickyMask and StickyKeyLatch.MODIFIER_MASK != 0) {
                Button(
                    onClick = onClear,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A1A1A),
                        contentColor = Color(0xFFFFB0B0),
                    ),
                ) {
                    Text("CLR", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StickyModifierButton(
    label: String,
    key: Int,
    stickyMask: Int,
    onToggle: (Int) -> Unit,
) {
    val active = stickyMask and key != 0
    Button(
        onClick = { onToggle(key) },
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF2E5F9A) else Color(0xFF172332),
            contentColor = if (active) Color.White else Color(0xFFB8CDE0),
        ),
    ) {
        Text(label, fontSize = 11.sp)
    }
}