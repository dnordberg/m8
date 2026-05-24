package com.m8droid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m8droid.browse.BrowseViewModel
import com.m8droid.browse.ContentKind
import com.m8droid.browse.DownloadStore
import com.m8droid.browse.FileHubLayout
import com.m8droid.browse.FileHubTabs
import com.m8droid.browse.RemoteItem
import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8ProjectLibrary
import com.m8droid.emulator.M8sParser
import com.m8droid.emulator.RecentSongStore
import com.m8droid.ui.academy.AcademyTheme

private val M8_GREEN = AcademyTheme.AccentMagenta
private val M8_BG = AcademyTheme.BgRoot
private val M8_BG_DIM = AcademyTheme.BgCard.copy(alpha = 0.86f)
private val M8_DIM = AcademyTheme.TextDim
private val M8_CARD = AcademyTheme.BgCard
private val M8_CARD_HI = AcademyTheme.BgCardHi
private val M8_CYAN = AcademyTheme.AccentCyan

@Composable
fun BrowseDialog(
    onDismiss: () -> Unit,
    slotCount: Int,
    onLoadInstrument: (slot: Int, inst: M8Instrument) -> Unit,
    onLoadSong: (M8sParser.ParsedSong, String?) -> Unit,
    recentSongs: List<RecentSongStore.Entry> = emptyList(),
    onRefreshRecentSongs: () -> Unit = {},
    onNewSong: () -> String = { "NEW SONG" },
    onOpenDeviceSong: () -> Unit = {},
    onLoadRecentSong: (RecentSongStore.Entry) -> String = { "LOADED" },
    savedProjects: List<M8ProjectLibrary.SavedProject> = emptyList(),
    onRefreshProjects: () -> Unit = {},
    onLoadProject: (String) -> String = { "LOADED" },
    onRenameProject: (String, String) -> String = { _, _ -> "RENAMED" },
    onDuplicateProject: (String, String) -> String = { _, _ -> "DUPLICATED" },
    onDeleteProject: (String) -> String = { "DELETED" },
    shouldConfirmSongReplace: () -> Boolean = { false },
    onSaveCurrentSong: () -> String = { "SAVED" },
    saveStatus: String? = null,
    viewModel: BrowseViewModel = viewModel(),
) {
    val sources = viewModel.sources
    val sourceIndex by viewModel.currentSourceIndex.collectAsState()
    val items by viewModel.items.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    val lastDownloaded by viewModel.lastDownloaded.collectAsState()
    val sdEntries by viewModel.sdEntries.collectAsState()
    val sdSelected by viewModel.sdSelected.collectAsState()
    val loadStatus by viewModel.loadStatus.collectAsState()
    val previewStatus by viewModel.previewStatus.collectAsState()
    var fileTab by remember { mutableStateOf(FileHubTabs.defaultLabel) }
    val viewingRecent = fileTab == "RECENT"
    val viewingOpenDevice = fileTab == "OPEN DEVICE"
    val viewingDownload = fileTab == "DOWNLOAD"
    val viewingSd = viewingOpenDevice
    val viewingProjects = false
    val downloadedStates = remember(items, sdEntries) { DownloadStore.markDownloaded(items, sdEntries) }
    val downloadedByKey = remember(downloadedStates) { downloadedStates.associateBy { remoteKey(it.item) } }
    val selectedDownloaded = selected?.let { downloadedByKey[remoteKey(it)]?.entry }
    var selectedProject by remember { mutableStateOf<M8ProjectLibrary.SavedProject?>(null) }
    var projectLoadStatus by remember { mutableStateOf<String?>(null) }
    var fileActionStatus by remember { mutableStateOf<String?>(null) }
    var pendingSongLoad by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestSongLoad(action: () -> Unit) {
        if (shouldConfirmSongReplace()) pendingSongLoad = action else action()
    }

    // First-open fetch.
    LaunchedEffect(Unit) {
        if (items.isEmpty() && !loading) viewModel.refresh()
        onRefreshRecentSongs()
        onRefreshProjects()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(FileHubLayout.edgePaddingDp.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(FileHubLayout.dialogWidthFraction)
                    .fillMaxHeight(FileHubLayout.dialogHeightFraction)
                    .background(M8_BG, RoundedCornerShape(8.dp))
                    .border(1.dp, AcademyTheme.BorderDim, RoundedCornerShape(8.dp)),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "▣ FILE HUB",
                        color = M8_GREEN,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    CloseButton(onClick = onDismiss, tint = AcademyTheme.TextNormal)
                }

                Spacer(Modifier.height(8.dp))

                NewSongBanner(
                    status = fileActionStatus ?: saveStatus,
                    onClick = { requestSongLoad { fileActionStatus = onNewSong() } },
                )

                Spacer(Modifier.height(8.dp))

                // Three primary File modes matching the phone mockups: Recent, Open Device, Download.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(M8_CARD, RoundedCornerShape(8.dp))
                        .border(1.dp, AcademyTheme.BorderDim, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FileHubTabs.topTabLabels.forEach { label ->
                        val active = fileTab == label
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) M8_CYAN.copy(alpha = 0.18f) else Color.Transparent,
                                    RoundedCornerShape(5.dp),
                                )
                                .clickable {
                                    fileTab = label
                                    when (label) {
                                        "OPEN DEVICE" -> requestSongLoad { onOpenDeviceSong() }
                                        "DOWNLOAD" -> if (items.isEmpty() && !loading) viewModel.refresh()
                                    }
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (active) M8_CYAN else AcademyTheme.TextNormal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (viewingDownload) {
                    Text(
                        text = "DOWNLOAD SOURCES",
                        color = M8_DIM,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FileHubTabs.compactDownloadSourceLabels(FileHubTabs.downloadSourceLabels(sources.map { it.displayName })).forEachIndexed { i, label ->
                            val active = i == sourceIndex
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (active) M8_GREEN else M8_DIM.copy(alpha = 0.6f),
                                        RoundedCornerShape(5.dp),
                                    )
                                    .background(
                                        if (active) M8_GREEN.copy(alpha = 0.12f) else Color.Transparent,
                                        RoundedCornerShape(5.dp),
                                    )
                                    .clickable { viewModel.selectSource(i) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) M8_GREEN else M8_DIM,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Body: list (left) + detail (right) in a Row, stacked on narrow screens
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.weight(1f)) {
                        val desc = when {
                            viewingRecent -> "Songs and projects opened recently"
                            viewingProjects -> "Saved app-native projects in m8sd/Projects"
                            viewingSd -> "Virtual M8 SD card at ${viewModel.sdRootPath}"
                            else -> sources.getOrNull(sourceIndex)?.description.orEmpty()
                        }
                        if (desc.isNotEmpty()) {
                            Text(
                                text = desc,
                                color = M8_DIM,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        when {
                            viewingRecent -> if (recentSongs.isEmpty()) EmptyState("NO RECENTS") else RecentList(
                                entries = recentSongs,
                                onSelect = { entry ->
                                    requestSongLoad {
                                        fileActionStatus = runCatching { onLoadRecentSong(entry) }
                                            .getOrElse { "ERROR: ${it.message ?: "load failed"}" }
                                    }
                                },
                            )
                            viewingProjects -> if (savedProjects.isEmpty()) EmptyState() else ProjectList(
                                projects = savedProjects,
                                selected = selectedProject,
                                onSelect = {
                                    selectedProject = it
                                    projectLoadStatus = null
                                },
                            )
                            viewingSd -> if (sdEntries.isEmpty()) EmptyState() else SdList(
                                entries = sdEntries,
                                selected = sdSelected,
                                onSelect = { viewModel.selectSdEntry(it) },
                            )
                            loading -> LoadingState()
                            error != null -> ErrorState(error!!) { viewModel.refresh() }
                            items.isEmpty() -> EmptyState()
                            else -> ItemList(
                                items = items,
                                selected = selected,
                                downloadedKeys = downloadedStates.filter { it.isDownloaded }.map { remoteKey(it.item) }.toSet(),
                                onSelect = { viewModel.select(it) },
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (viewingRecent) {
                            RecentDetailPane(fileActionStatus ?: saveStatus)
                        } else if (viewingProjects) {
                            ProjectDetailPane(
                                project = selectedProject,
                                loadStatus = projectLoadStatus ?: saveStatus,
                                onRefresh = onRefreshProjects,
                                onLoad = {
                                    val project = selectedProject ?: return@ProjectDetailPane
                                    requestSongLoad {
                                        projectLoadStatus = runCatching { onLoadProject(project.path) }
                                            .getOrElse { "ERROR: ${it.message ?: "load failed"}" }
                                    }
                                },
                                onRename = { name ->
                                    val project = selectedProject ?: return@ProjectDetailPane
                                    projectLoadStatus = runCatching { onRenameProject(project.path, name) }
                                        .getOrElse { "ERROR: ${it.message ?: "rename failed"}" }
                                    selectedProject = null
                                    onRefreshProjects()
                                },
                                onDuplicate = { name ->
                                    val project = selectedProject ?: return@ProjectDetailPane
                                    projectLoadStatus = runCatching { onDuplicateProject(project.path, name) }
                                        .getOrElse { "ERROR: ${it.message ?: "duplicate failed"}" }
                                    onRefreshProjects()
                                },
                                onDelete = {
                                    val project = selectedProject ?: return@ProjectDetailPane
                                    projectLoadStatus = runCatching { onDeleteProject(project.path) }
                                        .getOrElse { "ERROR: ${it.message ?: "delete failed"}" }
                                    selectedProject = null
                                    onRefreshProjects()
                                },
                            )
                        } else if (viewingSd) {
                            SdDetailPane(
                                entry = sdSelected,
                                slotCount = slotCount,
                                loadStatus = loadStatus,
                                previewStatus = previewStatus,
                                onLoad = { slot ->
                                    val e = sdSelected ?: return@SdDetailPane
                                    viewModel.loadInstrumentIntoSlot(e, slot, onLoadInstrument)
                                },
                                onLoadSong = {
                                    val e = sdSelected ?: return@SdDetailPane
                                    requestSongLoad { viewModel.loadSongFromEntry(e, onLoadSong) }
                                },
                                onPreviewSample = {
                                    val e = sdSelected ?: return@SdDetailPane
                                    viewModel.previewSample(e)
                                },
                                onStopPreview = { viewModel.stopSamplePreview() },
                            )
                        } else {
                            DetailPane(
                                item = selected,
                                downloading = downloading,
                                lastDownloaded = lastDownloaded ?: selectedDownloaded,
                                loadStatus = loadStatus,
                                slotCount = slotCount,
                                onDownload = { viewModel.downloadSelected() },
                                onDownloadAndLoadSong = {
                                    val it = selected ?: return@DetailPane
                                    requestSongLoad { viewModel.downloadAndLoadSong(it, onLoadSong) }
                                },
                                onDownloadAndLoadInstrument = { slot ->
                                    val it = selected ?: return@DetailPane
                                    viewModel.downloadAndLoadInstrument(it, slot, onLoadInstrument)
                                },
                                onDismissResult = { viewModel.clearLastDownloaded() },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    val pending = pendingSongLoad
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingSongLoad = null },
            title = { Text("Replace current song?") },
            text = {
                Column {
                    Text("Current edits are not saved. Save them before loading a different song, or discard them.")
                    if (!saveStatus.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(saveStatus, color = M8_GREEN, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveCurrentSong()
                    pendingSongLoad = null
                    pending()
                }) { Text("Save + Replace") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingSongLoad = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        pendingSongLoad = null
                        pending()
                    }) { Text("Discard") }
                }
            },
        )
    }
}

@Composable
private fun NewSongBanner(
    status: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(M8_CARD, RoundedCornerShape(8.dp))
                .border(1.dp, AcademyTheme.AccentYellow.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = FileHubTabs.newSongBannerLabel,
                color = AcademyTheme.AccentYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!status.isNullOrBlank() && !status.equals("CURRENT FRESH SONG", ignoreCase = true)) {
            Spacer(Modifier.height(4.dp))
            Text(status, color = M8_DIM, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun CompactButton(label: String, onClick: () -> Unit) {
    Text(
        text = "[$label]",
        color = M8_GREEN,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "LOADING...",
            color = M8_GREEN,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun EmptyState(label: String = "NO ITEMS") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = M8_DIM,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ERROR",
            color = Color(0xFFFF4040),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = msg,
            color = Color(0xFFFF4040),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "[RETRY]",
            color = M8_GREEN,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                .clickable { onRetry() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SdList(
    entries: List<DownloadStore.Entry>,
    selected: DownloadStore.Entry?,
    onSelect: (DownloadStore.Entry) -> Unit,
) {
    // Group by the M8-SD folder (Songs/Instruments/Samples/...) so the
    // layout mirrors what a real M8 screen would show.
    val grouped = entries.groupBy { it.kind }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        ContentKind.values().forEach { kind ->
            val list = grouped[kind].orEmpty()
            if (list.isEmpty()) return@forEach
            item(key = "h-$kind") {
                Text(
                    text = "/${folderLabel(kind)}/",
                    color = M8_GREEN,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            items(list, key = { e -> entryKey(e) }) { e ->
                val isSelected = selected?.let { entryKey(it) == entryKey(e) } == true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .background(
                            if (isSelected) M8_CYAN.copy(alpha = 0.18f) else M8_CARD,
                        )
                        .border(
                            1.dp,
                            if (isSelected) M8_CYAN else AcademyTheme.BorderDim,
                            RoundedCornerShape(3.dp),
                        )
                        .clickable { onSelect(e) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = e.sdPath,
                        color = M8_GREEN,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    val meta = buildString {
                        if (!e.author.isNullOrBlank()) append("by ${e.author}  ")
                        append("(${humanSize(e.sizeBytes)})")
                        append("  [${e.sourceName}]")
                    }
                    Text(
                        text = meta,
                        color = M8_DIM,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentList(
    entries: List<RecentSongStore.Entry>,
    onSelect: (RecentSongStore.Entry) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.location }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(M8_CARD, RoundedCornerShape(8.dp))
                    .border(1.dp, AcademyTheme.BorderDim, RoundedCornerShape(8.dp))
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = entry.title,
                    color = M8_GREEN,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "${if (entry.kind == RecentSongStore.Kind.PROJECT) "PROJECT" else "SONG"}  ${entry.location}",
                    color = M8_DIM,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentDetailPane(status: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, M8_DIM, RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Text(
            text = "RECENT FILES",
            color = M8_GREEN,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap a recent song/project to load it. Use tabs below for downloads, SD, or projects.",
            color = M8_DIM,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (!status.isNullOrBlank() && !status.equals("CURRENT FRESH SONG", ignoreCase = true)) {
            Spacer(Modifier.height(10.dp))
            Text(status, color = M8_GREEN, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<M8ProjectLibrary.SavedProject>,
    selected: M8ProjectLibrary.SavedProject?,
    onSelect: (M8ProjectLibrary.SavedProject) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(projects, key = { it.path }) { project ->
            val isSelected = selected?.path == project.path
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .background(if (isSelected) M8_CYAN.copy(alpha = 0.18f) else M8_CARD)
                    .border(1.dp, if (isSelected) M8_CYAN else AcademyTheme.BorderDim, RoundedCornerShape(3.dp))
                    .clickable { onSelect(project) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = project.songName,
                    color = M8_GREEN,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "${project.fileName}  ${project.tempo} BPM  ${humanSize(project.sizeBytes)}",
                    color = M8_DIM,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ProjectDetailPane(
    project: M8ProjectLibrary.SavedProject?,
    loadStatus: String?,
    onRefresh: () -> Unit,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, M8_DIM, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (project == null) {
            Text("Select a saved project", color = M8_DIM, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "[REFRESH]",
                color = M8_GREEN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            return@Column
        }
        Text(project.songName, color = M8_GREEN, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Field("file", project.fileName)
        Field("tempo", "${project.tempo} BPM")
        Field("size", humanSize(project.sizeBytes))
        Spacer(Modifier.height(10.dp))
        var projectName by remember(project.path) { mutableStateOf(project.fileName.removeSuffix(".m8droid")) }
        var confirmDelete by remember(project.path) { mutableStateOf(false) }
        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            label = { Text("Project name") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = M8_GREEN,
                unfocusedTextColor = M8_GREEN,
                focusedBorderColor = M8_GREEN,
                unfocusedBorderColor = M8_DIM,
                focusedLabelColor = M8_GREEN,
                unfocusedLabelColor = M8_DIM,
                cursorColor = M8_GREEN,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "[LOAD PROJECT]",
                color = M8_GREEN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                    .clickable { onLoad() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = "[REFRESH]",
                color = M8_GREEN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactButton("RENAME") { onRename(projectName) }
            CompactButton("DUPLICATE") { onDuplicate("${projectName}_copy") }
            CompactButton(if (confirmDelete) "CONFIRM DELETE" else "DELETE") {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        }
        if (confirmDelete) {
            Spacer(Modifier.height(4.dp))
            Text("Tap CONFIRM DELETE to remove this .m8droid file", color = Color(0xFFFFA040), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (!loadStatus.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(loadStatus, color = M8_GREEN, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SdDetailPane(
    entry: DownloadStore.Entry?,
    slotCount: Int,
    loadStatus: String?,
    previewStatus: String?,
    onLoad: (slot: Int) -> Unit,
    onLoadSong: () -> Unit,
    onPreviewSample: () -> Unit,
    onStopPreview: () -> Unit,
) {
    var selectedSlot by remember(entry?.id) { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, M8_DIM, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (entry == null) {
            Text(
                text = "Select an SD entry",
                color = M8_DIM,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            return@Column
        }

        Text(
            text = entry.title,
            color = M8_GREEN,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Field("sd", entry.sdPath)
        Field("type", entry.kind.name)
        entry.author?.let { Field("author", it) }
        Field("size", humanSize(entry.sizeBytes))
        Field("source", entry.sourceName)
        entry.license?.let { if (it.isNotBlank()) Field("license", it) }

        Spacer(Modifier.height(10.dp))

        when (entry.kind) {
            ContentKind.INSTRUMENT -> {
                Text(
                    text = "LOAD INTO SLOT",
                    color = M8_GREEN,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(slotCount) { i ->
                        val active = i == selectedSlot
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    1.dp,
                                    if (active) M8_GREEN else M8_DIM,
                                    RoundedCornerShape(3.dp),
                                )
                                .background(
                                    if (active) M8_GREEN.copy(alpha = 0.2f) else Color.Transparent,
                                )
                                .clickable { selectedSlot = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = i.toString(),
                                color = if (active) M8_GREEN else M8_DIM,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "[LOAD]",
                    color = M8_GREEN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                        .clickable { onLoad(selectedSlot) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            ContentKind.SONG -> {
                Text(
                    text = "[LOAD SONG]",
                    color = M8_GREEN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                        .clickable { onLoadSong() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            ContentKind.SAMPLE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "[PREVIEW]",
                        color = M8_GREEN,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                            .clickable { onPreviewSample() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "[STOP]",
                        color = M8_DIM,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .border(1.dp, M8_DIM, RoundedCornerShape(4.dp))
                            .clickable { onStopPreview() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            ContentKind.PACK -> NotYetNote("Pack unpack not implemented yet")
            else -> NotYetNote("No loader for this type yet")
        }

        if (!loadStatus.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            val isError = loadStatus.startsWith("ERROR", ignoreCase = true)
            Text(
                text = loadStatus,
                color = if (isError) Color(0xFFFF4040) else M8_GREEN,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!previewStatus.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            val isError = previewStatus.startsWith("ERROR", ignoreCase = true)
            Text(
                text = previewStatus,
                color = if (isError) Color(0xFFFF4040) else M8_DIM,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun NotYetNote(msg: String) {
    Text(
        text = msg,
        color = M8_DIM,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun folderLabel(kind: ContentKind): String = when (kind) {
    ContentKind.SONG -> "Songs"
    ContentKind.INSTRUMENT -> "Instruments"
    ContentKind.SAMPLE -> "Samples"
    ContentKind.THEME -> "Themes"
    ContentKind.SCALE -> "Scales"
    ContentKind.PACK -> "Packs"
    ContentKind.UNKNOWN -> "Other"
}

@Composable
private fun ItemList(
    items: List<RemoteItem>,
    selected: RemoteItem?,
    downloadedKeys: Set<String>,
    onSelect: (RemoteItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { remoteKey(it) }) { item ->
            val isSelected = selected?.let { remoteKey(it) == remoteKey(item) } == true
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(
                        if (isSelected) M8_CYAN.copy(alpha = 0.18f) else M8_CARD,
                    )
                    .border(
                        1.dp,
                        if (isSelected) M8_CYAN else AcademyTheme.BorderDim,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = kindGlyph(item.kind),
                        color = M8_GREEN,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp),
                    )
                    Text(
                        text = item.title,
                        color = M8_GREEN,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (downloadedKeys.contains(remoteKey(item))) {
                        Text(
                            text = "DOWNLOADED",
                            color = M8_GREEN,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .border(1.dp, M8_GREEN.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                    }
                }
                if (!item.author.isNullOrBlank()) {
                    Text(
                        text = "by ${item.author}",
                        color = M8_DIM,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPane(
    item: RemoteItem?,
    downloading: Boolean,
    lastDownloaded: com.m8droid.browse.DownloadStore.Entry?,
    loadStatus: String?,
    slotCount: Int,
    onDownload: () -> Unit,
    onDownloadAndLoadSong: () -> Unit,
    onDownloadAndLoadInstrument: (slot: Int) -> Unit,
    onDismissResult: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, M8_DIM, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (item == null) {
            Text(
                text = "Select an item",
                color = M8_DIM,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            return@Column
        }

        Text(
            text = item.title,
            color = M8_GREEN,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Field("type", item.kind.name)
        item.author?.let { Field("author", it) }
        item.license?.let { Field("license", it) }
        item.sizeBytes?.let { Field("size", humanSize(it)) }
        item.downloadCount?.let { Field("downloads", it.toString()) }
        item.createdAt?.let { Field("date", it) }
        if (item.tags.isNotEmpty()) Field("tags", item.tags.joinToString(", "))
        if (!item.description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.description,
                color = M8_DIM,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(10.dp))

        val downloadedEntry = lastDownloaded?.takeIf { it.id == item.id && it.sourceName == item.sourceName && it.kind == item.kind }
        if (downloadedEntry != null) {
            Text(
                text = "DOWNLOADED",
                color = M8_GREEN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = downloadedEntry.sdPath,
                color = M8_GREEN,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Songs get a "save + load in one click" action; instruments
        // get a slot picker + save+load; everything else uses plain download.
        val isSong = item.kind == ContentKind.SONG
        val isInst = item.kind == ContentKind.INSTRUMENT
        when {
            isSong -> {
                val label = when {
                    downloading -> "LOADING..."
                    downloadedEntry != null -> "[LOAD DOWNLOADED]"
                    else -> "[SAVE + LOAD]"
                }
                Text(
                    text = label,
                    color = M8_GREEN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                        .clickable(enabled = !downloading) { onDownloadAndLoadSong() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            isInst -> {
                var selectedSlot by remember(item.id) { mutableStateOf(0) }
                Text(
                    text = if (downloadedEntry != null) "LOAD DOWNLOADED INTO SLOT" else "SAVE + LOAD INTO SLOT",
                    color = M8_GREEN,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(slotCount) { i ->
                        val active = i == selectedSlot
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    1.dp,
                                    if (active) M8_GREEN else M8_DIM,
                                    RoundedCornerShape(3.dp),
                                )
                                .background(
                                    if (active) M8_GREEN.copy(alpha = 0.2f) else Color.Transparent,
                                )
                                .clickable { selectedSlot = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = i.toString(),
                                color = if (active) M8_GREEN else M8_DIM,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val label = when {
                    downloading -> "LOADING..."
                    downloadedEntry != null -> "[LOAD DOWNLOADED]"
                    else -> "[SAVE + LOAD]"
                }
                Text(
                    text = label,
                    color = M8_GREEN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                        .clickable(enabled = !downloading) { onDownloadAndLoadInstrument(selectedSlot) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            else -> {
                val label = when {
                    downloading -> "DOWNLOADING..."
                    downloadedEntry != null -> "[DOWNLOADED]"
                    else -> "[DOWNLOAD]"
                }
                Text(
                    text = label,
                    color = M8_GREEN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                        .clickable(enabled = !downloading && downloadedEntry == null) { onDownload() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (!loadStatus.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            val isError = loadStatus.startsWith("ERROR", ignoreCase = true)
            Text(
                text = loadStatus,
                color = if (isError) Color(0xFFFF4040) else M8_GREEN,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(
            text = "$label:",
            color = M8_DIM,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            color = M8_GREEN,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun entryKey(entry: DownloadStore.Entry): String = "${entry.sourceName}|${entry.kind.name}|${entry.id}"

private fun remoteKey(item: RemoteItem): String = "${item.sourceName}|${item.kind.name}|${item.id}"

private fun kindGlyph(kind: ContentKind): String = when (kind) {
    ContentKind.SONG -> "SONG"
    ContentKind.INSTRUMENT -> "INST"
    ContentKind.THEME -> "THEM"
    ContentKind.SCALE -> "SCAL"
    ContentKind.SAMPLE -> "WAV "
    ContentKind.PACK -> "PACK"
    ContentKind.UNKNOWN -> "?   "
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}