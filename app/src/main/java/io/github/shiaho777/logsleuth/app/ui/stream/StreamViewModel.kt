package io.github.shiaho777.logsleuth.app.ui.stream

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.apps.groupEntriesByApp
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A log line with a stable identity for LazyColumn keys. */
data class UiLogEntry(val seq: Long, val entry: LogcatEntry)

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
    /** Session just finished — prompt the user to share it. */
    val finishedSession: io.github.shiaho777.logsleuth.app.data.db.SessionEntity? = null,
    /** [finishedSession] came from a save-to-session snapshot, not a recording. */
    val finishedSnapshot: Boolean = false,
    /** How many of [finishedSession]'s lines were buffered before start. */
    val finishedBackfill: Long = 0,
    /** Floating recording controls are shown over other apps. */
    val bubbleEnabled: Boolean = false,
    /**
     * True while [entries] was just rewritten by a bulk wave (startup
     * backfill, flood batch, refilter, clear) — the list suppresses
     * per-row entrance fades for that wave or a 20k-line reload would
     * ghost every row at once.
     */
    val bulkAppend: Boolean = false,
    /** Last stream failure detail (e.g. "logcat exited 1: Permission
     *  denied"); null while healthy or when access is simply not granted. */
    val lastError: String? = null,
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

    private val listMutex = Mutex()
    private val all = ArrayList<UiLogEntry>()
    private var visible = ArrayList<UiLogEntry>()
    private var seq = 0L
    private var bufferCap = 20_000
    private val pending = ArrayList<LogcatEntry>()
    // Declared before init: SharedFlow emissions run on the emitter's
    // thread, so the collector below can touch these while init is still
    // running — a post-init declaration reads as null there (NPE crash).
    private val staged = ArrayList<LogcatEntry>(512)
    private val stagedMutex = Mutex()

    init {
        engine.refreshAccess()
        engine.acquireClient()

        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                bufferCap = s.bufferSize
                _ui.update { it.copy(bubbleEnabled = s.bubbleEnabled) }
            }
        }
        viewModelScope.launch { engine.access.collect { a -> _ui.update { it.copy(access = a) } } }
        viewModelScope.launch { engine.state.collect { s -> _ui.update { it.copy(engineState = s) } } }
        viewModelScope.launch { engine.lastError.collect { e -> _ui.update { it.copy(lastError = e) } } }
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
                    // Session row finalizes asynchronously; fetch after a beat.
                    kotlinx.coroutines.delay(400)
                    val session = sessionDao.getById(id!!)
                    _ui.update {
                        it.copy(finishedSession = session, finishedBackfill = backfill)
                    }
                }
                wasRecording = r.isRecording
            }
        }
        viewModelScope.launch {
            filterDao.observeAll().collect { list ->
                _ui.update { it.copy(presets = list.map(::toModel)) }
            }
        }
        viewModelScope.launch {
            _ui.update { it.copy(apps = installedApps.load()) }
        }

        // Collector: stage entries; a ticker publishes them every ~34ms so
        // a busy logcat cannot trigger a recompose per line. On Default —
        // the snapshot path filter-matches up to bufferCap entries and must
        // not run on the main thread (it froze cold/warm starts).
        //
        // onSubscription runs after this subscriber is registered but before
        // it receives anything: the snapshot taken inside therefore covers
        // every emission this subscription could miss, and seq dedup drops
        // entries already included in it. No gap, no duplicates.
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
                }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // ~34ms publishes (~every other frame) keep batches at
                // 1-2 rows: new lines materialize individually and the
                // chase drains a near-constant trickle — a conveyor, not
                // discrete pushes.
                delay(34)
                drainStaged()
            }
        }
    }

    private suspend fun snapshotToLists(snapshot: List<LogcatEntry>) {
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
            publishLocked(bulk = true)
            bulkPending = false
        }
    }

    private var bulkPending = false
    private var lastBulkPublishMs = 0L

    private suspend fun drainStaged() {
        val batch = stagedMutex.withLock {
            if (staged.isEmpty() && !bulkPending) return
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
            // Paused entries are bounded too: without this, pausing during a
            // log storm grows `pending` without limit.
            if (pending.size > bufferCap) {
                pending.subList(0, pending.size - bufferCap).clear()
            }
            trimLocked()
            if (!pausedNow && (addedVisible > 0 || bulkPending)) {
                // During a flood (tens of rows per tick) publish at ~5Hz
                // instead of every 34ms — the chase stays pinned either
                // way, and skipping intermediate recompositions is what
                // keeps a 20k-line backfill from strobe-lagging. The last
                // wave flushes on the next tick via bulkPending.
                val now = android.os.SystemClock.uptimeMillis()
                val bulk = bulkPending || addedVisible > 24
                if (!bulk || now - lastBulkPublishMs >= 200) {
                    publishLocked(bulk)
                    if (bulk) lastBulkPublishMs = now
                    bulkPending = false
                } else {
                    bulkPending = true
                }
            }
        }
        if (pausedNow) {
            _ui.update { it.copy(pausedIncoming = it.pausedIncoming + batch.size) }
        } else if (addedVisible > 0) {
            // Keep hit indices fresh while the search bar is open — the stream
            // keeps appending, so a one-shot scan goes stale within seconds.
            val s = _ui.value
            if (s.searching && s.searchQuery.isNotBlank()) recomputeHits(s.searchQuery)
        }
    }

    private fun trimLocked() = evictOverflow(all, visible, bufferCap)

    /** Call with [listMutex] held. [bulk] = the publish rewrote a large
     * chunk of the list — rows suppress their entrance fade for that wave. */
    private fun publishLocked(bulk: Boolean = false) {
        val snapshot = ArrayList(visible)
        _ui.update { it.copy(entries = snapshot, bulkAppend = bulk) }
    }

    private fun currentCompiled(): CompiledFilter =
        CompiledFilter(_ui.value.filter, resolveUid(_ui.value.filter.packageName))

    // ---------------- user actions ----------------

    fun setFilter(filter: LogFilter) {
        _ui.update { it.copy(filter = filter, regexInvalid = false) }
        viewModelScope.launch(Dispatchers.Default) { refilter() }
    }

    fun setPaused(paused: Boolean) {
        _ui.update { it.copy(paused = paused) }
        if (!paused) {
            viewModelScope.launch(Dispatchers.Default) {
                val compiled = currentCompiled()
                listMutex.withLock {
                    for (e in pending) {
                        val uiEntry = UiLogEntry(seq++, e)
                        all.add(uiEntry)
                        if (compiled.matches(e)) visible.add(uiEntry)
                    }
                    pending.clear()
                    trimLocked()
                    publishLocked(bulk = true)
                }
                _ui.update { it.copy(pausedIncoming = 0) }
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            engine.clearBuffer()
            // `staged` holds in-flight emissions between the collector and the
            // batch flush — without it a clear would let ~120ms of lines return.
            stagedMutex.withLock { staged.clear() }
            listMutex.withLock {
                all.clear()
                visible.clear()
                pending.clear()
                bulkPending = false
                publishLocked(bulk = true)
            }
            _ui.update { it.copy(pausedIncoming = 0, searchHits = emptyList()) }
        }
    }

    /**
     * Per-app aggregation of the current buffer for the scope pickers —
     * computed on demand (dialog open), not per publish, since a full
     * 20k-entry scan per batch would be pure waste.
     */
    suspend fun appGroups(): List<io.github.shiaho777.logsleuth.app.core.apps.AppLogGroup> {
        val entries = listMutex.withLock { all.map { it.entry } + pending }
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
            listMutex.withLock {
                all.removeAll { it.entry.uid in uids }
                visible.removeAll { it.entry.uid in uids }
                pending.removeAll { it.uid in uids }
                publishLocked(bulk = true)
            }
        }
    }

    /**
     * Writes the current buffer (or only the [uids] subset; null = all) to a
     * finished session, then surfaces the share sheet as with a recording.
     */
    fun saveToSession(uids: Set<Int?>? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val entries = listMutex.withLock {
                // Include `pending` — paused lines are still part of the stream
                // the user sees (they surface on resume) and belong in a
                // "save everything" snapshot. `staged` is a <120ms window and
                // is intentionally left out.
                (all.filter { uids == null || it.entry.uid in uids }.map { it.entry } +
                    pending.filter { uids == null || it.uid in uids })
                    .sortedBy { it.seq }
            }
            recordingManager.snapshotToSession(entries).onSuccess { id ->
                val session = sessionDao.getById(id)
                if (session != null) {
                    _ui.update {
                        it.copy(
                            finishedSession = session,
                            finishedBackfill = 0,
                            finishedSnapshot = true,
                        )
                    }
                }
            }
        }
    }

    fun toggleRecording() {
        val recording = _ui.value.recording.isRecording
        val f = _ui.value.filter
        val intent = Intent(context, RecordService::class.java).apply {
            action = if (recording) RecordService.ACTION_STOP else RecordService.ACTION_START
            if (!recording) {
                // Record what the user currently sees, filters included.
                putExtra(RecordService.EXTRA_FILTER_PACKAGE, f.packageName)
                putExtra(RecordService.EXTRA_FILTER_LEVEL, f.minLevel.name)
                putExtra(RecordService.EXTRA_FILTER_QUERY, f.query)
                putExtra(RecordService.EXTRA_FILTER_TAG, f.tagQuery)
                putExtra(RecordService.EXTRA_FILTER_EXCLUDE, f.excludeQuery)
                putExtra(RecordService.EXTRA_FILTER_REGEX, f.useRegex)
            }
        }
        if (recording) context.startService(intent)
        else androidx.core.content.ContextCompat.startForegroundService(context, intent)
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

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(250)
            recomputeHits(query, resetIndex = true)
        }
    }

    private suspend fun recomputeHits(query: String, resetIndex: Boolean = false) {
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
        _ui.update {
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
            publishLocked(bulk = true)
        }
        _ui.update { it.copy(regexInvalid = compiled.regexInvalid) }
    }

    private fun resolveUid(packageName: String?): Int? =
        installedApps.resolveUid(packageName)

    fun shareFinishedSession(format: io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format) {
        val session = _ui.value.finishedSession ?: return
        _ui.update { it.copy(finishedSession = null, finishedBackfill = 0) }
        viewModelScope.launch {
            exporter.export(session.id, format).onSuccess { file ->
                exporter.share(
                    file,
                    if (format == io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format.ZIP) {
                        "application/zip"
                    } else {
                        "text/plain"
                    },
                )
            }
        }
    }

    fun dismissFinishedSession() {
        _ui.update { it.copy(finishedSession = null, finishedBackfill = 0) }
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

/**
 * Drops the oldest `all.size - cap` entries. `visible` is a seq-ordered
 * subsequence of `all`, so evicted entries form a prefix of it — O(overflow)
 * instead of a per-entry indexOfFirst scan.
 */
internal fun evictOverflow(
    all: MutableList<UiLogEntry>,
    visible: MutableList<UiLogEntry>,
    cap: Int,
) {
    val overflow = all.size - cap
    if (overflow <= 0) return
    val lastEvictedSeq = all[overflow - 1].seq
    // subList().clear() is a single arraycopy — removeAt(0) in a loop is
    // O(n·overflow) and stalls the drain under sustained streams.
    all.subList(0, overflow).clear()
    var drop = 0
    while (drop < visible.size && visible[drop].seq <= lastEvictedSeq) drop++
    if (drop > 0) visible.subList(0, drop).clear()
}
