package io.github.shiaho777.logsleuth.app.ui.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.service.RecordService
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import io.github.shiaho777.logsleuth.app.service.RecordingState
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val step: Int = 1,
    val apps: List<AppChoice> = emptyList(),
    val selectedApp: AppChoice? = null,
    val hasAccess: Boolean = false,
    val appsLoading: Boolean = true,
    val sourceEndpoint: String? = null,
    val otherRecording: Boolean = false,
    val isStarting: Boolean = false,
    val isRecording: Boolean = false,
    val isFinishing: Boolean = false,
    val failed: Boolean = false,
    val exporting: Boolean = false,
    val recordedLines: Long = 0,
    val crashCaught: String? = null,
    val finishedSession: SessionEntity? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedApps: InstalledApps,
    private val recordingManager: RecordingManager,
    private val engine: LogcatEngine,
    private val sessionDao: SessionDao,
    private val exporter: SessionExporter,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReportUiState())
    val ui: StateFlow<ReportUiState> = _ui.asStateFlow()

    private var requestId: String? = null
    private var stopRequested = false
    private var selectedEndpoint: String? = null

    init {
        viewModelScope.launch {
            engine.access.collect { access -> _ui.update { it.copy(hasAccess = access.granted) } }
        }
        viewModelScope.launch {
            engine.access.map { it.remoteEndpoint }.distinctUntilChanged().collectLatest { endpoint ->
                _ui.update { state ->
                    if (requestId != null || state.finishedSession != null) state.copy(appsLoading = true)
                    else state.copy(apps = emptyList(), selectedApp = null, step = 1, appsLoading = true)
                }
                val apps = installedApps.loadForCapture(endpoint?.let(engine::remotePackages))
                _ui.update { it.copy(apps = apps, appsLoading = false, sourceEndpoint = endpoint) }
            }
        }
        viewModelScope.launch {
            recordingManager.state.collect(::publishRecording)
        }
        viewModelScope.launch {
            engine.crashes.collect { signal ->
                val selected = _ui.value.selectedApp ?: return@collect
                val recording = recordingManager.state.value
                val id = ownedSessionId(recording) ?: return@collect
                val active = engine.activeRecording ?: return@collect
                if (id == active.id && signal.remoteEndpoint == active.remoteEndpoint &&
                    signal.packageName == selected.packageName && !stopRequested
                ) {
                    _ui.update { it.copy(crashCaught = selected.label) }
                    stopRecording()
                }
            }
        }
    }

    fun selectApp(app: AppChoice) {
        val state = _ui.value
        if (requestId != null || state.step != 1 || state.appsLoading || app !in state.apps) return
        selectedEndpoint = state.sourceEndpoint
        _ui.update { it.copy(selectedApp = app, failed = false) }
    }

    fun nextStep() {
        _ui.update { if (it.step == 1 && it.selectedApp != null) it.copy(step = 2) else it }
    }

    fun prevStep() {
        if (requestId == null) _ui.update { if (it.step == 2) it.copy(step = 1, failed = false) else it }
    }

    fun startRecording() {
        val state = _ui.value
        val app = state.selectedApp ?: return
        if (state.step != 2 || requestId != null || recordingManager.state.value.isRecording) return
        engine.refreshAccess()
        val access = engine.access.value
        if (!access.granted || state.appsLoading) return
        if (access.remoteEndpoint != selectedEndpoint) {
            finishReport()
            return
        }
        val token = UUID.randomUUID().toString()
        requestId = token
        stopRequested = false
        _ui.update { it.copy(isStarting = true, failed = false, crashCaught = null) }
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (requestId != token) return
                val id = resultData?.getLong(RecordService.EXTRA_SESSION_ID) ?: 0L
                if (resultCode == RecordService.STARTED && id > 0L) {
                    awaitSession(id)
                } else {
                    failRecording()
                }
            }
        }
        try {
            ContextCompat.startForegroundService(
                context,
                RecordService.startRequested(context, LogFilter(packageName = app.packageName), token, access.remoteEndpoint, receiver),
            )
        } catch (_: IllegalStateException) {
            failRecording()
        } catch (_: SecurityException) {
            failRecording()
        }
    }

    fun openTarget(): Boolean {
        val packageName = _ui.value.selectedApp?.packageName ?: return false
        if (selectedEndpoint != null || ownedSessionId(recordingManager.state.value) == null || stopRequested) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun stopRecording() {
        val sessionId = ownedSessionId(recordingManager.state.value) ?: return
        if (stopRequested) return
        stopRequested = true
        publishRecording(recordingManager.state.value)
        try {
            context.startService(RecordService.stopRequested(context, sessionId))
        } catch (_: IllegalStateException) {
            undoStopRequest()
        } catch (_: SecurityException) {
            undoStopRequest()
        }
    }

    private fun undoStopRequest() {
        stopRequested = false
        _ui.update { it.copy(failed = true) }
        publishRecording(recordingManager.state.value)
    }

    private fun awaitSession(id: Long) {
        _ui.update { it.copy(isStarting = false) }
        publishRecording(recordingManager.state.value)
        viewModelScope.launch {
            recordingManager.state.first { it.sessionId != id }
            val session = sessionDao.getById(id)
            if (session?.endedAt == null) {
                failRecording()
            } else {
                requestId = null
                stopRequested = false
                _ui.update { it.copy(step = 3, isRecording = false, isFinishing = false, finishedSession = session) }
                publishRecording(recordingManager.state.value)
            }
        }
    }

    private fun failRecording() {
        requestId = null
        stopRequested = false
        if (selectedEndpoint != engine.access.value.remoteEndpoint) finishReport()
        _ui.update { it.copy(isStarting = false, isRecording = false, isFinishing = false, failed = true) }
        publishRecording(recordingManager.state.value)
    }

    private fun ownedSessionId(recording: RecordingState): Long? =
        recording.sessionId?.takeIf { requestId != null && recording.requestId == requestId }

    private fun publishRecording(recording: RecordingState) {
        val owns = ownedSessionId(recording) != null
        _ui.update {
            val starting = it.isStarting && !owns
            it.copy(
                otherRecording = recording.isRecording && !owns,
                isStarting = starting,
                isRecording = owns,
                isFinishing = requestId != null && !starting && (!owns || stopRequested),
                recordedLines = if (owns) recording.lineCount else 0,
            )
        }
    }

    fun finishReport() {
        _ui.update {
            it.copy(
                step = 1,
                selectedApp = null,
                crashCaught = null,
                finishedSession = null,
                failed = false,
            )
        }
    }

    fun share(format: SessionExporter.Format, onResult: (Result<Unit>) -> Unit) {
        val session = _ui.value.finishedSession ?: return
        if (_ui.value.exporting) return
        _ui.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                onResult(exporter.exportAndShare(session.id, format))
            } finally {
                _ui.update { it.copy(exporting = false) }
            }
        }
    }
}
