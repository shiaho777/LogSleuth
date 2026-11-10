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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Mirrors Settings.searchCaseSensitive. Default matches the old search. */
    @Volatile
    private var searchIgnoreCase = true
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
                val ignore = !s.searchCaseSensitive
                val caseChanged = ignore != searchIgnoreCase
                searchIgnoreCase = ignore
                _ui.update {
                    it.copy(
                        bubbleEnabled = s.bubbleEnabled,
                        searchCaseSensitive = s.searchCaseSensitive,
                    )
                }
                if (caseChanged) {
                    val query = _ui.value.searchQuery
                    if (query.isNotBlank()) {
                        searchJob?.cancel()
                        searchJob = viewModelScope.launch(Dispatchers.Default) {
                            recomputeHits(query, resetIndex = true)
                        }
                    }
                }
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
        viewModelScope.launch {
            _ui.update { it.copy(apps = installedApps.load()) }
        }

        // Collector: stage entries; a drainer publishes them once per frame
        // so a busy logcat cannot trigger a recompose per line. On Default —
        // the snapshot path filter-matches up to the whole buffer cap and must
        // not run on the main thread (it froze cold/warm starts).
        //
        // onSubscription runs after this subscriber is registered but before
        // it receives anything: the snapshot taken inside therefore covers
        // every emission this subscription could miss, and seq dedup drops
        // entries already included in it. No gap, no duplicates.
        //
        // The drainer suspends until a line arrives, then waits one frame so
        // a burst becomes one update. Idle does not wake.
        viewModelScope.launch(Dispatchers.Default) {
            var snapshotSeq = 0L
            engine.entries
                .onSubscription {
                    val snap = engine.snapshot()
                    snapshotSeq = snap.maxSeq
                    snapshotToLists(snap.entries)
                }
                .collect { entry ->
                    if (entry.seq <= snapshotSeq) return@collect
                    stagedMutex.withLock { staged.add(entry) }
                    stagedSignal.trySend(Unit)
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

    private suspend fun snapshotToLists(snapshot: List<LogcatEntry>) {
        pipeline.snapshot(snapshot, ::currentRules)
    }

    private suspend fun drainStaged() {
        val batch = stagedMutex.withLock {
            if (staged.isEmpty()) return
            ArrayList(staged).also { staged.clear() }
        }
        // Keep hit ids fresh while the search bar is open — the stream keeps
        // appending, so a one-shot scan goes stale within seconds.
        if (pipeline.append(batch, ::currentRules)) refreshSearchHitsThrottled()
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
                if (pipeline.flushPending(::currentRules)) refreshSearchHits()
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            engine.clearBuffer()
            // `staged` holds in-flight emissions between the collector and the
            // next frame flush — without it a clear would let those lines return.
            stagedMutex.withLock { staged.clear() }
            pipeline.clear()
            _ui.update { it.copy(searchHits = emptyList(), searchHitIndex = -1) }
        }
    }

    /**
     * Per-app aggregation of the current buffer for the scope pickers —
     * computed on demand (dialog open), not per publish, since a full
     * 20k-entry scan per batch would be pure waste.
     */
    suspend fun appGroups(): List<io.github.shiaho777.logsleuth.app.core.apps.AppLogGroup> {
        val entries = pipeline.allEntries()
        val launchable = installedApps.load().mapTo(HashSet()) { it.packageName }
        val pm = context.packageManager
        return withContext(Dispatchers.Default) {
            groupEntriesByApp(
                entries = entries,
                unattributedLabel = context.getString(R.string.scope_unattributed),
            ) { uid ->
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
            refreshSearchHits()
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
            packageName = installedApps.packageForUid(entry.uid),
        )

    fun saveFilter(filter: LogFilter) {
        viewModelScope.launch {
            filterDao.insert(filter.copy(id = 0).toEntity())
            _ui.update { it.copy(filterNotice = it.filterNotice + 1) }
        }
    }

    fun restartLogging() = engine.restart()

    fun toggleRecording() {
        val recording = _ui.value.recording.isRecording
        if (recording) {
            context.startService(
                Intent(context, RecordService::class.java).setAction(RecordService.ACTION_STOP),
            )
        } else {
            // Record what the user currently sees, filters included.
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                RecordService.startIntent(context, _ui.value.filter),
            )
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

    // Search
    fun setSearching(searching: Boolean) {
        searchJob?.cancel()
        _ui.update { it.copy(searching = searching, searchQuery = "", searchHits = emptyList(), searchHitIndex = -1) }
    }

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(250)
            recomputeHits(query, resetIndex = true)
        }
    }

    fun setSearchCaseSensitive(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSearchCaseSensitive(value) }
    }

    private suspend fun recomputeHits(query: String, resetIndex: Boolean = false) {
        val hits = pipeline.hits(query, searchIgnoreCase)
        _ui.update {
            // The scan ran off the main thread; the user may have edited the
            // query or closed search meanwhile. Hits for any other query would
            // highlight the wrong rows, so only the current one may land.
            if (!it.searching || it.searchQuery != query) return@update it
            it.copy(
                searchHits = hits,
                searchHitIndex = when {
                    hits.isEmpty() -> -1
                    resetIndex -> 0
                    else -> it.searchHitIndex.coerceIn(0, hits.lastIndex)
                },
            )
        }
    }

    /** Re-indexes hits after the visible list was rebuilt. */
    private suspend fun refreshSearchHits() {
        val s = _ui.value
        if (s.searching && s.searchQuery.isNotBlank()) recomputeHits(s.searchQuery)
    }

    private var hitsRefreshJob: Job? = null

    /**
     * Re-indexes hits after an append, trailing by [HITS_REFRESH_MS]. A full
     * scan per batch would run up to 60 times a second over the whole visible
     * list while searching a busy stream, competing with the drain for the
     * pipeline lock; one debounced scan per window keeps the count accurate and
     * still covers the last batch of a burst.
     */
    private fun refreshSearchHitsThrottled() {
        val s = _ui.value
        if (!s.searching || s.searchQuery.isBlank()) return
        if (hitsRefreshJob?.isActive == true) return
        hitsRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            delay(HITS_REFRESH_MS)
            recomputeHits(_ui.value.searchQuery)
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
        // The rules are compiled inside the pipeline lock: every filter or
        // stack change launches its own refilter, and they can reach the lock
        // out of order. Reading them there means the last one to run always
        // applies the newest rules, so the list never settles on a filter the
        // bar no longer shows.
        pipeline.refilter(::currentRules)
        refreshSearchHits()
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
