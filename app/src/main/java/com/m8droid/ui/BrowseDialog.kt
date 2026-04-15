package com.m8droid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m8droid.browse.BrowseViewModel
import com.m8droid.browse.ContentKind
import com.m8droid.browse.DownloadStore
import com.m8droid.browse.RemoteItem
import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8sParser

private val M8_GREEN = Color(0xFF00FF00)
private val M8_BG = Color(0xFF0A0A1A)
private val M8_BG_DIM = Color(0xCC0A0A1A)
private val M8_DIM = Color(0xFF008800)

@Composable
fun BrowseDialog(
    onDismiss: () -> Unit,
    slotCount: Int,
    onLoadInstrument: (slot: Int, inst: M8Instrument) -> Unit,
    onLoadSong: (M8sParser.ParsedSong) -> Unit,
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
    val viewingSd = sourceIndex == viewModel.sdTabIndex

    // First-open fetch.
    LaunchedEffect(Unit) { if (items.isEmpty() && !loading) viewModel.refresh() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(M8_BG, RoundedCornerShape(8.dp))
                .border(1.dp, M8_GREEN, RoundedCornerShape(8.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LOAD",
                        color = M8_GREEN,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "X",
                        color = M8_GREEN,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .size(28.dp)
                            .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                            .clickable { onDismiss() }
                            .wrapContentSize(Alignment.Center),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Source tabs (remote sources + local SD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val tabs = sources.map { it.displayName } + "SD"
                    tabs.forEachIndexed { i, label ->
                        val active = i == sourceIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.dp,
                                    if (active) M8_GREEN else M8_DIM,
                                    RoundedCornerShape(4.dp),
                                )
                                .background(
                                    if (active) M8_GREEN.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { viewModel.selectSource(i) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (active) M8_GREEN else M8_DIM,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Body: list (left) + detail (right) in a Row, stacked on narrow screens
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.weight(1f)) {
                        val desc = if (viewingSd) "Virtual M8 SD card at ${viewModel.sdRootPath}"
                                   else sources.getOrNull(sourceIndex)?.description.orEmpty()
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
                                onSelect = { viewModel.select(it) },
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (viewingSd) {
                            SdDetailPane(
                                entry = sdSelected,
                                slotCount = slotCount,
                                loadStatus = loadStatus,
                                onLoad = { slot ->
                                    val e = sdSelected ?: return@SdDetailPane
                                    viewModel.loadInstrumentIntoSlot(e, slot, onLoadInstrument)
                                },
                                onLoadSong = {
                                    val e = sdSelected ?: return@SdDetailPane
                                    viewModel.loadSongFromEntry(e, onLoadSong)
                                },
                            )
                        } else {
                            DetailPane(
                                item = selected,
                                downloading = downloading,
                                lastDownloaded = lastDownloaded,
                                loadStatus = loadStatus,
                                onDownload = { viewModel.downloadSelected() },
                                onDownloadAndLoadSong = {
                                    val it = selected ?: return@DetailPane
                                    viewModel.downloadAndLoadSong(it, onLoadSong)
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
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "NO ITEMS",
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
            items(list, key = { e -> e.sourceName + "|" + e.id }) { e ->
                val isSelected = selected?.id == e.id && selected.sourceName == e.sourceName
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .background(
                            if (isSelected) M8_GREEN.copy(alpha = 0.2f) else Color.Transparent,
                        )
                        .border(
                            1.dp,
                            if (isSelected) M8_GREEN else M8_DIM.copy(alpha = 0.4f),
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
private fun SdDetailPane(
    entry: DownloadStore.Entry?,
    slotCount: Int,
    loadStatus: String?,
    onLoad: (slot: Int) -> Unit,
    onLoadSong: () -> Unit,
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
            ContentKind.SAMPLE -> NotYetNote("Sample playback needs engine work")
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
    onSelect: (RemoteItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.sourceName + "|" + it.id }) { item ->
            val isSelected = selected?.id == item.id && selected.sourceName == item.sourceName
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(
                        if (isSelected) M8_GREEN.copy(alpha = 0.2f) else Color.Transparent,
                    )
                    .border(
                        1.dp,
                        if (isSelected) M8_GREEN else M8_DIM.copy(alpha = 0.4f),
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
    onDownload: () -> Unit,
    onDownloadAndLoadSong: () -> Unit,
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

        if (lastDownloaded != null && lastDownloaded.id == item.id) {
            Text(
                text = "SAVED TO SD",
                color = M8_GREEN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = lastDownloaded.sdPath,
                color = M8_GREEN,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "[OK]",
                color = M8_GREEN,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .border(1.dp, M8_GREEN, RoundedCornerShape(4.dp))
                    .clickable { onDismissResult() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            // Songs get a "save + load in one click" action; everything
            // else still uses plain download-to-SD.
            val isSong = item.kind == ContentKind.SONG
            val label = when {
                downloading && isSong -> "LOADING..."
                downloading -> "DOWNLOADING..."
                isSong -> "[SAVE + LOAD]"
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
                    .clickable(enabled = !downloading) {
                        if (isSong) onDownloadAndLoadSong() else onDownload()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
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
