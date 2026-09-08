package io.github.shiaho777.logsleuth.app.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.BuildConfig
import io.github.shiaho777.logsleuth.app.core.logcat.AccessChecker
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val readLogsGranted: Boolean = false,
) {
    val granted: Boolean
        get() = shizukuStatus == ShizukuStatus.READY || readLogsGranted
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val accessChecker: AccessChecker,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shizukuManager.status.collect { status ->
                _uiState.value = _uiState.value.copy(
                    shizukuStatus = status,
                    readLogsGranted = accessChecker.readLogsGranted(),
                )
            }
        }
        // Poll READ_LOGS: the grant arrives from adb while we may be showing
        // this screen. Bounded to the ViewModel's lifetime so it stops when
        // the setup screen is left for good.
        viewModelScope.launch {
            repeat(150) { // ~5 min at 2s interval
                refresh()
                delay(2_000)
            }
        }
    }

    fun refresh() {
        shizukuManager.refresh()
        _uiState.value = _uiState.value.copy(readLogsGranted = accessChecker.readLogsGranted())
    }

    fun requestShizukuPermission() = shizukuManager.requestPermission()

    fun copyAdbCommand() {
        val cmd = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb", cmd))
    }

    fun markSetupCompleted() {
        viewModelScope.launch { settingsRepository.setSetupCompleted(true) }
    }
}
