package io.github.shiaho777.logsleuth.app.service

import android.content.Context
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RecordingState(
    val isRecording: Boolean = false,
    val sessionId: Long? = null,
    val lineCount: Long = 0,
    /** Lines backfilled from the engine buffer at start (context before live capture). */
    val backfillCount: Long = 0,
    val startedAt: Long = 0L,
    /** Writer is open but new lines are skipped until [RecordingManager.resume]. */
    val paused: Boolean = false,
)

/**
 * Writes the live stream to a session file. Controlled by [RecordService],
 * the quick-settings tile and the floating bubble.
 *
 * UI state ([state]) is published in batches (every [STATE_BATCH] lines) —
 * at high log volume a per-line StateFlow write is pure waste; the exact
 * final count is persisted on stop via [finalLines].
 */
@Singleton
class RecordingManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val engine: LogcatEngine,
    private val sessionDao: SessionDao,
    private val filterDao: FilterDao,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Exact line count; the published [RecordingState.lineCount] may lag briefly. */
    @Volatile
    private var finalLines = 0L

    private var collectJob: Job? = null
    private var filtersJob: Job? = null

    /** Enabled saved rules, refreshed while a recording is open. */
    @Volatile
    private var activeStack = FilterStack.PASS
    private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()

    /**
     * Serializes [start], [stop] and [reconcileInterruptedSessions]. Without
     * it a stop could land while start is half done, or a manual stop and a
     * limit stop could both release the engine client.
     */
    private val lifecycleMutex = Mutex()

    /** Session file byte cap captured at start; 0 = not recording. */
    @Volatile
    private var maxBytes = 0L

    @Volatile
    private var limitMb = 0

    /** Duration cap in hours captured at start; 0 = unlimited. */
    @Volatile
    private var limitHours = 0

    /** Set once a cap fires — later emissions must not re-trigger stop/notify. */
    private val limitTriggered = AtomicBoolean(false)

    /** Timer for the duration cap; null when unlimited. */
    private var durationJob: Job? = null

    /** Bytes written so far — counted as UTF-8 line bytes, matching the file. */
    private var bytesWritten = 0L

    suspend fun start(name: String?, filter: LogFilter): Result<Long> = lifecycleMutex.withLock {
        // Checked outside runCatching: the failure cleanup below tears down the
        // writer and jobs, which must never hit a recording in progress.
        if (_state.value.isRecording) {
            return@withLock Result.failure(IllegalStateException("Already recording"))
        }
        runCatching {
            // Without a log grant the engine streams nothing; a recording
            // would silently produce a header-only file.
            check(engine.access.value.granted) { "Log access not granted" }

            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val file = File(dir, "session_${System.currentTimeMillis()}.log")
            val w = file.bufferedWriter(Charsets.UTF_8, 64 * 1024)
            writer = w

            val settings = settingsRepository.settings.first()
            limitMb = settings.recordingMaxMb
            maxBytes = limitMb.toLong() * 1024 * 1024
            limitHours = settings.recordingMaxHours
            bytesWritten = 0L
            limitTriggered.set(false)

            val header = buildString {
                appendLine("# LogSleuth session")
                appendLine("# started: ${java.time.Instant.now()}")
                appendLine("# device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("# android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("# access: ${engine.access.value.kind}")
            }
            w.write(header)
            bytesWritten += header.toByteArray(Charsets.UTF_8).size
            w.flush()

            val sessionId = sessionDao.insert(
                SessionEntity(
                    name = name ?: defaultName(),
                    filePath = file.absolutePath,
                    startedAt = System.currentTimeMillis(),
                    accessKind = engine.access.value.kind.name,
                    filterPackage = filter.packageName,
                ),
            )

            val compiled = CompiledFilter(filter, resolveUid(filter.packageName))
            val initialRules = filterDao.observeAll().first().map { it.toLogFilter() }
            activeStack = FilterStack.compile(initialRules, ::resolveUid)
            filtersJob = scope.launch {
                filterDao.observeAll().collect { rows ->
                    activeStack = FilterStack.compile(rows.map { it.toLogFilter() }, ::resolveUid)
                }
            }
            engine.activeSessionId = sessionId

            // isRecording is published synchronously so stop() and duplicate
            // start() calls see it before the collector coroutine runs.
            _state.value = RecordingState(
                isRecording = true,
                sessionId = sessionId,
                startedAt = System.currentTimeMillis(),
            )

            var count = 0L
            collectJob = scope.launch {
                var snapshotSeq = 0L
                engine.entries
                    .onSubscription {
                        // Subscribe first, then snapshot: emissions between
                        // registration and the snapshot land in both places
                        // and are deduped by seq; emissions before the
                        // subscription are recovered from the buffer.
                        val snap = engine.snapshot()
                        snapshotSeq = snap.maxSeq
                        // Include recent history as context before start.
                        val matched = snap.entries.filter { compiled.matches(it) && activeStack.matches(it) }
                        writeMutex.withLock {
                            matched.forEach {
                                w.appendLine(it.raw)
                                bytesWritten += it.raw.toByteArray(Charsets.UTF_8).size + 1
                            }
                            if (matched.isNotEmpty()) {
                                // '#'-prefixed: skipped on replay, visible in exports.
                                w.appendLine(
                                    "# ===== recording started; the ${matched.size} lines above are buffered context =====",
                                )
                            }
                            w.flush()
                        }
                        count = matched.size.toLong()
                        finalLines = count
                        _state.update { it.copy(lineCount = count, backfillCount = count) }
                        // A huge engine buffer can already exceed the cap.
                        if (bytesWritten >= maxBytes) stopForLimit()
                    }
                    .collect { entry ->
                        if (entry.seq <= snapshotSeq) return@collect
                        if (_state.value.paused) return@collect
                        if (compiled.matches(entry) && activeStack.matches(entry)) {
                            writeMutex.withLock {
                                w.appendLine(entry.raw)
                                bytesWritten += entry.raw.toByteArray(Charsets.UTF_8).size + 1
                                count++
                                finalLines = count
                                if (count % FLUSH_BATCH == 0L) w.flush()
                            }
                            if (count % STATE_BATCH == 0L) {
                                _state.update { it.copy(lineCount = count) }
                            }
                            if (bytesWritten >= maxBytes) stopForLimit()
                        }
                    }
            }

            // Duration cap: 0 disables it; the timer stops the recording and
            // notifies instead of letting an idle session run forever.
            if (limitHours > 0) {
                durationJob = scope.launch {
                    delay(limitHours.toLong() * 3_600_000L)
                    stopForTimeout()
                }
            }

            engine.acquireClient()
            sessionId
        }.onFailure {
            withContext(NonCancellable) {
                collectJob?.cancelAndJoin()
                collectJob = null
                filtersJob?.cancelAndJoin()
                filtersJob = null
                activeStack = FilterStack.PASS
                durationJob?.cancelAndJoin()
                durationJob = null
                engine.activeSessionId = null
                _state.value = RecordingState()
                writeMutex.withLock {
                    runCatching { writer?.close() }
                    writer = null
                }
            }
        }
    }

    /**
     * Writes an already-buffered set of entries as a finished session —
     * "save current view to Sessions" without ever recording live. Uses the
     * same file format as a recording (comment header + raw lines) so the
     * replay path reads it identically.
     */
    suspend fun snapshotToSession(
        entries: List<LogcatEntry>,
        name: String? = null,
    ): Result<Long> = runCatching {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "session_${System.currentTimeMillis()}.log")
        val header = buildString {
            appendLine("# LogSleuth session (snapshot)")
            appendLine("# saved: ${java.time.Instant.now()}")
            appendLine("# device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("# android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("# access: ${engine.access.value.kind}")
        }
        file.bufferedWriter(Charsets.UTF_8, 64 * 1024).use { w ->
            w.write(header)
            for (e in entries) w.appendLine(e.raw)
        }
        val now = System.currentTimeMillis()
        sessionDao.insert(
            SessionEntity(
                name = name ?: defaultName(),
                filePath = file.absolutePath,
                startedAt = entries.firstOrNull()?.timestampMillis ?: now,
                endedAt = entries.lastOrNull()?.timestampMillis ?: now,
                lineCount = entries.size.toLong(),
                accessKind = engine.access.value.kind.name,
            ),
        )
    }

    suspend fun addBookmark() {
        engine.addBookmark()
        writeMutex.withLock {
            writer?.appendLine("===== BOOKMARK ${java.time.Instant.now()} =====")
            writer?.flush()
        }
    }

    /**
     * Called from the collector when the session file reaches [maxBytes].
     * Stops the recording on a separate coroutine (stop joins the collector,
     * so calling it from inside would wait on itself) and tells the user why
     * it ended. Only the session that hit the cap is stopped: if the user
     * already stopped it and started another, the new one is left alone.
     */
    private fun stopForLimit() {
        if (!limitTriggered.compareAndSet(false, true)) return
        val mb = limitMb
        val sessionId = _state.value.sessionId ?: return
        scope.launch {
            if (stopSession(sessionId)) notificationHelper.notifyRecordingLimit(mb, sessionId)
        }
    }

    /** Called by the duration-cap timer. Same shape as [stopForLimit]. */
    private fun stopForTimeout() {
        if (!limitTriggered.compareAndSet(false, true)) return
        val hours = limitHours
        val sessionId = _state.value.sessionId ?: return
        scope.launch {
            if (stopSession(sessionId)) notificationHelper.notifyRecordingTimeout(hours, sessionId)
        }
    }

    /** Stops writing without closing the session. Lines during the pause are dropped. */
    fun pause() {
        _state.update { if (!it.isRecording || it.paused) it else it.copy(paused = true) }
    }

    fun resume() {
        _state.update { if (!it.isRecording || !it.paused) it else it.copy(paused = false) }
    }

    suspend fun stop() {
        lifecycleMutex.withLock { withContext(NonCancellable) { stopLocked(expectedSessionId = null) } }
    }

    /** Stops [sessionId] if it is still the active recording; true when it was. */
    private suspend fun stopSession(sessionId: Long): Boolean =
        lifecycleMutex.withLock { withContext(NonCancellable) { stopLocked(sessionId) } }

    /**
     * Caller holds [lifecycleMutex] and runs non-cancellable, so a stop that
     * has begun always finishes the session. The collector is joined before
     * the writer closes: a line still being written must land in the file,
     * not hit a closed stream.
     */
    private suspend fun stopLocked(expectedSessionId: Long?): Boolean {
        val s = _state.value
        if (!s.isRecording) return false
        if (expectedSessionId != null && s.sessionId != expectedSessionId) return false
        collectJob?.cancelAndJoin()
        collectJob = null
        filtersJob?.cancelAndJoin()
        filtersJob = null
        activeStack = FilterStack.PASS
        durationJob?.cancelAndJoin()
        durationJob = null
        engine.activeSessionId = null
        engine.releaseClient()
        maxBytes = 0L

        writeMutex.withLock {
            runCatching {
                writer?.flush()
                writer?.close()
            }
            writer = null
        }
        s.sessionId?.let { sessionDao.finish(it, System.currentTimeMillis(), finalLines) }
        _state.value = RecordingState()
        return true
    }

    /**
     * Marks sessions orphaned by a process death as finished — the writer
     * died with the process, so endedAt was never written. Called once at
     * app start; endedAt falls back to the file's mtime (when recording
     * actually stopped), and the line count is recomputed with the same
     * assembler logic used live. Runs under [lifecycleMutex] so a recording
     * being started at the same moment is never mistaken for an orphan; the
     * active session is skipped and every other unfinished one is reconciled.
     */
    fun reconcileInterruptedSessions() {
        scope.launch {
            lifecycleMutex.withLock {
                val activeId = _state.value.sessionId
                for (s in sessionDao.unfinished()) {
                    if (s.id == activeId) continue
                    val file = File(s.filePath)
                    val endedAt = file.lastModified()
                        .takeIf { it > 0L } ?: System.currentTimeMillis()
                    sessionDao.finish(s.id, endedAt, countEntries(file))
                }
            }
        }
    }

    private fun countEntries(file: File): Long {
        if (!file.isFile) return 0L
        val assembler = EntryAssembler()
        var n = 0L
        runCatching {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#") || line.startsWith("=====")) continue
                    n += assembler.onLine(line).size
                }
            }
            if (assembler.flush() != null) n++
        }
        return n
    }

    private fun defaultName(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        return "Session " + fmt.format(java.util.Date())
    }

    private fun resolveUid(packageName: String?): Int? {
        if (packageName == null) return null
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull()
    }

    companion object {
        private const val FLUSH_BATCH = 64L
        private const val STATE_BATCH = 64L
    }
}
