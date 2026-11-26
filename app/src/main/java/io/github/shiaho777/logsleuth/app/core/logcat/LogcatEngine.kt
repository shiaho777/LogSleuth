package io.github.shiaho777.logsleuth.app.core.logcat

import android.os.Build
import android.util.Log
import io.github.shiaho777.logsleuth.app.core.crashes.CrashCapture
import io.github.shiaho777.logsleuth.app.core.crashes.CrashRecorder
import io.github.shiaho777.logsleuth.app.core.detect.CrashDetector
import io.github.shiaho777.logsleuth.app.core.detect.CrashSignal
import io.github.shiaho777.logsleuth.app.core.root.RootManager
import io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager
import io.github.shiaho777.logsleuth.app.core.root.RootStatus
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.BookmarkEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "LogcatEngine"

/** Log lines older than this (vs stream start) are the buffer dump logcat
 * prints before following. Recording them again notifies for crashes that
 * already happened, and a reconnect replays the same lines. */
private const val CRASH_BACKLOG_GRACE_MS = 5_000L

/**
 * Single owner of the logcat process. Consumers (stream UI, recording
 * service) attach/detach; the process runs while at least one consumer is
 * attached. Entries fan out via [entries]; a ring [snapshot] gives late
 * consumers recent history.
 *
 * If the process dies (Shizuku restart, root shell, logd hiccup), the stream
 * retries with exponential backoff while consumers remain attached.
 */
@Singleton
class LogcatEngine @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val rootManager: RootManager,
    private val accessChecker: AccessChecker,
    private val crashRecorder: CrashRecorder,
    private val bookmarkDao: BookmarkDao,
    private val remoteAdbManager: RemoteAdbManager,
    settingsRepository: SettingsRepository,
) {
    enum class State { STOPPED, STARTING, RUNNING, ERROR }

    companion object {
        /**
         * [lastError] value while a READ_LOGS start is waiting for the
         * activity to be resumed. The UI maps this to its own string.
         */
        const val ADB_NEEDS_FOREGROUND = "adb_needs_foreground"
    }

    /** Last stream failure detail (e.g. "logcat exited 1: Permission
     *  denied") for the UI to surface; null when healthy/not granted. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * A buffer snapshot plus the sequence watermark at the time it was taken.
     * Consumers that subscribe *before* calling [snapshot] can deduplicate
     * live emissions with `entry.seq <= maxSeq`, closing the gap between
     * snapshot and subscription where entries would otherwise be lost.
     */
    data class Snapshot(val entries: List<LogcatEntry>, val maxSeq: Long)

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

    data class ActiveRecording(val id: Long, val remoteEndpoint: String?)

    @Volatile
    var activeRecording: ActiveRecording? = null

    private val history = LogHistory()

    @Volatile
    private var bufferCap = 20_000

    /** Cold start follows from now instead of dumping the ring. */
    @Volatile
    private var liveOnlyOnStart = false

    /** True while [MainActivity] is resumed, which is when the process is top. */
    @Volatile
    private var inForeground = false

    /**
     * A READ_LOGS start was skipped because the app was not on screen.
     * Coming to the foreground starts it; nothing retries in the background.
     */
    @Volatile
    private var adbStartDeferred = false

    private var streamJob: Job? = null
    private var reconnectJob: Job? = null
    private var consumers = 0

    /** Backoff for stream retries; reset after a successful (re)connection. */
    @Volatile
    private var backoffMs = 1_000L

    /** User's preferred grant. `auto` tries Shizuku, then root, then READ_LOGS. */
    @Volatile
    private var accessPreference: String = AccessPolicy.AUTO

    /** False until the user opts in. We never exec `su` before that. */
    @Volatile
    private var rootEnabled: Boolean = false

    /**
     * Bumped every time a stream is started or stopped. A dying stream may
     * only clear [streamJob] when it still owns the current generation, so
     * its `finally` cannot wipe out the replacement stream.
     */
    @Volatile
    private var streamGeneration = 0

    init {
        scope.launch {
            settingsRepository.settings.collect {
                bufferCap = it.bufferSize
                crashRecorder.capture = CrashCapture(
                    collectJava = it.collectJava,
                    collectNative = it.collectNative,
                    collectAnr = it.collectAnr,
                    notifyMaster = it.crashNotifications,
                    notifyJava = it.notifyJava,
                    notifyNative = it.notifyNative,
                    notifyAnr = it.notifyAnr,
                )
                crashRecorder.separateChannels = it.separateCrashChannels
                crashRecorder.blacklist = it.crashBlacklist
                liveOnlyOnStart = it.liveOnlyOnStart
                val prefChanged = accessPreference != it.accessPreference
                val rootChanged = rootEnabled != it.rootEnabled
                accessPreference = it.accessPreference
                rootEnabled = it.rootEnabled
                if (rootChanged) rootManager.refresh(it.rootEnabled)
                if (prefChanged || rootChanged) refreshAccess()
            }
        }
        // Grant changes restart the stream when the *active* path changes.
        // Root becoming ready while auto is still on Shizuku does not.
        scope.launch {
            shizukuManager.status.collect { refreshAccess() }
        }
        scope.launch {
            rootManager.status.collect { refreshAccess() }
        }
        scope.launch {
            remoteAdbManager.state.collect { refreshAccess() }
        }
        // A previous process (or a crash loop before retention existed) may
        // have left more history than the caps allow.
        scope.launch { runCatching { crashRecorder.prune() } }
    }

    private fun currentAccess(
        remote: RemoteAdbManager.State = remoteAdbManager.state.value,
    ): AccessState {
        val access = accessChecker.currentState(
            preference = accessPreference,
            rootReady = rootManager.status.value == RootStatus.READY,
            remoteReady = remote.status == RemoteAdbManager.Status.READY,
        )
        return access.copy(
            remoteEndpoint = remote.endpoint.takeIf { access.kind == AccessKind.REMOTE_ADB },
            remoteFallback = accessPreference == AccessPolicy.REMOTE && access.kind != AccessKind.REMOTE_ADB,
        )
    }

    @Synchronized
    fun refreshAccess() {
        val previous = _access.value
        val next = currentAccess()
        if (previous == next) return
        _access.value = next
        if (consumers == 0) return
        val kindChanged = previous.kind != next.kind || previous.remoteEndpoint != next.remoteEndpoint
        val becameGranted = !previous.granted && next.granted
        if (!kindChanged && !becameGranted) return
        if (next.granted) {
            resetBackoffAndRestart()
        } else {
            stopStream()
        }
    }

    /**
     * The activity calls this from onResume and onPause. Android 13+ only
     * shows the logcat approval dialog while the process is top, so a
     * READ_LOGS start waits until then. An already-running stream is left
     * alone: an approved logcat keeps its fd after the app leaves the screen.
     */
    fun setInForeground(foreground: Boolean) {
        val becameVisible = foreground && !inForeground
        inForeground = foreground
        if (!becameVisible) return
        synchronized(this) {
            if (consumers > 0 && streamJob == null && adbStartDeferred) {
                reconnectJob?.cancel()
                reconnectJob = null
                backoffMs = 1_000L
                startStreamLocked()
            }
        }
    }

    /** A consumer wants the stream. Starts logcat on first consumer. */
    @Synchronized
    fun acquireClient() {
        consumers++
        if (streamJob == null && reconnectJob == null) startStreamLocked()
    }

    @Synchronized
    fun releaseClient() {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers == 0) stopStream()
    }

    internal fun isInForeground(): Boolean = inForeground

    /** Recent entries for consumers attaching later (e.g. opening the screen). */
    fun snapshot(remoteEndpoint: String? = access.value.remoteEndpoint): Snapshot =
        history.snapshot(remoteEndpoint)

    fun watermark(remoteEndpoint: String?): Long = history.watermark(remoteEndpoint)

    fun retained(remoteEndpoint: String?, after: Long, through: Long): List<LogcatEntry> =
        history.retained(remoteEndpoint, after, through)

    fun remotePackages(endpoint: String): Map<Int, String> =
        remoteAdbManager.state.value.takeIf { it.endpoint == endpoint }?.packages.orEmpty()

    /** Inserts a timestamp bookmark; linked to the active recording if any. */
    suspend fun addBookmark(note: String = "") {
        bookmarkDao.insert(
            BookmarkEntity(
                sessionId = activeRecording?.id,
                time = System.currentTimeMillis(),
                note = note,
            ),
        )
    }

    private fun startStreamLocked() {
        // Set _access directly: refreshAccess()'s restart-on-grant would
        // re-enter startStreamLocked while this monitor is already held and
        // leave a second stream's Job orphaned.
        val remote = remoteAdbManager.state.value
        val access = currentAccess(remote)
        _access.value = access
        _lastError.value = null
        if (!_access.value.granted) {
            adbStartDeferred = false
            _state.value = State.ERROR
            streamGeneration++
            streamJob = null
            return
        }
        if (adbStartNeedsForeground(Build.VERSION.SDK_INT, _access.value.kind, inForeground)) {
            adbStartDeferred = true
            _lastError.value = ADB_NEEDS_FOREGROUND
            _state.value = State.ERROR
            streamGeneration++
            streamJob = null
            return
        }
        adbStartDeferred = false

        val source: LogcatSource = when (access.kind) {
            AccessKind.SHIZUKU -> ShizukuLogcatSource(shizukuManager)
            AccessKind.ROOT -> RootLogcatSource()
            AccessKind.READ_LOGS -> LocalLogcatSource()
            AccessKind.REMOTE_ADB -> remoteAdbManager.source(remote)
            AccessKind.NONE -> return
        }

        val generation = ++streamGeneration
        _state.value = State.STARTING
        streamJob = scope.launch {
            val assembler = EntryAssembler()
            val crashDetector = CrashDetector()
            val endpoint = access.remoteEndpoint
            val packages = (source as? RemoteAdbLogcatSource)?.packages.orEmpty()
            val since = streamSince(history.stamp(endpoint), liveOnlyOnStart, System.currentTimeMillis())
            val acceptCrashesAfter = System.currentTimeMillis() - CRASH_BACKLOG_GRACE_MS
            fun crash(signal: CrashSignal) = onCrash(
                signal.copy(remoteEndpoint = endpoint),
                acceptCrashesAfter,
            )
            fun publish(entry: LogcatEntry) {
                val stamped = dispatch(entry, generation, endpoint, packages) ?: return
                crashDetector.onEntry(stamped)?.let(::crash)
            }
            try {
                source.stream(since).collect { line ->
                    if (generation != streamGeneration) return@collect
                    if (_state.compareAndSet(State.STARTING, State.RUNNING)) {
                        backoffMs = 1_000L
                    }
                    for (entry in assembler.onLine(line)) {
                        publish(entry)
                    }
                }
                // Clean EOF (shouldn't normally happen for logcat) — treat as
                // a lost process and retry. A stream we already replaced must
                // not schedule that retry.
                synchronized(this@LogcatEngine) {
                    if (consumers > 0 && generation == streamGeneration) scheduleReconnectLocked()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "logcat stream failed", e)
                // `su` fell back to `sh`. Mark root unusable and pick another
                // path now, so reconnect does not raise the grant dialog again.
                if (e is RootDeniedException) {
                    rootManager.markDenied()
                    synchronized(this@LogcatEngine) {
                        if (generation == streamGeneration) refreshAccess()
                    }
                } else if (endpoint != null) {
                    if (generation == streamGeneration) {
                        assembler.flush()?.let(::publish)
                        crashDetector.drain().forEach(::crash)
                    }
                    synchronized(this@LogcatEngine) {
                        if (generation == streamGeneration) {
                            remoteAdbManager.markFailed(endpoint, e.message)
                            _lastError.value = e.message ?: e.javaClass.simpleName
                            refreshAccess()
                        }
                    }
                } else {
                    synchronized(this@LogcatEngine) {
                        if (consumers > 0 && generation == streamGeneration) {
                            _lastError.value = e.message ?: e.javaClass.simpleName
                            _state.value = State.ERROR
                            scheduleReconnectLocked()
                        }
                    }
                }
            } finally {
                if (generation == streamGeneration) {
                    assembler.flush()?.let(::publish)
                    // Interleaved crashes leave several blocks open; emit
                    // every one, not just the oldest.
                    crashDetector.drain().forEach(::crash)
                }
                // Check ownership under the lock: a restart between an
                // unlocked check and this write would orphan its new stream.
                synchronized(this@LogcatEngine) {
                    if (generation == streamGeneration) {
                        if (_state.value != State.ERROR) _state.value = State.STOPPED
                        streamJob = null
                    }
                }
            }
        }
    }

    /**
     * Retries the stream after [backoffMs]; single pending retry at a time.
     * Caller holds the engine monitor.
     */
    private fun scheduleReconnectLocked() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(backoffMs)
            synchronized(this@LogcatEngine) {
                // Whoever replaces reconnectJob cancels it under this lock
                // first, so a cancelled retry must not touch the new one.
                if (!isActive) return@launch
                backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
                reconnectJob = null
                if (consumers > 0 && streamJob == null) {
                    startStreamLocked()
                }
            }
        }
    }

    fun restart() = resetBackoffAndRestart()

    /**
     * Access changed while clients are attached. Cancel the live process and
     * open the new source immediately. [streamGeneration] keeps the old
     * job's `finally` from clearing the job we assign here.
     */
    private fun resetBackoffAndRestart() {
        synchronized(this@LogcatEngine) {
            backoffMs = 1_000L
            reconnectJob?.cancel()
            reconnectJob = null
            if (consumers > 0) {
                streamJob?.cancel()
                startStreamLocked()
            }
        }
    }

    @Synchronized
    private fun stopStream() {
        streamGeneration++
        streamJob?.cancel()
        streamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        adbStartDeferred = false
        _state.value = State.STOPPED
    }

    @Synchronized
    private fun dispatch(
        entry: LogcatEntry,
        generation: Int,
        remoteEndpoint: String?,
        packages: Map<Int, String>,
    ): LogcatEntry? {
        if (generation != streamGeneration) return null
        val attributed = if (remoteEndpoint == null) entry else {
            entry.copy(remoteEndpoint = remoteEndpoint, remotePackage = entry.uid?.let(packages::get))
        }
        val stamped = history.append(attributed, bufferCap)
        _entries.tryEmit(stamped)
        return stamped
    }

    private fun onCrash(signal: CrashSignal, threshold: Long) {
        val sessionId = activeRecording?.takeIf { it.remoteEndpoint == signal.remoteEndpoint }?.id
        scope.launch {
            if (signal.timeMillis < threshold) return@launch
            if (crashRecorder.ignores(signal.packageName)) return@launch
            _crashes.tryEmit(signal)
            crashRecorder.record(signal, sessionId) {
                history.snapshot(signal.remoteEndpoint).entries
            }
        }
    }

    fun clearBuffer(remoteEndpoint: String? = access.value.remoteEndpoint) {
        history.clear(remoteEndpoint)
    }
}
