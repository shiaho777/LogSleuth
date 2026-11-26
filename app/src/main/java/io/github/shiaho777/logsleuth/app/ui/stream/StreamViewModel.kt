package io.github.shiaho777.logsleuth.app.ui.stream

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.apps.groupEntriesByApp
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.data.db.toEntity
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.AccessState
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.matchesLogSearch
import io.github.shiaho777.logsleuth.app.core.logcat.retainedSearchHitIndex
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.RecordService
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import io.github.shiaho777.logsleuth.app.service.RecordingState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A log line with a stable identity for LazyColumn keys. */
data class UiLogEntry(val seq: Long, val entry: LogcatEntry)

/** Debounce for re-indexing search hits while the stream appends. */
private const val HITS_REFRESH_MS = 250L

data class StreamUiState(
    val access: AccessState? = null,
    val remoteEndpoint: String? = null,
    val engineState: LogcatEngine.State = LogcatEngine.State.STOPPED,
    val filter: LogFilter = LogFilter.DEFAULT,
    val regexInvalid: Boolean = false,
    val paused: Boolean = false,
    val pausedIncoming: Int = 0,
    val recording: RecordingState = RecordingState(),
    val apps: List<AppChoice> = emptyList(),
    val presets: List<LogFilter> = emptyList(),
    val searching: Boolean = false,
    val searchQuery: String = "",
    /**
     * Stable row ids ([UiLogEntry.seq]) that match the search query. Ids, not
     * list positions: a ring-buffer trim shifts positions but not ids.
     */
    val searchHits: List<Long> = emptyList(),
    val searchHitIndex: Int = -1,
    val searchPending: Boolean = false,
    /** Session just finished — prompt the user to share it. */
    val finishedSession: io.github.shiaho777.logsleuth.app.data.db.SessionEntity? = null,
    /** [finishedSession] came from a save-to-session snapshot, not a recording. */
    val finishedSnapshot: Boolean = false,
    /** How many of [finishedSession]'s lines were buffered before start. */
    val finishedBackfill: Long = 0,
    /** Floating recording controls are shown over other apps. */
    val bubbleEnabled: Boolean = false,
    /** Last stream failure detail (e.g. "logcat exited 1: Permission
     *  denied"); null while healthy or when access is simply not granted. */
    val lastError: String? = null,
    /**
     * Android 13+ is holding a READ_LOGS start until the app is on screen.
     * Kept apart from [lastError] so the sentinel is never shown raw.
     */
    val adbHeld: Boolean = false,
    /** Bumped when a filter is created from a log line, so the screen can toast. */
    val filterNotice: Int = 0,
    val searchCaseSensitive: Boolean = false,
    /** Buffer has lines but the active filter hides every one. */
    val filteredOut: Boolean = false,
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val engine: LogcatEngine,
    private val recordingManager: RecordingManager,
    private val filterDao: FilterDao,
    private val settingsRepository: SettingsRepository,
    private val sessionDao: io.github.shiaho777.logsleuth.app.data.db.SessionDao,
    private val exporter: io.github.shiaho777.logsleuth.app.core.export.SessionExporter,
    private val installedApps: InstalledApps,
) : ViewModel() {

    private val _ui = MutableStateFlow(StreamUiState())
    val ui: StateFlow<StreamUiState> = _ui.asStateFlow()

    /** Owns the three lists; its edits are mirrored onto [entries] on the main thread. */
    private val pipeline = LogListPipeline(
        emit = { edits.trySend(it) },
        onPausedIncoming = { n -> _ui.update { it.copy(pausedIncoming = n) } },
        onRegexInvalid = { invalid -> _ui.update { it.copy(regexInvalid = invalid) } },
    )

    /**
     * What the list draws. Appends and prefix drops land here; the screen
     * reads it directly so a new line does not copy the buffer or rebuild
     * the bars around the list. Mutated only on the main thread.
     */
    val entries: SnapshotStateList<UiLogEntry> = mutableStateListOf()

    /** Edits queued by [pipeline], applied in that order on the main thread. */
    private val edits = Channel<ListEdit>(Channel.UNLIMITED)

    private var searchJob: Job? = null
    private var searchRefreshRequested = false

    // Declared before init: SharedFlow emissions run on the emitter's
    // thread, so the collector below can touch these while init is still
    // running — a post-init declaration reads as null there (NPE crash).
    private val staged = ArrayList<LogcatEntry>(512)
    private val stagedMutex = Mutex()
    private val stagedSignal = Channel<Unit>(Channel.CONFLATED)

    /** Enabled saved rules. AND-ed with the quick filter on the bar. */
    @Volatile
    private var enabledStack = FilterStack.PASS

    init {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            for (edit in edits) applyEdit(edit)
        }
        engine.refreshAccess()
        engine.acquireClient()

        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                pipeline.setCap(s.bufferSize)
                val caseChanged = s.searchCaseSensitive != _ui.value.searchCaseSensitive
                _ui.update {
                    it.copy(
                        bubbleEnabled = s.bubbleEnabled,
                        searchCaseSensitive = s.searchCaseSensitive,
                    )
                }
                if (caseChanged) requestSearch(reset = true, delayMs = 0L)
            }
        }
        viewModelScope.launch { engine.access.collect { a -> _ui.update { it.copy(access = a) } } }
        viewModelScope.launch { engine.state.collect { s -> _ui.update { it.copy(engineState = s) } } }
        viewModelScope.launch {
            engine.lastError.collect { e ->
                val held = e == LogcatEngine.ADB_NEEDS_FOREGROUND
                _ui.update { it.copy(lastError = if (held) null else e, adbHeld = held) }
            }
        }
        viewModelScope.launch {
            var wasRecording = false
            var lastSessionId: Long? = null
            var lastBackfill = 0L
            recordingManager.state.collect { r ->
                if (r.isRecording) {
                    lastSessionId = r.sessionId
                    lastBackfill = r.backfillCount
                }
                _ui.update { it.copy(recording = r) }
                if (wasRecording && !r.isRecording && lastSessionId != null) {
                    val id = lastSessionId
                    val backfill = lastBackfill
                    lastSessionId = null
                    lastBackfill = 0L
                    // stop() writes the session row before it clears this state.
                    val session = id?.let { sessionDao.getById(it) }
                    if (session != null) {
                        _ui.update {
                            it.copy(finishedSession = session, finishedBackfill = backfill)
                        }
                    }
                }
                wasRecording = r.isRecording
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            filterDao.observeAll().collect { list ->
                val models = list.map { it.toLogFilter() }
                enabledStack = FilterStack.compile(models, ::resolveUid)
                _ui.update { it.copy(presets = models) }
                refilter()
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            engine.access.map { it.remoteEndpoint }.distinctUntilChanged().collectLatest { endpoint ->
                val apps = installedApps.loadForCapture(endpoint?.let(engine::remotePackages))
                var snapshotSeq = 0L
                engine.entries.onSubscription {
                    stagedMutex.withLock {
                        val snap = engine.snapshot(endpoint)
                        snapshotSeq = snap.maxSeq
                        staged.clear()
                        _ui.update {
                            it.copy(remoteEndpoint = endpoint, apps = apps)
                        }
                        pipeline.snapshot(snap.entries, ::currentRules)
                    }
                }.collect { entry ->
                    if (entry.remoteEndpoint != endpoint || entry.seq <= snapshotSeq) return@collect
                    stagedMutex.withLock { staged.add(entry) }
                    stagedSignal.trySend(Unit)
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                stagedSignal.receive()
                delay(16)
                while (stagedSignal.tryReceive().isSuccess) Unit
                drainStaged()
            }
        }
    }

    private suspend fun drainStaged() {
        stagedMutex.withLock {
            if (staged.isEmpty()) return
            val batch = ArrayList(staged)
            staged.clear()
            pipeline.append(batch, ::currentRules)
        }
    }

    /** Main thread. [filteredOut] is written only when it changes. */
    private fun applyEdit(edit: ListEdit) {
        when (edit) {
            is ListEdit.Replace -> {
                entries.clear()
                if (edit.items.isNotEmpty()) entries.addAll(edit.items)
            }
            is ListEdit.Append -> {
                val drop = edit.dropPrefix.coerceAtMost(entries.size)
                if (drop > 0) entries.subList(0, drop).clear()
                if (edit.items.isNotEmpty()) entries.addAll(edit.items)
            }
        }
        if (_ui.value.filteredOut != edit.filteredOut) {
            _ui.update { it.copy(filteredOut = edit.filteredOut) }
        }
        requestSearch()
    }

    /**
     * The newest rules, compiled once they are inside the pipeline lock. A
     * rebuild must apply the rules current at the moment it runs, not the ones
     * that were current when it was scheduled.
     */
    private fun currentRules(): FilterRules {
        val filter = _ui.value.filter
        return FilterRules(filter, enabledStack, resolveUid(filter.packageName))
    }

    // ---------------- user actions ----------------

    private var filterJob: Job? = null

    fun setFilter(filter: LogFilter) {
        _ui.update { it.copy(filter = filter, regexInvalid = false) }
        // refilter reads the newest filter under the lock, so a rebuild still
        // queued for an older keystroke would only repeat the scan.
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) { refilter() }
    }

    fun setPaused(paused: Boolean) {
        // Flip the flag synchronously; the pipeline reads it under its lock,
        // so a batch drained right after the tap cannot slip past the pause.
        _ui.update { it.copy(paused = paused, pausedIncoming = 0) }
        if (paused) {
            pipeline.pause()
        } else {
            pipeline.markRunning()
            viewModelScope.launch(Dispatchers.Default) {
                pipeline.flushPending(::currentRules)
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            stagedMutex.withLock {
                engine.clearBuffer(_ui.value.remoteEndpoint)
                staged.clear()
                pipeline.clear()
            }
        }
    }

    /**
     * Per-app aggregation of the current buffer for the scope pickers —
     * computed on demand (dialog open), not per publish, since a full
     * 20k-entry scan per batch would be pure waste.
     */
    suspend fun appGroups(): List<io.github.shiaho777.logsleuth.app.core.apps.AppLogGroup> {
        val entries = pipeline.allEntries()
        val isRemote = entries.firstOrNull()?.remoteEndpoint != null
        val remotePackages = if (isRemote) {
            entries.mapNotNull { entry ->
                entry.uid?.let { uid -> entry.remotePackage?.let { uid to it } }
            }.toMap()
        } else emptyMap()
        val launchable = if (isRemote) emptySet() else installedApps.load().mapTo(HashSet()) { it.packageName }
        val pm = context.packageManager
        return withContext(Dispatchers.Default) {
            groupEntriesByApp(
                entries = entries,
                unattributedLabel = context.getString(R.string.scope_unattributed),
            ) { uid ->
                if (isRemote) {
                    val pkg = remotePackages[uid]
                    return@groupEntriesByApp pkg to (pkg ?: "UID $uid")
                }
                val pkgs = runCatching { pm.getPackagesForUid(uid)?.toList() }
                    .getOrNull().orEmpty()
                val pkg = pkgs.firstOrNull { it in launchable } ?: pkgs.firstOrNull()
                val label = pkg?.let {
                    runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
                    }.getOrNull()
                } ?: "UID $uid"
                pkg to label
            }
        }
    }

    /** Removes entries belonging to [uids] (null uid = unattributed group). */
    fun clearApps(uids: Set<Int?>) {
        viewModelScope.launch(Dispatchers.Default) {
            stagedMutex.withLock { staged.removeAll { it.uid in uids } }
            pipeline.removeUids(uids, ::currentRules)
        }
    }

    /**
     * Writes the current buffer (or only the [uids] subset; null = all) to a
     * finished session, then surfaces the share sheet as with a recording.
     */
    fun saveToSession(uids: Set<Int?>? = null, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.Default) { pipeline.entriesForSave(uids) }
            onResult(publishSnapshot(entries))
        }
    }

    fun toggleRecordingPause() {
        viewModelScope.launch {
            if (_ui.value.recording.paused) recordingManager.resume()
            else recordingManager.pause()
        }
    }

    fun saveSelection(entries: List<LogcatEntry>, onResult: (Result<Unit>) -> Unit) {
        if (entries.isEmpty()) return
        viewModelScope.launch { onResult(publishSnapshot(entries)) }
    }

    /**
     * Writes [entries] as a finished session and surfaces the share sheet for
     * it. The row is read back so the sheet shows what was actually stored; a
     * missing row means the save did not land, which is reported as a failure.
     */
    private suspend fun publishSnapshot(entries: List<LogcatEntry>): Result<Unit> {
        val id = withContext(Dispatchers.IO) { recordingManager.snapshotToSession(entries) }
            .getOrElse { return Result.failure(it) }
        val session = sessionDao.getById(id)
            ?: return Result.failure(IllegalStateException("Session $id missing after insert"))
        _ui.update {
            it.copy(
                finishedSession = session,
                finishedBackfill = 0,
                finishedSnapshot = true,
            )
        }
        return Result.success(Unit)
    }

    /**
     * Prefills an including rule from this line so the editor can drop
     * fields before saving.
     */
    fun draftFilterFromLine(entry: LogcatEntry): LogFilter =
        LogFilter.fromLine(
            tag = entry.tag,
            message = entry.message,
            pid = entry.pid,
            tid = entry.tid,
            uid = entry.uid,
            level = entry.level,
            packageName = if (entry.remoteEndpoint != null) entry.remotePackage else installedApps.packageForUid(entry.uid),
        )

    fun saveFilter(filter: LogFilter) {
        viewModelScope.launch {
            filterDao.insert(filter.copy(id = 0).toEntity())
            _ui.update { it.copy(filterNotice = it.filterNotice + 1) }
        }
    }

    fun restartLogging() = engine.restart()

    fun startRecording(): Boolean {
        if (recordingManager.state.value.isRecording) return true
        return try {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                RecordService.startIntent(context, _ui.value.filter),
            )
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun stopRecording(): Boolean {
        val sessionId = recordingManager.state.value.sessionId ?: return true
        return try {
            context.startService(RecordService.stopRequested(context, sessionId)) != null
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Toggles the floating control pill; returns false when the overlay
     * permission is still missing (caller should open the system page). */
    fun toggleBubble(): Boolean {
        val enabling = !_ui.value.bubbleEnabled
        if (enabling &&
            !android.provider.Settings.canDrawOverlays(context)
        ) {
            return false
        }
        viewModelScope.launch {
            settingsRepository.setBubbleEnabled(enabling)
            if (enabling) {
                context.startService(Intent(context, io.github.shiaho777.logsleuth.app.service.BubbleService::class.java))
            } else {
                context.stopService(Intent(context, io.github.shiaho777.logsleuth.app.service.BubbleService::class.java))
            }
        }
        return true
    }

    fun overlaySettingsIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        android.net.Uri.parse("package:${context.packageName}"),
    )

    fun savePreset(name: String) {
        viewModelScope.launch {
            filterDao.insert(_ui.value.filter.copy(name = name, id = 0).toEntity())
        }
    }

    /** Turns one saved rule on or off. The stack refreshes from the database. */
    fun setRuleEnabled(filter: LogFilter, enabled: Boolean) {
        viewModelScope.launch { filterDao.update(filter.copy(enabled = enabled).toEntity()) }
    }

    fun disableEnabledRules() {
        viewModelScope.launch {
            _ui.value.presets.filter { it.enabled }.forEach { rule ->
                filterDao.update(rule.copy(enabled = false).toEntity())
            }
        }
    }

    fun applyPreset(preset: LogFilter) = setFilter(preset.copy(id = preset.id, name = preset.name))

    fun deletePreset(preset: LogFilter) {
        viewModelScope.launch { filterDao.delete(preset.toEntity()) }
    }

    fun setSearching(searching: Boolean) {
        searchJob?.cancel()
        searchRefreshRequested = false
        _ui.update { it.copy(searching = searching, searchQuery = "", searchHits = emptyList(), searchHitIndex = -1, searchPending = false) }
    }

    fun setSearchQuery(query: String) {
        if (query == _ui.value.searchQuery) return
        _ui.update {
            it.copy(
                searchQuery = query,
                searchHits = emptyList(),
                searchHitIndex = -1,
                searchPending = query.isNotBlank(),
            )
        }
        requestSearch(reset = true)
    }

    fun setSearchCaseSensitive(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSearchCaseSensitive(value) }
    }

    private fun requestSearch(reset: Boolean = false, delayMs: Long = HITS_REFRESH_MS) {
        if (reset) {
            searchJob?.cancel()
            searchRefreshRequested = false
        }
        val state = _ui.value
        if (!state.searching || state.searchQuery.isBlank()) return
        if (!reset && searchJob?.isActive == true) {
            searchRefreshRequested = true
            return
        }
        if (reset && !state.searchPending) {
            _ui.update { it.copy(searchHits = emptyList(), searchHitIndex = -1, searchPending = true) }
        }

        searchJob = viewModelScope.launch {
            delay(delayMs)
            do {
                searchRefreshRequested = false
                val rows = entries.toList()
                val hits = withContext(Dispatchers.Default) {
                    rows.mapNotNull { row ->
                        ensureActive()
                        row.seq.takeIf { row.entry.matchesLogSearch(state.searchQuery, !state.searchCaseSensitive) }
                    }
                }
                _ui.update {
                    it.copy(
                        searchHits = hits,
                        searchHitIndex = retainedSearchHitIndex(hits, it.searchHits.getOrNull(it.searchHitIndex)),
                        searchPending = false,
                    )
                }
                if (searchRefreshRequested) delay(HITS_REFRESH_MS)
            } while (searchRefreshRequested)
        }
    }

    fun nextSearchHit(direction: Int) {
        _ui.update { state ->
            if (state.searchPending || state.searchHits.isEmpty()) state
            else state.copy(
                searchHitIndex = Math.floorMod(state.searchHitIndex + direction, state.searchHits.size),
            )
        }
    }

    private suspend fun refilter() {
        // The rules are compiled inside the pipeline lock: every filter or
        // stack change launches its own refilter, and they can reach the lock
        // out of order. Reading them there means the last one to run always
        // applies the newest rules, so the list never settles on a filter the
        // bar no longer shows.
        pipeline.refilter(::currentRules)
    }

    private fun resolveUid(packageName: String?): Int? =
        installedApps.resolveUid(packageName)

    fun shareFinishedSession(
        format: io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val session = _ui.value.finishedSession ?: return
        _ui.update { it.copy(finishedSession = null, finishedBackfill = 0) }
        viewModelScope.launch {
            onResult(exporter.exportAndShare(session.id, format))
        }
    }

    fun dismissFinishedSession() {
        _ui.update { it.copy(finishedSession = null, finishedBackfill = 0) }
    }

    override fun onCleared() {
        edits.close()
        engine.releaseClient()
        super.onCleared()
    }
}

/**
 * Index of [seq] in a seq-ordered list, or of the next surviving line when
 * [seq] was just trimmed from the front. -1 when nothing at or after [seq]
 * remains. Keeping this index on screen is what stops a parked viewport
 * from sliding as the ring buffer drops old lines.
 */
internal fun parkedIndex(entries: List<UiLogEntry>, seq: Long): Int {
    if (entries.isEmpty()) return -1
    var lo = 0
    var hi = entries.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (entries[mid].seq < seq) lo = mid + 1 else hi = mid
    }
    return if (lo < entries.size) lo else -1
}
