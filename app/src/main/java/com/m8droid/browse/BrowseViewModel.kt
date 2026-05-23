package com.m8droid.browse

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8iParser
import com.m8droid.emulator.M8sParser
import com.m8droid.audio.SampleCache
import com.m8droid.audio.SamplePreviewPlayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns state for the Browse/Download dialog: which source is active,
 * items fetched, currently-selected item, and in-flight loading/download
 * status. One source is "current" at a time; switching tabs kicks a new
 * fetch.
 */
class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val http = HttpClient()
    private val store = DownloadStore(application)
    private val sampleCache = SampleCache(File(application.filesDir, "m8sd"))
    private val samplePreviewPlayer = SamplePreviewPlayer()

    val sources: List<ContentSource> = DownloadSources.create(http)

    private val _currentSourceIndex = MutableStateFlow(0)
    val currentSourceIndex: StateFlow<Int> = _currentSourceIndex.asStateFlow()

    private val _items = MutableStateFlow<List<RemoteItem>>(emptyList())
    val items: StateFlow<List<RemoteItem>> = _items.asStateFlow()

    private val _selected = MutableStateFlow<RemoteItem?>(null)
    val selected: StateFlow<RemoteItem?> = _selected.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _lastDownloaded = MutableStateFlow<DownloadStore.Entry?>(null)
    val lastDownloaded: StateFlow<DownloadStore.Entry?> = _lastDownloaded.asStateFlow()

    // SD tab state — index == sources.size means "showing local SD card".
    val sdTabIndex: Int = sources.size
    val projectTabIndex: Int = sources.size + 1

    private val _sdEntries = MutableStateFlow<List<DownloadStore.Entry>>(emptyList())
    val sdEntries: StateFlow<List<DownloadStore.Entry>> = _sdEntries.asStateFlow()

    val isViewingSd: Boolean get() = _currentSourceIndex.value == sdTabIndex

    val sdRootPath: String get() = store.rootPath

    fun selectSource(index: Int) {
        if (index == _currentSourceIndex.value) return
        if (index !in 0..projectTabIndex) return
        _currentSourceIndex.value = index
        _selected.value = null
        _sdSelected.value = null
        when (index) {
            sdTabIndex -> refreshSd()
            projectTabIndex -> {
                _loading.value = false
                _error.value = null
                _items.value = emptyList()
            }
            else -> refresh()
        }
    }

    private fun refreshSd() {
        _loading.value = false
        _error.value = null
        _items.value = emptyList()
        refreshDownloadedEntries()
    }

    private fun refreshDownloadedEntries() {
        _sdEntries.value = store.list().sortedByDescending { it.downloadedAt }
    }

    fun refresh() {
        val src = sources.getOrNull(_currentSourceIndex.value) ?: return
        refreshDownloadedEntries()
        _loading.value = true
        _error.value = null
        _items.value = emptyList()
        viewModelScope.launch {
            val result = runCatching { src.fetchItems() }
            result.onSuccess { _items.value = it }
                .onFailure {
                    Log.w("BrowseViewModel", "fetch failed for ${src.displayName}", it)
                    _error.value = it.message ?: "Failed to load ${src.displayName}"
                }
            _loading.value = false
        }
    }

    fun select(item: RemoteItem?) {
        _selected.value = item
    }

    fun downloadSelected() {
        val item = _selected.value ?: return
        _downloading.value = true
        _error.value = null
        _loadStatus.value = null
        viewModelScope.launch {
            val result = runCatching {
                val existing = withContext(Dispatchers.IO) { store.findExisting(item) }
                if (existing != null) {
                    existing to true
                } else {
                    store.save(item, http.getBytes(item.downloadUrl)) to false
                }
            }
            result.onSuccess { (entry, reused) ->
                _lastDownloaded.value = entry
                // Keep the SD list warm so the SD tab is current next time it opens.
                refreshDownloadedEntries()
                val prefix = if (reused) "USING DOWNLOADED" else "DOWNLOADED"
                _loadStatus.value = "$prefix ${entry.sdPath}"
            }
                .onFailure {
                    Log.w("BrowseViewModel", "download failed for ${item.title}", it)
                    _error.value = "Download failed: ${it.message}"
                }
            _downloading.value = false
        }
    }

    fun clearLastDownloaded() {
        _lastDownloaded.value = null
    }

    // --- SD tab: local entry selection + instrument load flow ---

    private val _sdSelected = MutableStateFlow<DownloadStore.Entry?>(null)
    val sdSelected: StateFlow<DownloadStore.Entry?> = _sdSelected.asStateFlow()

    private val _loadStatus = MutableStateFlow<String?>(null)
    val loadStatus: StateFlow<String?> = _loadStatus.asStateFlow()

    private val _previewStatus = MutableStateFlow<String?>(null)
    val previewStatus: StateFlow<String?> = _previewStatus.asStateFlow()

    fun selectSdEntry(entry: DownloadStore.Entry?) {
        _sdSelected.value = entry
        _loadStatus.value = null
        _previewStatus.value = null
    }

    fun previewSample(entry: DownloadStore.Entry) {
        if (entry.kind != ContentKind.SAMPLE) {
            _previewStatus.value = "ERROR: not a sample"
            return
        }
        _previewStatus.value = "PREVIEWING ${entry.title}"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { sampleCache.load(entry.sdPath) }
            }
            val sample = result.getOrNull()
            if (sample == null) {
                _previewStatus.value = "ERROR: WAV preview failed"
            } else {
                samplePreviewPlayer.play(sample)
                _previewStatus.value = "PLAYING ${entry.sdPath}"
            }
        }
    }

    fun stopSamplePreview() {
        samplePreviewPlayer.stop()
        _previewStatus.value = "PREVIEW STOPPED"
    }

    /**
     * M8Instrument to [apply] (typically M8ViewModel.replaceInstrument).
     * Status / errors surface through [loadStatus].
     */
    fun loadInstrumentIntoSlot(
        entry: DownloadStore.Entry,
        slot: Int,
        apply: (Int, M8Instrument) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) { File(entry.localPath).readBytes() }
                val inst = M8iParser.parse(bytes)
                apply(slot, inst)
                "LOADED '${inst.name}' -> SLOT $slot"
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "parse failed"}" }
        }
    }

    /**
     * Parse an .m8s file already on the SD store and hand the result to
     * [apply] (typically M8ViewModel::replaceSong). Status / errors surface
     * through [loadStatus], same as instrument loads.
     */
    fun loadSongFromEntry(
        entry: DownloadStore.Entry,
        apply: (M8sParser.ParsedSong, String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) { File(entry.localPath).readBytes() }
                val song = M8sParser.parse(bytes)
                apply(song, entry.localPath)
                songLoadStatus(song)
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "parse failed"}" }
        }
    }

    private fun songLoadStatus(song: M8sParser.ParsedSong): String {
        val base = "LOADED '${song.header.name}' @ ${song.header.tempo} BPM"
        return if (song.warnings.isEmpty()) base else base + "\nWARN: " + song.warnings.joinToString(" | ")
    }

    /**
     * parse + load it into a slot. One-click flow for instruments.
     */
    fun downloadAndLoadInstrument(
        item: RemoteItem,
        slot: Int,
        apply: (Int, M8Instrument) -> Unit,
    ) {
        _downloading.value = true
        _error.value = null
        _loadStatus.value = null
        viewModelScope.launch {
            val result = runCatching {
                val existing = withContext(Dispatchers.IO) { store.findExisting(item) }
                val bytes = if (existing != null) {
                    withContext(Dispatchers.IO) { File(existing.localPath).readBytes() }
                } else {
                    http.getBytes(item.downloadUrl)
                }
                val entry = existing ?: withContext(Dispatchers.IO) { store.save(item, bytes) }
                _lastDownloaded.value = entry
                refreshDownloadedEntries()
                val inst = M8iParser.parse(bytes)
                apply(slot, inst)
                val prefix = if (existing != null) "USING DOWNLOADED" else "LOADED"
                "$prefix '${inst.name}' -> SLOT $slot"
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "load failed"}" }
            _downloading.value = false
        }
    }

    /**
     * Download a remote .m8s, save it to the SD store, and immediately
     * parse + apply it. This is the "one-click" load flow: the user
     * picks a song in the browser and hears it without a separate
     * download-then-load step.
     */
    fun downloadAndLoadSong(
        item: RemoteItem,
        apply: (M8sParser.ParsedSong, String?) -> Unit,
    ) {
        _downloading.value = true
        _error.value = null
        _loadStatus.value = null
        viewModelScope.launch {
            val result = runCatching {
                val existing = withContext(Dispatchers.IO) { store.findExisting(item) }
                val bytes = if (existing != null) {
                    withContext(Dispatchers.IO) { File(existing.localPath).readBytes() }
                } else {
                    http.getBytes(item.downloadUrl)
                }
                val entry = existing ?: withContext(Dispatchers.IO) { store.save(item, bytes) }
                _lastDownloaded.value = entry
                refreshDownloadedEntries()
                val song = M8sParser.parse(bytes)
                apply(song, entry.localPath)
                val prefix = if (existing != null) "USING DOWNLOADED" else "DOWNLOADED"
                "$prefix ${entry.sdPath}\n${songLoadStatus(song)}"
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "load failed"}" }
            _downloading.value = false
        }
    }
}
