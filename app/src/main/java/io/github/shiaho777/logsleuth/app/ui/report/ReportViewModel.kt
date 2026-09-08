package io.github.shiaho777.logsleuth.app.ui.report

import android.content.Context
import android.content.Intent
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
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val step: Int = 1,
    val apps: List<AppChoice> = emptyList(),
    val selectedApp: AppChoice? = null,
    val isRecording: Boolean = false,
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

    /** Session id captured at the moment recording starts/stops. */
    private var trackedSessionId: Long? = null
    private var autoStopHandled = false

    init {
        viewModelScope.launch {
            _ui.update { it.copy(apps = installedApps.load()) }
        }
        // Mirror the global recording state.
        viewModelScope.launch {
            recordingManager.state.collect { s ->
                _ui.update {
                    it.copy(isRecording = s.isRecording, recordedLines = s.lineCount)
                }
                if (s.isRecording) trackedSessionId = s.sessionId
            }
        }
        // Auto-stop when the selected app crashes during a report recording.
        viewModelScope.launch {
            engine.crashes.collect { signal ->
                val selected = _ui.value.selectedApp ?: return@collect
                if (!_ui.value.isRecording || autoStopHandled) return@collect
                if (signal.packageName == selected.packageName) {
                    autoStopHandled = true
                    _ui.update { it.copy(crashCaught = selected.packageName) }
                    stopRecording()
                }
            }
        }
    }

    fun selectApp(app: AppChoice) {
        _ui.update { it.copy(selectedApp = app) }
    }

    fun nextStep() {
        _ui.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }
    }

    fun prevStep() {
        _ui.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    fun startRecording() {
        val app = _ui.value.selectedApp
        autoStopHandled = false
        engine.refreshAccess()
        val intent = Intent(context, RecordService::class.java).apply {
            action = RecordService.ACTION_START
            putExtra(RecordService.EXTRA_FILTER_PACKAGE, app?.packageName)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun stopRecording() {
        context.startService(
            Intent(context, RecordService::class.java).setAction(RecordService.ACTION_STOP),
        )
        viewModelScope.launch {
            // Wait for the service to finalize the session row.
            val id = trackedSessionId
            var session: SessionEntity? = null
            repeat(20) {
                delay(150)
                session = id?.let { sessionDao.getById(it) }
                if (session?.endedAt != null) return@repeat
            }
            _ui.update {
                it.copy(
                    step = 3,
                    finishedSession = session ?: it.finishedSession,
                )
            }
        }
    }

    fun share(format: SessionExporter.Format) {
        val session = _ui.value.finishedSession ?: return
        viewModelScope.launch {
            exporter.export(session.id, format).onSuccess { file ->
                exporter.share(
                    file,
                    if (format == SessionExporter.Format.ZIP) "application/zip" else "text/plain",
                )
            }
        }
    }
}
