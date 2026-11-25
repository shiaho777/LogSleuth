package io.github.shiaho777.logsleuth.app.core.remoteadb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.logcat.RemoteAdbLogcatSource
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class RemoteAdbManager @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
) {
    enum class Status { IDLE, CONNECTING, READY, FAILED }

    data class State(
        val status: Status = Status.IDLE,
        val host: String = "",
        val port: Int = 5555,
        val detail: String? = null,
        internal val packages: Map<Int, String> = emptyMap(),
    ) {
        internal val endpoint: String
            get() = if (':' in host && !host.startsWith('[')) "[$host]:$port" else "$host:$port"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keys = AdbKeyManager(File(context.filesDir, "remote-adb"))

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val current = _state.value
                if (settings.remoteAdbHost != current.host || settings.remoteAdbPort != current.port) {
                    // Config changed: whatever was connected is stale.
                    _state.value = State(
                        status = Status.IDLE,
                        host = settings.remoteAdbHost,
                        port = settings.remoteAdbPort,
                    )
                }
            }
        }
    }

    fun source(state: State): RemoteAdbLogcatSource =
        RemoteAdbLogcatSource(state.host, state.port, keys, state.packages)

    /**
     * Probes the connection: authenticate and run a trivial shell command.
     * The remote screen may show the "Allow debugging?" dialog while this
     * runs — the caller surfaces that hint. Success marks the path ready.
     */
    fun testConnection() {
        val s = _state.value
        if (s.host.isBlank()) {
            _state.value = s.copy(status = Status.FAILED, detail = "no host configured")
            return
        }
        val connecting = s.copy(status = Status.CONNECTING, detail = null)
        if (s.status == Status.CONNECTING || !_state.compareAndSet(s, connecting)) return
        scope.launch {
            val result = runCatching {
                AdbConnection(s.host, s.port, keys, readTimeoutMs = 30_000).use { connection ->
                    connection.authenticate()
                    connection.openShell("pm list packages -U; echo logsleuth-ok")
                    val out = ByteArrayOutputStream()
                    connection.readShell { chunk ->
                        check(out.size() + chunk.size <= MAX_PROBE_BYTES) { "Remote package list is too large" }
                        out.write(chunk)
                    }
                    val text = out.toString(Charsets.UTF_8.name())
                    check(text.lineSequence().any { it.trim() == "logsleuth-ok" }) { "Remote shell did not finish the probe" }
                    RemotePackages.parse(text.lineSequence())
                }
            }
            _state.compareAndSet(
                connecting,
                connecting.copy(
                    status = if (result.isSuccess) Status.READY else Status.FAILED,
                    detail = result.exceptionOrNull()?.message,
                    packages = result.getOrNull().orEmpty(),
                ),
            )
        }
    }

    fun markFailed(endpoint: String, detail: String?) {
        val s = _state.value
        if (s.endpoint == endpoint && s.status == Status.READY) {
            _state.compareAndSet(s, s.copy(status = Status.FAILED, detail = detail))
        }
    }

    fun disconnect() {
        _state.value = _state.value.copy(status = Status.IDLE, detail = null)
    }

    /**
     * Marks the configured link ready without running the probe — used by
     * automated verification harnesses that drive the real stream next.
     */
    fun forceReadyForTest() {
        val s = _state.value
        if (s.host.isNotBlank()) {
            _state.value = s.copy(status = Status.READY, detail = null)
        }
    }

    private companion object {
        const val MAX_PROBE_BYTES = 2 * 1024 * 1024
    }
}
