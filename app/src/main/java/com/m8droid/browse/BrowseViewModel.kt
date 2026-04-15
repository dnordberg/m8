package com.m8droid.browse

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8iParser
import com.m8droid.emulator.M8sParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns state for the Browse/Download dialog: which source is active,
 * items fetched, currently-selected item, and in-flight loading/download
 * status. One source is "current" at a time; switching tabs kicks a new
 * fetch.
 */
class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val http = HttpClient()
    private val store = DownloadStore(application)

    val sources: List<ContentSource> = listOf(
        GitHubSource(http),
        PatchstorageSource(http),
        ArchiveOrgSource(http),
    )

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

    private val _sdEntries = MutableStateFlow<List<DownloadStore.Entry>>(emptyList())
    val sdEntries: StateFlow<List<DownloadStore.Entry>> = _sdEntries.asStateFlow()

    val isViewingSd: Boolean get() = _currentSourceIndex.value == sdTabIndex

    val sdRootPath: String get() = store.rootPath

    fun selectSource(index: Int) {
        if (index == _currentSourceIndex.value) return
        if (index !in 0..sdTabIndex) return
        _currentSourceIndex.value = index
        _selected.value = null
        if (index == sdTabIndex) refreshSd() else refresh()
    }

    private fun refreshSd() {
        _loading.value = false
        _error.value = null
        _items.value = emptyList()
        _sdEntries.value = store.list().sortedByDescending { it.downloadedAt }
    }

    fun refresh() {
        val src = sources.getOrNull(_currentSourceIndex.value) ?: return
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
        viewModelScope.launch {
            val result = runCatching {
                val bytes = http.getBytes(item.downloadUrl)
                store.save(item, bytes)
            }
            result.onSuccess {
                _lastDownloaded.value = it
                // Keep the SD list warm so the SD tab is current next time it opens.
                _sdEntries.value = store.list().sortedByDescending { e -> e.downloadedAt }
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

    fun selectSdEntry(entry: DownloadStore.Entry?) {
        _sdSelected.value = entry
        _loadStatus.value = null
    }

    /**
     * Read a downloaded .m8i file, parse it, and hand the resulting
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
        apply: (M8sParser.ParsedSong) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) { File(entry.localPath).readBytes() }
                val song = M8sParser.parse(bytes)
                apply(song)
                "LOADED '${song.header.name}' @ ${song.header.tempo} BPM"
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "parse failed"}" }
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
        apply: (M8sParser.ParsedSong) -> Unit,
    ) {
        _downloading.value = true
        _error.value = null
        _loadStatus.value = null
        viewModelScope.launch {
            val result = runCatching {
                val bytes = http.getBytes(item.downloadUrl)
                val entry = withContext(Dispatchers.IO) { store.save(item, bytes) }
                _lastDownloaded.value = entry
                _sdEntries.value = store.list().sortedByDescending { it.downloadedAt }
                val song = M8sParser.parse(bytes)
                apply(song)
                "LOADED '${song.header.name}' @ ${song.header.tempo} BPM"
            }
            _loadStatus.value = result.getOrElse { "ERROR: ${it.message ?: "load failed"}" }
            _downloading.value = false
        }
    }
}
