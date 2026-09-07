package io.github.shiaho777.logsleuth.app.ui.stream

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.AccessState
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.FilterEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.RecordService
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import io.github.shiaho777.logsleuth.app.service.RecordingState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A log line with a stable identity for LazyColumn keys. */
data class UiLogEntry(val seq: Long, val entry: LogcatEntry)

data class AppChoice(val packageName: String, val label: String)

data class StreamUiState(
    val access: AccessState? = null,
    val engineState: LogcatEngine.State = LogcatEngine.State.STOPPED,
    val filter: LogFilter = LogFilter.DEFAULT,
    val regexInvalid: Boolean = false,
    val entries: List<UiLogEntry> = emptyList(),
    val paused: Boolean = false,
    val pausedIncoming: Int = 0,
    val recording: RecordingState = RecordingState(),
    val apps: List<AppChoice> = emptyList(),
    val presets: List<LogFilter> = emptyList(),
    val searching: Boolean = false,
    val searchQuery: String = "",
    /** Indices into [entries] that match the search query. */
    val searchHits: List<Int> = emptyList(),
    val searchHitIndex: Int = -1,
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val engine: LogcatEngine,
    private val recordingManager: RecordingManager,
    private val filterDao: FilterDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(StreamUiState())
    val ui: StateFlow<StreamUiState> = _ui.asStateFlow()

    private val listMutex = Mutex()
    private val all = ArrayList<UiLogEntry>()
    private var visible = ArrayList<UiLogEntry>()
    private var seq = 0L
    private var bufferCap = 20_000
    private val pending = ArrayList<LogcatEntry>()

    init {
        engine.refreshAccess()
        engine.acquireClient()

        viewModelScope.launch {
            settingsRepository.settings.collect { bufferCap = it.bufferSize }
        }
        viewModelScope.launch { engine.access.collect { a -> _ui.update { it.copy(access = a) } } }
        viewModelScope.launch { engine.state.collect { s -> _ui.update { it.copy(engineState = s) } } }
        viewModelScope.launch {
            recordingManager.state.collect { r -> _ui.update { it.copy(recording = r) } }
        }
        viewModelScope.launch {
            filterDao.observeAll().collect { list ->
                _ui.update { it.copy(presets = list.map(::toModel)) }
            }
        }
        viewModelScope.launch { loadInstalledApps() }

        // Collector: stage entries; a ticker publishes them in ~120ms batches
        // so a busy logcat cannot trigger a recompose per line.
        viewModelScope.launch {
            snapshotToLists()
            engine.entries.collect { entry ->
                stagedMutex.withLock { staged.add(entry) }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(120)
                drainStaged()
            }
        }
    }

    private val staged = ArrayList<LogcatEntry>(512)
    private val stagedMutex = Mutex()

    private suspend fun snapshotToLists() {
        val snapshot = engine.snapshot()
        val compiled = currentCompiled()
        listMutex.withLock {
            all.clear()
            visible.clear()
            snapshot.forEach { e ->
                val uiEntry = UiLogEntry(seq++, e)
                all.add(uiEntry)
                if (compiled.matches(e)) visible.add(uiEntry)
            }
            trimLocked()
            publishLocked()
        }
    }

    private suspend fun drainStaged() {
        val batch = stagedMutex.withLock {
            if (staged.isEmpty()) return
            ArrayList(staged).also { staged.clear() }
        }
        val compiled = currentCompiled()
        val pausedNow = _ui.value.paused
        var addedVisible = 0
        listMutex.withLock {
            for (e in batch) {
                if (pausedNow) {
                    pending.add(e)
                    continue
                }
                val uiEntry = UiLogEntry(seq++, e)
                all.add(uiEntry)
                if (compiled.matches(e)) {
                    visible.add(uiEntry)
                    addedVisible++
                }
            }
            trimLocked()
            if (!pausedNow && addedVisible > 0) publishLocked()
        }
        if (pausedNow) {
            _ui.update { it.copy(pausedIncoming = it.pausedIncoming + batch.size) }
        }
    }

    private fun trimLocked() {
        while (all.size > bufferCap) {
            val removed = all.removeAt(0)
            val idx = visible.indexOfFirst { it.seq == removed.seq }
            if (idx >= 0) visible.removeAt(idx)
        }
    }

    /** Call with [listMutex] held. */
    private fun publishLocked() {
        val snapshot = ArrayList(visible)
        _ui.update { it.copy(entries = snapshot) }
    }

    private fun currentCompiled(): CompiledFilter =
        CompiledFilter(_ui.value.filter, resolveUid(_ui.value.filter.packageName))

    // ---------------- user actions ----------------

    fun setFilter(filter: LogFilter) {
        _ui.update { it.copy(filter = filter, regexInvalid = false) }
        viewModelScope.launch { refilter() }
    }

    fun setPaused(paused: Boolean) {
        _ui.update { it.copy(paused = paused) }
        if (!paused) {
            viewModelScope.launch {
                val compiled = currentCompiled()
                listMutex.withLock {
                    for (e in pending) {
                        val uiEntry = UiLogEntry(seq++, e)
                        all.add(uiEntry)
                        if (compiled.matches(e)) visible.add(uiEntry)
                    }
                    pending.clear()
                    trimLocked()
                    publishLocked()
                }
                _ui.update { it.copy(pausedIncoming = 0) }
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            engine.clearBuffer()
            listMutex.withLock {
                all.clear()
                visible.clear()
                pending.clear()
                publishLocked()
            }
            _ui.update { it.copy(pausedIncoming = 0, searchHits = emptyList()) }
        }
    }

    fun toggleRecording() {
        val recording = _ui.value.recording.isRecording
        val intent = Intent(context, RecordService::class.java).apply {
            action = if (recording) RecordService.ACTION_STOP else RecordService.ACTION_START
            if (!recording) putExtra(RecordService.EXTRA_FILTER_PACKAGE, _ui.value.filter.packageName)
        }
        if (recording) context.startService(intent)
        else androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            filterDao.insert(toEntity(_ui.value.filter.copy(name = name)))
        }
    }

    fun applyPreset(preset: LogFilter) = setFilter(preset.copy(id = preset.id, name = preset.name))

    fun deletePreset(preset: LogFilter) {
        viewModelScope.launch { filterDao.delete(toEntity(preset)) }
    }

    // Search
    fun setSearching(searching: Boolean) {
        _ui.update { it.copy(searching = searching, searchQuery = "", searchHits = emptyList(), searchHitIndex = -1) }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        viewModelScope.launch(Dispatchers.Default) {
            val hits = if (query.isBlank()) {
                emptyList()
            } else {
                listMutex.withLock {
                    visible.mapIndexedNotNull { index, uiEntry ->
                        val e = uiEntry.entry
                        if (e.tag.contains(query, true) || e.message.contains(query, true)) index else null
                    }
                }
            }
            _ui.update { it.copy(searchHits = hits, searchHitIndex = if (hits.isEmpty()) -1 else 0) }
        }
    }

    fun nextSearchHit(direction: Int) {
        _ui.update { state ->
            if (state.searchHits.isEmpty()) state
            else state.copy(
                searchHitIndex = Math.floorMod(state.searchHitIndex + direction, state.searchHits.size),
            )
        }
    }

    private suspend fun refilter() {
        val compiled = CompiledFilter(_ui.value.filter, resolveUid(_ui.value.filter.packageName))
        listMutex.withLock {
            visible = all.filterTo(ArrayList()) { compiled.matches(it.entry) }
            publishLocked()
        }
        _ui.update { it.copy(regexInvalid = compiled.regexInvalid) }
    }

    private suspend fun loadInstalledApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
                val info: ApplicationInfo = ri.activityInfo.applicationInfo
                AppChoice(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                )
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
        _ui.update { it.copy(apps = apps) }
    }

    private fun resolveUid(packageName: String?): Int? {
        if (packageName == null) return null
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull()
    }

    override fun onCleared() {
        engine.releaseClient()
        super.onCleared()
    }

    private fun toEntity(f: LogFilter) = FilterEntity(
        id = f.id,
        name = f.name,
        minLevel = f.minLevel.name,
        query = f.query,
        excludeQuery = f.excludeQuery,
        tagQuery = f.tagQuery,
        useRegex = f.useRegex,
        packageName = f.packageName,
    )

    private fun toModel(e: FilterEntity) = LogFilter(
        id = e.id,
        name = e.name,
        minLevel = io.github.shiaho777.logsleuth.app.core.logcat.LogLevel.valueOf(e.minLevel),
        query = e.query,
        excludeQuery = e.excludeQuery,
        tagQuery = e.tagQuery,
        useRegex = e.useRegex,
        packageName = e.packageName,
    )
}
