package io.github.shiaho777.logsleuth.app.service

import android.content.Context
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import java.io.BufferedWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecordingState(
    val isRecording: Boolean = false,
    val sessionId: Long? = null,
    val lineCount: Long = 0,
    /** Lines backfilled from the engine buffer at start (context before live capture). */
    val backfillCount: Long = 0,
    val startedAt: Long = 0L,
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
    private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()

    /** Session file byte cap captured at start; 0 = not recording. */
    @Volatile
    private var maxBytes = 0L

    @Volatile
    private var limitMb = 0

    /** Set once the cap fires — later emissions must not re-trigger stop/notify. */
    @Volatile
    private var limitTriggered = false

    /** Bytes written so far — counted as UTF-8 line bytes, matching the file. */
    private var bytesWritten = 0L

    suspend fun start(name: String?, filter: LogFilter): Result<Long> =
        runCatching {
            check(!_state.value.isRecording) { "Already recording" }

            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val file = File(dir, "session_${System.currentTimeMillis()}.log")
            val w = file.bufferedWriter(Charsets.UTF_8, 64 * 1024)
            writer = w

            limitMb = settingsRepository.settings.first().recordingMaxMb
            maxBytes = limitMb.toLong() * 1024 * 1024
            bytesWritten = 0L
            limitTriggered = false

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
                        val matched = snap.entries.filter(compiled::matches)
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
                        _state.value = _state.value.copy(
                            lineCount = count,
                            backfillCount = count,
                        )
                        // A huge engine buffer can already exceed the cap.
                        if (bytesWritten >= maxBytes) stopForLimit()
                    }
                    .collect { entry ->
                        if (entry.seq <= snapshotSeq) return@collect
                        if (compiled.matches(entry)) {
                            writeMutex.withLock {
                                w.appendLine(entry.raw)
                                bytesWritten += entry.raw.toByteArray(Charsets.UTF_8).size + 1
                                count++
                                finalLines = count
                                if (count % FLUSH_BATCH == 0L) w.flush()
                            }
                            if (count % STATE_BATCH == 0L) {
                                _state.value = _state.value.copy(lineCount = count)
                            }
                            if (bytesWritten >= maxBytes) stopForLimit()
                        }
                    }
            }

            engine.acquireClient()
            sessionId
        }.onFailure {
            collectJob?.cancel()
            collectJob = null
            engine.activeSessionId = null
            _state.value = RecordingState()
            runCatching { writer?.close() }
            writer = null
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
     * Stops the recording on a separate coroutine (cancelling our own job
     * mid-write would deadlock the mutex) and tells the user why it ended.
     */
    private fun stopForLimit() {
        if (limitTriggered) return
        limitTriggered = true
        val mb = limitMb
        scope.launch {
            stop()
            notificationHelper.notifyRecordingLimit(mb)
        }
    }

    suspend fun stop() {
        val s = _state.value
        if (!s.isRecording) return
        collectJob?.cancel()
        collectJob = null
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
    }

    /**
     * Marks sessions orphaned by a process death as finished — the writer
     * died with the process, so endedAt was never written. Called once at
     * app start; endedAt falls back to the file's mtime (when recording
     * actually stopped), and the line count is recomputed with the same
     * assembler logic used live. A recording that is currently active is
     * never touched.
     */
    fun reconcileInterruptedSessions() {
        scope.launch {
            if (_state.value.isRecording) return@launch
            for (s in sessionDao.unfinished()) {
                val file = File(s.filePath)
                val endedAt = file.lastModified()
                    .takeIf { it > 0L } ?: System.currentTimeMillis()
                sessionDao.finish(s.id, endedAt, countEntries(file))
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
