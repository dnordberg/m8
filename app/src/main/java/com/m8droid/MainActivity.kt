package com.m8droid

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
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

object AppWindowLayout {
    const val drawsBehindSystemBars: Boolean = true
    const val statusBarTransparent: Boolean = true
}

class MainActivity : ComponentActivity() {

    private val viewModel: M8ViewModel by viewModels()
    private var currentKeyState = 0
    private var showHotkeys = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, !AppWindowLayout.drawsBehindSystemBars)
        window.statusBarColor = if (AppWindowLayout.statusBarTransparent) {
            android.graphics.Color.TRANSPARENT
        } else {
            android.graphics.Color.BLACK
        }
        window.navigationBarColor = android.graphics.Color.BLACK
        viewModel.startMidi()

        setContent {
            M8Theme {
                M8App(
                    viewModel = viewModel,
                    showHotkeys = showHotkeys,
                    onShareProject = { path -> shareSavedProject(path) },
                    onShareDiagnostics = { shareDiagnostics() },
                )
            }
        }
        window.decorView.post { handleInboundProjectIntent(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInboundProjectIntent(intent)
    }

    private fun handleInboundProjectIntent(intent: Intent?) {
        val uri = inboundProjectUri(intent) ?: return
        val shouldLoad = !viewModel.shouldConfirmBeforeReplacingSong()
        runCatching { viewModel.importProjectFromUri(uri, loadAfterImport = shouldLoad) }
            .onSuccess { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
            .onFailure { error ->
                Toast.makeText(this, "ERROR: ${error.message ?: "project import failed"}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun inboundProjectUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }

    fun shareSavedProject(path: String): String {
        return runCatching {
            val file = viewModel.exportableSavedProjectFile(path)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share M8 project"))
            viewModel.markProjectExported(path)
        }.getOrElse { error ->
            val message = "ERROR: ${error.message ?: "share failed"}"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            message
        }
    }

    private fun shareDiagnostics() {
        runCatching {
            val file = viewModel.exportDiagnosticsFile()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                clipData = ClipData.newUri(contentResolver, file.name, uri)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share M8 diagnostics"))
        }.onFailure { error ->
            Toast.makeText(this, "ERROR: ${error.message ?: "diagnostics export failed"}", Toast.LENGTH_SHORT).show()
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
private fun M8App(
    viewModel: M8ViewModel,
    showHotkeys: MutableState<Boolean>,
    onShareProject: (String) -> String,
    onShareDiagnostics: () -> Unit,
) {
    val displayTick by viewModel.displayTick.collectAsState()
    val tutorial = viewModel.tutorial
    var showHelpMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showAcademyFreshStartConfirm by remember { mutableStateOf(false) }
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
    val savedProjects by viewModel.savedProjects.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()
    val projectSaveStatus by viewModel.projectSaveStatus.collectAsState()
    val startupRecovery by viewModel.startupRecovery.collectAsState()
    val projectWarnings by viewModel.projectWarnings.collectAsState()
    val hapticView = LocalView.current
    val openSongLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { viewModel.getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        runCatching { viewModel.loadSongFromUri(uri) }
            .onSuccess { Toast.makeText(viewModel.getApplication(), it, Toast.LENGTH_SHORT).show() }
            .onFailure { error ->
                Toast.makeText(viewModel.getApplication(), "ERROR: ${error.message ?: "file load failed"}", Toast.LENGTH_SHORT).show()
            }
    }

    fun performNavigationHaptic() {
        hapticView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun performEditHaptic() {
        hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun enterAcademyWithFreshSong() {
        viewModel.startFreshAcademyTutorialSong()
        appMode = AppMode.ACADEMY
    }

    fun requestAcademyMode() {
        if (viewModel.shouldConfirmBeforeReplacingSong()) {
            showAcademyFreshStartConfirm = true
        } else {
            enterAcademyWithFreshSong()
        }
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
                .navigationBarsPadding(),
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

                    val showHexEntry = remember(displayTick, serverSettings.hexEditorEnabled) {
                        serverSettings.hexEditorEnabled && viewModel.isEditMode && viewModel.canEnterHexDigit
                    }
                    val showNotePicker = remember(displayTick) {
                        viewModel.isEditMode && viewModel.canEnterNoteFromPicker
                    }
                    val showTextEditor = remember(displayTick) {
                        viewModel.isEditMode && viewModel.canEditTextField
                    }
                    val showValueNudge = remember(displayTick, showHexEntry, showNotePicker, showTextEditor) {
                        viewModel.isEditMode && viewModel.canAdjustSelectedValue &&
                            !showHexEntry && !showNotePicker && !showTextEditor
                    }
                    val showPhraseFields = remember(displayTick) {
                        viewModel.isEditMode && viewModel.isPhraseScreen
                    }
                    val showMixerFields = remember(displayTick) {
                        viewModel.isEditMode && viewModel.isMixerTrackSelection
                    }
                    val showTrackerQuickActions = remember(displayTick) {
                        viewModel.isEditMode && viewModel.canUseTrackerQuickActions
                    }
                    val trackerQuickStatus = remember(displayTick) { viewModel.trackerEditStatus }
                    Column(modifier = Modifier.fillMaxSize()) {
                        AppHeaderBar(
                            subtitle = if (academyState == AcademyState.QUEST_ACTIVE) {
                                "QUEST"
                            } else {
                                com.m8droid.emulator.M8Emulator.SCREEN_NAMES.getOrNull(viewModel.currentScreen) ?: "PROJECT"
                            },
                            onLoad = { showLoadDialog = true },
                            onSave = { viewModel.saveCurrentSong() },
                            dirty = isSongDirty,
                            onSettings = { showSettings = true },
                            onHelp = { showHelpMenu = true },
                            onAcademy = { requestAcademyMode() },
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
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (serverSettings.buttonLayout) {
                                ButtonLayout.BEST -> M8BestLayout(onKeyStateChanged = onKeys, screenContent = screen, externalKeyMask = keyState)
                                ButtonLayout.FULL_DEVICE -> M8FullDeviceLayout(onKeyStateChanged = onKeys, screenContent = screen, externalKeyMask = keyState)
                            }

                            if (showTrackerQuickActions) {
                                val quickActionTop = maxHeight * M8MainLayout.quickActionTopFraction
                                TrackerQuickActionBar(
                                    status = trackerQuickStatus,
                                    onInsert = {
                                        if (viewModel.quickInsertAtSelection()) performEditHaptic()
                                    },
                                    onClear = {
                                        if (viewModel.clearSelection()) performEditHaptic()
                                    },
                                    onDuplicate = {
                                        if (viewModel.duplicateSelection()) performEditHaptic()
                                    },
                                    onTransposeDown = {
                                        if (viewModel.transposeSelection(-1)) performEditHaptic()
                                    },
                                    onTransposeUp = {
                                        if (viewModel.transposeSelection(1)) performEditHaptic()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = quickActionTop)
                                        .padding(end = 8.dp),
                                )
                            }
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
                            if (showTextEditor) {
                                SongNameEditorPad(
                                    label = viewModel.editableTextFieldLabel,
                                    name = viewModel.currentEditableText,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    onApply = {
                                        if (viewModel.setEditableTextFromEditor(it)) performEditHaptic()
                                    },
                                )
                            }
                            if (showValueNudge) {
                                ValueNudgePad(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    onDecreaseCoarse = {
                                        if (viewModel.adjustSelectedValue(-1, coarse = true)) performEditHaptic()
                                    },
                                    onDecrease = {
                                        if (viewModel.adjustSelectedValue(-1)) performEditHaptic()
                                    },
                                    onIncrease = {
                                        if (viewModel.adjustSelectedValue(1)) performEditHaptic()
                                    },
                                    onIncreaseCoarse = {
                                        if (viewModel.adjustSelectedValue(1, coarse = true)) performEditHaptic()
                                    },
                                )
                            }
                            if (showPhraseFields) {
                                EditFieldStrip(
                                    labels = listOf("NOTE", "INST", "VOL", "FX1", "F1V", "FX2", "F2V", "FX3", "F3V"),
                                    selectedColumn = viewModel.currentPhraseEditColumn,
                                    onSelect = {
                                        if (viewModel.selectPhraseEditColumn(it)) performNavigationHaptic()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 8.dp)
                                        .padding(
                                            bottom = when {
                                                showHexEntry -> 194.dp
                                                showNotePicker -> 112.dp
                                                else -> 70.dp
                                            },
                                        ),
                                )
                            }
                            if (showMixerFields) {
                                EditFieldStrip(
                                    labels = listOf("VOL", "PAN", "CHO", "DEL", "REV"),
                                    selectedColumn = viewModel.currentMixerEditParameter,
                                    onSelect = {
                                        if (viewModel.selectMixerEditParameter(it)) performNavigationHaptic()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 70.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (startupRecovery != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissStartupRecovery() },
                title = { Text(startupRecovery!!.title) },
                text = { Text("${startupRecovery!!.detail}\n\nYour existing project files were left untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissStartupRecovery()
                        showLoadDialog = true
                    }) { Text(startupRecovery!!.primaryAction.uppercase()) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissStartupRecovery() }) {
                        Text(startupRecovery!!.dismissAction.uppercase())
                    }
                },
            )
        }

        if (projectWarnings != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissProjectWarnings() },
                title = { Text("Project warnings") },
                text = { Text(projectWarnings!!.userMessage()) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissProjectWarnings() }) { Text("OK") }
                },
            )
        }

        if (showAcademyFreshStartConfirm) {
            AlertDialog(
                onDismissRequest = { showAcademyFreshStartConfirm = false },
                title = { Text("Start fresh Academy song?") },
                text = { Text("Academy lessons start from a clean tutorial file so quests do not depend on the demo or your current project. Save current work first?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.saveCurrentSong()
                        showAcademyFreshStartConfirm = false
                        enterAcademyWithFreshSong()
                    }) { Text("SAVE + START") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showAcademyFreshStartConfirm = false
                            enterAcademyWithFreshSong()
                        }) { Text("START FRESH") }
                        TextButton(onClick = { showAcademyFreshStartConfirm = false }) { Text("CANCEL") }
                    }
                },
            )
        }

        // Browse / download dialog
        if (showLoadDialog) {
            BrowseDialog(
                onDismiss = { showLoadDialog = false },
                slotCount = viewModel.instrumentSlotCount,
                onLoadInstrument = { slot, inst -> viewModel.replaceInstrument(slot, inst) },
                onLoadSong = { parsed, location -> viewModel.replaceSong(parsed, recentLocation = location) },
                recentSongs = recentSongs,
                onRefreshRecentSongs = { viewModel.refreshRecentSongs() },
                onNewSong = { viewModel.newSong() },
                onOpenDeviceSong = { openSongLauncher.launch(arrayOf("application/octet-stream", "audio/*", "*/*")) },
                onLoadRecentSong = { entry -> viewModel.loadRecentSong(entry) },
                savedProjects = savedProjects,
                onRefreshProjects = { viewModel.refreshSavedProjects() },
                onLoadProject = { path -> viewModel.loadSavedProject(path) },
                onRenameProject = { path, name -> viewModel.renameSavedProject(path, name) },
                onDuplicateProject = { path, name -> viewModel.duplicateSavedProject(path, name) },
                onDeleteProject = { path -> viewModel.deleteSavedProject(path) },
                onShareProject = { path -> onShareProject(path) },
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
                onExportDiagnostics = onShareDiagnostics,
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
private fun SongNameEditorPad(
    label: String,
    name: String,
    modifier: Modifier = Modifier,
    onApply: (String) -> Unit,
) {
    var draft by remember(name) { mutableStateOf(name) }
    Surface(
        modifier = modifier.fillMaxWidth(0.92f),
        color = Color(0xEE05080C),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFF3B5268), MaterialTheme.shapes.medium)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                color = Color(0xFFFF4FD8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(64) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFFE9F5FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF3B5268),
                    cursorColor = Color(0xFF00E5FF),
                    focusedTextColor = Color(0xFFE9F5FF),
                    unfocusedTextColor = Color(0xFFE9F5FF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onApply(draft) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF173042),
                        contentColor = Color(0xFFE9F5FF),
                    ),
                ) {
                    Text("APPLY", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
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
private fun ValueNudgePad(
    modifier: Modifier = Modifier,
    onDecreaseCoarse: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onIncreaseCoarse: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xEE05080C),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Color(0xFF3B5268), MaterialTheme.shapes.medium)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NudgeButton("−−", onDecreaseCoarse)
            NudgeButton("−", onDecrease)
            Text("EDIT", color = Color(0xFF9BB7D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            NudgeButton("+", onIncrease)
            NudgeButton("++", onIncreaseCoarse)
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 48.dp, height = 38.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF172332),
            contentColor = Color(0xFFE9F5FF),
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditFieldStrip(
    labels: List<String>,
    selectedColumn: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xEE05080C),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 5.dp,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .border(1.dp, Color(0xFF3B5268), MaterialTheme.shapes.small)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Button(
                    onClick = { onSelect(index) },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (index == selectedColumn) Color(0xFF2E5F9A) else Color(0xFF172332),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
