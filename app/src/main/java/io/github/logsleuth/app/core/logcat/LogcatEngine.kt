package io.github.logsleuth.app.core.logcat

import io.github.logsleuth.app.core.detect.CrashDetector
import io.github.logsleuth.app.core.detect.CrashSignal
import io.github.logsleuth.app.core.shizuku.ShizukuManager
import io.github.logsleuth.app.data.db.BookmarkDao
import io.github.logsleuth.app.data.db.BookmarkEntity
import io.github.logsleuth.app.data.db.CrashEventDao
import io.github.logsleuth.app.data.db.CrashEventEntity
import io.github.logsleuth.app.data.prefs.SettingsRepository
import io.github.logsleuth.app.service.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single owner of the logcat process. Consumers (stream UI, recording
 * service) attach/detach; the process runs while at least one consumer is
 * attached. Entries fan out via [entries]; a ring [snapshot] gives late
 * consumers recent history.
 */
@Singleton
class LogcatEngine @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val accessChecker: AccessChecker,
    private val crashEventDao: CrashEventDao,
    private val bookmarkDao: BookmarkDao,
    private val notificationHelper: NotificationHelper,
    settingsRepository: SettingsRepository,
) {
    enum class State { STOPPED, STARTING, RUNNING, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _access = MutableStateFlow(accessChecker.currentState())
    val access: StateFlow<AccessState> = _access.asStateFlow()

    private val _entries = MutableSharedFlow<LogcatEntry>(
        extraBufferCapacity = 4096,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val entries: SharedFlow<LogcatEntry> = _entries.asSharedFlow()

    private val _crashes = MutableSharedFlow<CrashSignal>(extraBufferCapacity = 16)
    val crashes: SharedFlow<CrashSignal> = _crashes.asSharedFlow()

    /** Recording session that crash events should be linked to (null when idle). */
    @Volatile
    var activeSessionId: Long? = null

    private val bufferMutex = Mutex()
    private val buffer = ArrayDeque<LogcatEntry>()

    @Volatile
    private var bufferCap = 20_000

    private var streamJob: Job? = null
    private var consumers = 0

    init {
        scope.launch {
            settingsRepository.settings.collect { bufferCap = it.bufferSize }
        }
    }

    fun refreshAccess() {
        _access.value = accessChecker.currentState()
    }

    /** A consumer wants the stream. Starts logcat on first consumer. */
    @Synchronized
    fun acquireClient() {
        consumers++
        if (streamJob == null) startStreamLocked()
    }

    @Synchronized
    fun releaseClient() {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers == 0) stopStream()
    }

    /** Recent entries for consumers attaching later (e.g. opening the screen). */
    suspend fun snapshot(): List<LogcatEntry> = bufferMutex.withLock { buffer.toList() }

    /** Inserts a timestamp bookmark; linked to the active recording if any. */
    suspend fun addBookmark(note: String = "") {
        bookmarkDao.insert(
            BookmarkEntity(
                sessionId = activeSessionId,
                time = System.currentTimeMillis(),
                note = note,
            ),
        )
    }

    private fun startStreamLocked() {
        val accessState = _access.value
        if (!accessState.granted) {
            refreshAccess()
            if (!_access.value.granted) {
                _state.value = State.ERROR
                return
            }
        }

        val source: LogcatSource = when (_access.value.kind) {
            AccessKind.SHIZUKU -> ShizukuLogcatSource(shizukuManager)
            else -> LocalLogcatSource()
        }

        _state.value = State.STARTING
        streamJob = scope.launch {
            val assembler = EntryAssembler()
            val crashDetector = CrashDetector()
            try {
                source.stream().collect { line ->
                    _state.compareAndSet(State.STARTING, State.RUNNING)
                    for (entry in assembler.onLine(line)) {
                        dispatch(entry, crashDetector)
                    }
                }
            } catch (_: Exception) {
                _state.value = State.ERROR
            } finally {
                assembler.flush()?.let { dispatch(it, crashDetector) }
                crashDetector.flush()?.let { onCrash(it) }
                if (_state.value != State.ERROR) _state.value = State.STOPPED
            }
        }
    }

    @Synchronized
    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        _state.value = State.STOPPED
    }

    private suspend fun dispatch(entry: LogcatEntry, crashDetector: CrashDetector) {
        bufferMutex.withLock {
            buffer.addLast(entry)
            while (buffer.size > bufferCap) buffer.removeFirst()
        }
        _entries.emit(entry)
        crashDetector.onEntry(entry)?.let { onCrash(it) }
    }

    private fun onCrash(signal: CrashSignal) {
        scope.launch {
            crashEventDao.insert(
                CrashEventEntity(
                    sessionId = activeSessionId,
                    time = signal.timeMillis,
                    pid = signal.pid,
                    packageName = signal.packageName,
                    type = signal.type.name,
                    firstLine = signal.firstLine,
                    snippet = signal.snippet,
                ),
            )
            notificationHelper.notifyCrash(signal)
            _crashes.emit(signal)
        }
    }

    suspend fun clearBuffer() {
        bufferMutex.withLock { buffer.clear() }
    }
}
