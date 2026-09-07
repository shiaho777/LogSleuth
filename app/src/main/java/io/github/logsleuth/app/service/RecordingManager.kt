package io.github.logsleuth.app.service

import android.content.Context
import io.github.logsleuth.app.core.filter.CompiledFilter
import io.github.logsleuth.app.core.filter.LogFilter
import io.github.logsleuth.app.core.logcat.LogcatEngine
import io.github.logsleuth.app.data.db.SessionDao
import io.github.logsleuth.app.data.db.SessionEntity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecordingState(
    val isRecording: Boolean = false,
    val sessionId: Long? = null,
    val lineCount: Long = 0,
    val startedAt: Long = 0L,
)

/**
 * Writes the live stream to a session file. Controlled by [RecordService],
 * the quick-settings tile and the floating bubble.
 */
@Singleton
class RecordingManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val engine: LogcatEngine,
    private val sessionDao: SessionDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var collectJob: Job? = null
    private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()

    suspend fun start(name: String?, filter: LogFilter): Result<Long> =
        runCatching {
            check(!_state.value.isRecording) { "Already recording" }

            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val file = File(dir, "session_${System.currentTimeMillis()}.log")
            val w = file.bufferedWriter(Charsets.UTF_8, 64 * 1024)
            writer = w

            val header = buildString {
                appendLine("# LogSleuth session")
                appendLine("# started: ${java.time.Instant.now()}")
                appendLine("# device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("# android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("# access: ${engine.access.value.kind}")
            }
            w.write(header)
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

            // Include recent history so the session has context before start.
            val snapshot = engine.snapshot()
            writeMutex.withLock {
                snapshot.filter(compiled::matches).forEach { w.appendLine(it.raw) }
                w.flush()
            }

            var count = snapshot.count(compiled::matches).toLong()
            collectJob = scope.launch {
                engine.entries.collect { entry ->
                    if (compiled.matches(entry)) {
                        writeMutex.withLock {
                            w.appendLine(entry.raw)
                            count++
                            if (count % 64 == 0L) w.flush()
                        }
                        _state.value = _state.value.copy(lineCount = count)
                    }
                }
            }

            engine.acquireClient()
            _state.value = RecordingState(
                isRecording = true,
                sessionId = sessionId,
                lineCount = count,
                startedAt = System.currentTimeMillis(),
            )
            sessionId
        }.onFailure {
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

    suspend fun stop() {
        val s = _state.value
        if (!s.isRecording) return
        collectJob?.cancel()
        collectJob = null
        engine.activeSessionId = null
        engine.releaseClient()

        writeMutex.withLock {
            runCatching {
                writer?.flush()
                writer?.close()
            }
            writer = null
        }
        s.sessionId?.let { sessionDao.finish(it, System.currentTimeMillis(), s.lineCount) }
        _state.value = RecordingState()
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
}
