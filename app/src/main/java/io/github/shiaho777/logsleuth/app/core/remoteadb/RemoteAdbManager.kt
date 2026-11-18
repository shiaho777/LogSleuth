package io.github.shiaho777.logsleuth.app.core.remoteadb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.logcat.RemoteAdbLogcatSource
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
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

/**
 * Owns the remote-device configuration (host:port from Settings) and the
 * key identity, and reports whether a remote stream is usable. The engine
 * reads [ready] when choosing an access path; a successful probe flips it
 * on, a stream failure flips it off so the policy can fall back.
 */
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
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keys = AdbKeyManager(File(context.filesDir, "remote-adb"))

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val ready: Boolean get() = _state.value.status == Status.READY

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

    fun keyManager(): AdbKeyManager = keys

    fun source(): RemoteAdbLogcatSource? {
        val s = _state.value
        if (s.host.isBlank()) return null
        return RemoteAdbLogcatSource(s.host, s.port, keys)
    }

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
        _state.value = s.copy(status = Status.CONNECTING, detail = null)
        scope.launch {
            val result = runCatching {
                AdbConnection(s.host, s.port, keys).use { connection ->
                    connection.authenticate()
                    connection.openShell("echo logsleuth-ok")
                    val out = StringBuilder()
                    connection.readShell { out.append(String(it, Charsets.UTF_8)) }
                    check(out.toString().contains("logsleuth-ok")) { "shell probe returned: $out" }
                }
            }
            _state.value = _state.value.copy(
                status = if (result.isSuccess) Status.READY else Status.FAILED,
                detail = result.exceptionOrNull()?.message,
            )
        }
    }

    /** Called by the engine when the remote stream dies. */
    fun markFailed(detail: String?) {
        _state.value = _state.value.copy(status = Status.FAILED, detail = detail)
    }

    fun markReady() {
        _state.value = _state.value.copy(status = Status.READY, detail = null)
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
}