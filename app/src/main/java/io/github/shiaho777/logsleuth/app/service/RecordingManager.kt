package io.github.shiaho777.logsleuth.app.service

import android.content.Context
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.AccessKind
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.SessionLogMetadata
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
    val backfillCount: Long = 0,
    val startedAt: Long = 0L,
    val paused: Boolean = false,
    val missedEntries: Long = 0L,
    val requestId: String? = null,
)

@Singleton
class RecordingManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val engine: LogcatEngine,
    private val sessionDao: SessionDao,
    private val filterDao: FilterDao,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val installedApps: InstalledApps,
) {
    private enum class StopReason { MANUAL, SIZE, TIME, WRITE_ERROR, DEVICE_CHANGED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile
    private var capture: Capture? = null
    private var jobs = emptyList<Job>()

    private inner class Capture(
        val id: Long,
        val file: File,
        val writer: BufferedWriter,
        val endpoint: String?,
        val maxMb: Int,
        val maxHours: Int,
        val filter: CompiledFilter,
        @Volatile var stack: FilterStack,
        snapshot: LogcatEngine.Snapshot,
        headerBytes: Long,
    ) {
        val maxBytes = maxMb.toLong() * 1024 * 1024
        var bytes = headerBytes
        var lines = 0L
        var missed = 0L
        var backfill: List<LogcatEntry>? = snapshot.entries
        var pendingStop: StopReason? = null
        val progressSignal = Channel<Unit>(Channel.CONFLATED)
        private val recordedUids = HashSet<Int>()
        val sequence = RecordingSequence(
            start = snapshot.maxSeq,
            retained = { after, through -> engine.retained(endpoint, after, through) },
            write = ::writeEntry,
            gap = ::writeGap,
        )

        fun writeLine(text: String) {
            writer.appendLine(text)
            bytes += text.toByteArray(Charsets.UTF_8).size + 1
        }

        fun writeEntry(entry: LogcatEntry): Boolean {
            if (bytes >= maxBytes) return false
            if (filter.matches(entry) && stack.matches(entry)) {
                val uid = entry.uid
                val pkg = entry.remotePackage
                if (uid != null && pkg != null && recordedUids.add(uid)) {
                    writeLine(SessionLogMetadata.packageLine(uid, pkg))
                }
                writeLine(entry.raw)
                lines++
                if (lines % BATCH == 0L) writer.flush()
            }
            return true
        }

        private fun writeGap(count: Long): Boolean {
            if (bytes >= maxBytes) return false
            writeLine(SessionLogMetadata.gap(count))
            missed += count
            return true
        }

        fun publishProgress() {
            _state.update {
                if (it.sessionId != id || (it.lineCount == lines && it.missedEntries == missed)) it
                else it.copy(lineCount = lines, missedEntries = missed)
            }
        }

        fun writeBackfill() {
            val entries = backfill ?: return
            backfill = null
            for (entry in entries) if (!writeEntry(entry)) break
            if (lines > 0 && bytes < maxBytes) {
                writeLine("# ===== recording started; the $lines lines above are buffered context =====")
            }
            writer.flush()
            _state.update { it.copy(lineCount = lines, backfillCount = lines) }
        }
    }

    suspend fun start(
        name: String?,
        filter: LogFilter,
        requestId: String? = null,
        expectedEndpoint: String? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            if (capture != null) return@withLock Result.failure(IllegalStateException("Already recording"))
            runCatching {
                engine.refreshAccess()
                val access = engine.access.value
                check(access.granted) { "Log access not granted" }
                val endpoint = access.remoteEndpoint
                check(requestId == null || endpoint == expectedEndpoint) { "Recording source changed" }
                val settings = settingsRepository.settings.first()
                val rules = filterDao.observeAll().first().map { it.toLogFilter() }
                val stack = FilterStack.compile(rules, installedApps::resolveUid)
                val compiled = CompiledFilter(filter, installedApps.resolveUid(filter.packageName))
                val snapshot = engine.snapshot(endpoint)
                val file = newSessionFile()
                val (id, writer, headerBytes) = try {
                    val header = buildString {
                        appendLine("# LogSleuth session")
                        appendLine("# started: ${Instant.now()}")
                        append(deviceHeader(access.kind.name, endpoint))
                    }
                    val writer = file.bufferedWriter(Charsets.UTF_8, WRITER_BYTES)
                    try {
                        writer.write(header)
                        writer.flush()
                        val id = sessionDao.insert(
                            SessionEntity(
                                name = name ?: defaultName(),
                                filePath = file.absolutePath,
                                startedAt = System.currentTimeMillis(),
                                accessKind = access.kind.name,
                                filterPackage = filter.packageName,
                                remoteEndpoint = endpoint,
                            ),
                        )
                        Triple(id, writer, header.toByteArray(Charsets.UTF_8).size.toLong())
                    } catch (failure: Throwable) {
                        runCatching { writer.close() }
                        throw failure
                    }
                } catch (failure: Throwable) {
                    file.delete()
                    throw failure
                }
                val current = Capture(
                    id, file, writer, endpoint, settings.recordingMaxMb, settings.recordingMaxHours,
                    compiled, stack, snapshot, headerBytes,
                )
                capture = current
                engine.activeRecording = LogcatEngine.ActiveRecording(id, endpoint)
                _state.value = RecordingState(
                    isRecording = true,
                    sessionId = id,
                    startedAt = System.currentTimeMillis(),
                    requestId = requestId,
                )
                engine.acquireClient()
                jobs = buildList {
                    add(scope.launch {
                        engine.entries.onSubscription {
                            record(current) {
                                writeBackfill()
                                sequence.recoverThrough(engine.watermark(endpoint))
                            }
                        }.collect { entry ->
                            if (entry.remoteEndpoint == endpoint) {
                                record(current) { sequence.accept(entry) }
                            } else {
                                stopAfterSourceChange(current)
                            }
                        }
                    })
                    add(scope.launch {
                        for (signal in current.progressSignal) {
                            delay(PROGRESS_REFRESH_MS)
                            writeMutex.withLock {
                                current.progressSignal.tryReceive()
                                if (capture === current && current.pendingStop == null) current.publishProgress()
                            }
                        }
                    })
                    add(scope.launch {
                        engine.access.collect { active ->
                            if (active.remoteEndpoint != endpoint) stopAfterSourceChange(current)
                        }
                    })
                    add(scope.launch {
                        filterDao.observeAll().collect { rows ->
                            current.stack = FilterStack.compile(rows.map { it.toLogFilter() }, installedApps::resolveUid)
                        }
                    })
                    if (current.maxHours > 0) add(scope.launch {
                        delay(current.maxHours * 3_600_000L)
                        writeMutex.withLock { scheduleStop(current, StopReason.TIME) }
                    })
                }
                id
            }
        }
    }

    private suspend fun record(current: Capture, write: Capture.() -> Unit) {
        writeMutex.withLock {
            if (current.pendingStop != null || _state.value.paused) return
            try {
                current.write()
                if (current.bytes >= current.maxBytes) scheduleStop(current, StopReason.SIZE)
                val published = _state.value
                when {
                    current.missed != published.missedEntries || current.lines / BATCH != published.lineCount / BATCH ->
                        current.publishProgress()
                    current.lines != published.lineCount -> current.progressSignal.trySend(Unit)
                }
            } catch (_: IOException) {
                scheduleStop(current, StopReason.WRITE_ERROR)
            }
        }
    }

    private suspend fun stopAfterSourceChange(current: Capture) {
        writeMutex.withLock { scheduleStop(current, StopReason.DEVICE_CHANGED) }
    }

    private fun scheduleStop(current: Capture, reason: StopReason) {
        if (current.pendingStop != null) return
        current.pendingStop = reason
        scope.launch { finish(current.id) }
    }

    suspend fun snapshotToSession(entries: List<LogcatEntry>, name: String? = null): Result<Long> = runCatching {
        val endpoint = entries.firstOrNull()?.remoteEndpoint
        check(entries.all { it.remoteEndpoint == endpoint }) { "Snapshot contains logs from different devices" }
        val activeAccess = engine.access.value.kind
        val accessKind = when {
            endpoint != null -> AccessKind.REMOTE_ADB.name
            activeAccess == AccessKind.REMOTE_ADB -> ""
            else -> activeAccess.name
        }
        val packages = entries.mapNotNull { entry ->
            entry.uid?.let { uid -> entry.remotePackage?.let { uid to it } }
        }.toMap()
        val file = newSessionFile()
        try {
            file.bufferedWriter(Charsets.UTF_8, WRITER_BYTES).use { writer ->
                writer.appendLine("# LogSleuth session (snapshot)")
                writer.appendLine("# saved: ${Instant.now()}")
                writer.write(deviceHeader(accessKind, endpoint, packages))
                entries.forEach { writer.appendLine(it.raw) }
            }
            val now = System.currentTimeMillis()
            sessionDao.insert(
                SessionEntity(
                    name = name ?: defaultName(),
                    filePath = file.absolutePath,
                    startedAt = entries.firstOrNull()?.timestampMillis ?: now,
                    endedAt = entries.lastOrNull()?.timestampMillis ?: now,
                    lineCount = entries.size.toLong(),
                    accessKind = accessKind,
                    remoteEndpoint = endpoint,
                ),
            )
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    suspend fun addBookmark(note: String = "", expectedId: Long? = null) = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            writeMutex.withLock {
                val current = capture
                if (current?.pendingStop != null || (expectedId != null && current?.id != expectedId)) return@withLock
                engine.addBookmark(note)
                if (current != null) {
                    try {
                        current.writeLine("===== BOOKMARK ${Instant.now()} =====")
                        current.writer.flush()
                        if (current.bytes >= current.maxBytes) scheduleStop(current, StopReason.SIZE)
                    } catch (_: IOException) {
                        scheduleStop(current, StopReason.WRITE_ERROR)
                    }
                }
            }
        }
    }

    suspend fun pause(expectedId: Long? = null) = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val current = capture ?: return@withLock
            if (_state.value.paused || (expectedId != null && current.id != expectedId)) return@withLock
            record(current) {
                writeBackfill()
                sequence.recoverThrough(engine.watermark(endpoint))
                writer.flush()
                _state.update { it.copy(paused = true, lineCount = lines, missedEntries = missed) }
            }
        }
    }

    suspend fun resume(expectedId: Long? = null) = lifecycleMutex.withLock {
        val current = capture ?: return@withLock
        writeMutex.withLock {
            if (_state.value.paused && current.pendingStop == null && (expectedId == null || current.id == expectedId)) {
                current.sequence.discardThrough(engine.watermark(current.endpoint))
                _state.update { it.copy(paused = false) }
            }
        }
    }

    suspend fun stop(expectedId: Long? = null) = finish(expectedId)

    private suspend fun finish(expectedId: Long? = null) = lifecycleMutex.withLock {
        val current = capture ?: return@withLock
        if (expectedId != null && current.id != expectedId) return@withLock
        withContext(NonCancellable + Dispatchers.IO) {
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
            jobs = emptyList()
            engine.activeRecording = null
            var reason = current.pendingStop ?: StopReason.MANUAL
            try {
                writeMutex.withLock {
                    try {
                        current.writer.use {
                            if (reason != StopReason.WRITE_ERROR && reason != StopReason.SIZE) {
                                current.writeBackfill()
                                if (!_state.value.paused) {
                                    current.sequence.recoverThrough(engine.watermark(current.endpoint))
                                }
                                if (reason == StopReason.DEVICE_CHANGED) {
                                    current.writer.appendLine(SessionLogMetadata.SOURCE_CHANGED)
                                }
                            }
                        }
                    } catch (_: IOException) {
                        reason = StopReason.WRITE_ERROR
                    }
                    capture = null
                }
                val lines = if (reason == StopReason.WRITE_ERROR) countEntries(current.file) else current.lines
                sessionDao.finish(current.id, System.currentTimeMillis(), lines)
                when (reason) {
                    StopReason.MANUAL -> Unit
                    StopReason.SIZE -> notificationHelper.notifyRecordingLimit(current.maxMb, current.id)
                    StopReason.TIME -> notificationHelper.notifyRecordingTimeout(current.maxHours, current.id)
                    StopReason.WRITE_ERROR -> notificationHelper.notifyRecordingWriteError(current.id)
                    StopReason.DEVICE_CHANGED -> notificationHelper.notifyRecordingSourceChanged(current.id)
                }
            } finally {
                engine.releaseClient()
                _state.value = RecordingState()
            }
        }
    }

    fun reconcileInterruptedSessions() {
        scope.launch {
            lifecycleMutex.withLock {
                for (session in sessionDao.unfinished()) {
                    if (session.id == capture?.id) continue
                    val file = File(session.filePath)
                    val endedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                    sessionDao.finish(session.id, endedAt, countEntries(file))
                }
            }
        }
    }

    private fun countEntries(file: File): Long {
        if (!file.isFile) return 0L
        val assembler = EntryAssembler()
        var count = 0L
        runCatching {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#") || line.startsWith("=====")) continue
                    count += assembler.onLine(line).size
                }
            }
            if (assembler.flush() != null) count++
        }
        return count
    }

    private fun newSessionFile(): File {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        return File.createTempFile("session_", ".log", dir)
    }

    private fun deviceHeader(
        access: String,
        endpoint: String?,
        packages: Map<Int, String> = emptyMap(),
    ) = buildString {
        appendLine("# access: $access")
        if (endpoint == null) {
            appendLine("# device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("# android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        } else {
            append(SessionLogMetadata.sourceHeader(endpoint, packages))
        }
    }

    private fun defaultName(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        return "Session " + fmt.format(java.util.Date())
    }

    private companion object {
        const val BATCH = 64L
        const val PROGRESS_REFRESH_MS = 500L
        const val WRITER_BYTES = 64 * 1024
    }
}
