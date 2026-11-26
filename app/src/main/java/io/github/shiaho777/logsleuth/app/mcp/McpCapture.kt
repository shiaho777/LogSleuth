package io.github.shiaho777.logsleuth.app.mcp

import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class McpCapture @Inject constructor(
    private val engine: LogcatEngine,
    private val recording: RecordingManager,
) {
    private data class Request(val name: String?, val filter: LogFilter, val report: String?)
    private data class Started(val request: Request, val id: Long)
    data class Report(val target: String, val endpoint: String?, val crash: String? = null)
    private val lock = Mutex()
    private val requests = linkedMapOf<String, Started>()
    private val reports = linkedMapOf<Long, Report>()
    private var enabled = false
    private var owned: Long? = null

    suspend fun enable() = lock.withLock { enabled = true }

    suspend fun disable() = lock.withLock {
        enabled = false
        owned?.let { recording.stop(it) }
        owned = null
        requests.clear()
    }

    suspend fun start(name: String?, filter: LogFilter, key: String?, report: String?): Long = lock.withLock {
        if (!enabled) refuse("MCP_DISABLED", "Enable MCP in LogSleuth.")
        val request = Request(name, filter, report)
        key?.let { requests[it] }?.let { previous ->
            if (previous.request != request) refuse("REQUEST_CONFLICT", "The request ID was used with different arguments.")
            return@withLock previous.id
        }
        if (recording.state.value.isRecording) refuse("RECORDING_ACTIVE", "Stop the active recording before starting another.")
        engine.refreshAccess()
        val access = engine.access.value
        if (!access.granted) refuse("LOG_ACCESS_REQUIRED", "Grant device log access in LogSleuth.")
        if (engine.state.value != LogcatEngine.State.RUNNING) {
            refuse("SOURCE_NOT_READY", "Open LogSleuth and wait for the source to start. Approve Android log access if prompted.")
        }
        val id = recording.start(name, filter, UUID.randomUUID().toString(), access.remoteEndpoint).getOrElse {
            if (recording.state.value.isRecording) refuse("RECORDING_ACTIVE", "A different recording is active.")
            refuse("RECORDING_FAILED", "The recording could not start. Check the active source and storage.")
        }
        owned = id
        if (key != null) {
            if (requests.size == 64) requests.remove(requests.keys.first())
            requests[key] = Started(request, id)
        }
        if (report != null) {
            if (reports.size == 64) reports.remove(reports.keys.first())
            reports[id] = Report(report, access.remoteEndpoint)
        }
        id
    }

    suspend fun report(id: Long): Report? = lock.withLock { reports[id] }

    suspend fun onCrash(packageName: String?, endpoint: String?, firstLine: String) = lock.withLock {
        val id = owned ?: return@withLock
        val report = reports[id] ?: return@withLock
        if (report.target == packageName && report.endpoint == endpoint && recording.state.value.sessionId == id) {
            reports[id] = report.copy(crash = firstLine)
            recording.stop(id)
        }
    }
}
