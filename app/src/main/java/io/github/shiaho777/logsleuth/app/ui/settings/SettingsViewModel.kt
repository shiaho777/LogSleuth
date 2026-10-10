package io.github.shiaho777.logsleuth.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.logcat.AccessState
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.root.RootManager
import io.github.shiaho777.logsleuth.app.core.root.RootStatus
import io.github.shiaho777.logsleuth.app.core.update.ReleaseInfo
import io.github.shiaho777.logsleuth.app.core.update.UpdateManager
import io.github.shiaho777.logsleuth.app.data.prefs.Settings
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.BubbleService
import io.github.shiaho777.logsleuth.app.service.CrashWatchService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val engine: LogcatEngine,
    private val rootManager: RootManager,
    private val updateManager: UpdateManager,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live log-access state — grant changes push through the engine. */
    val access: StateFlow<AccessState> = engine.access

    val rootStatus: StateFlow<RootStatus> = rootManager.status

    /** Called when the screen opens: an ADB grant has no event bus, so the
     *  access state only refreshes when someone asks. */
    fun refreshAccess() = engine.refreshAccess()

    fun setBufferSize(value: Int) = viewModelScope.launch {
        settingsRepository.setBufferSize(value)
    }

    fun setCrashNotifications(value: Boolean) = viewModelScope.launch {
        settingsRepository.setCrashNotifications(value)
    }

    fun setTheme(value: String) = viewModelScope.launch {
        settingsRepository.setTheme(value)
    }

    fun setLanguage(value: String) = viewModelScope.launch {
        settingsRepository.setLanguage(value)
    }

    fun setLogTextScale(value: Int) = viewModelScope.launch {
        settingsRepository.setLogTextScale(value)
    }

    fun setRecordingMaxMb(value: Int) = viewModelScope.launch {
        settingsRepository.setRecordingMaxMb(value)
    }

    fun setRecordingMaxHours(value: Int) = viewModelScope.launch {
        settingsRepository.setRecordingMaxHours(value)
    }

    fun setWatchOnBoot(value: Boolean) = viewModelScope.launch {
        settingsRepository.setWatchOnBoot(value)
        if (value) {
            runCatching { CrashWatchService.start(context) }
        } else {
            context.stopService(Intent(context, CrashWatchService::class.java))
        }
    }

    fun unignorePackage(packageName: String) = viewModelScope.launch {
        settingsRepository.unignorePackage(packageName)
    }

    fun setShowLogTime(value: Boolean) = viewModelScope.launch {
        settingsRepository.setShowLogTime(value)
    }

    fun setShowLogPid(value: Boolean) = viewModelScope.launch {
        settingsRepository.setShowLogPid(value)
    }

    fun setShowLogTid(value: Boolean) = viewModelScope.launch {
        settingsRepository.setShowLogTid(value)
    }

    fun setShowLogTag(value: Boolean) = viewModelScope.launch {
        settingsRepository.setShowLogTag(value)
    }

    fun setShowLogPackage(value: Boolean) = viewModelScope.launch {
        settingsRepository.setShowLogPackage(value)
    }

    fun setLogTimeFormat(value: String) = viewModelScope.launch {
        settingsRepository.setLogTimeFormat(value)
    }

    fun setRootEnabled(value: Boolean) = viewModelScope.launch {
        settingsRepository.setRootEnabled(value)
    }

    fun setAccessPreference(value: String) = viewModelScope.launch {
        settingsRepository.setAccessPreference(value)
    }

    /** Toggles the floating bubble; returns false when the overlay permission
     * is still missing (caller should open the system settings page). */
    fun setBubbleEnabled(value: Boolean): Boolean {
        if (value && !AndroidSettings.canDrawOverlays(context)) return false
        viewModelScope.launch {
            settingsRepository.setBubbleEnabled(value)
            if (value) {
                context.startService(Intent(context, BubbleService::class.java))
            } else {
                context.stopService(Intent(context, BubbleService::class.java))
            }
        }
        return true
    }

    fun overlaySettingsIntent(): Intent = Intent(
        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun canDrawOverlays(): Boolean = AndroidSettings.canDrawOverlays(context)

    // ---------------- GitHub / update card ----------------

    val update = updateManager.state
    fun checkUpdates() = updateManager.checkNow()
    fun downloadRelease(r: ReleaseInfo) = updateManager.start(r)
    fun pauseDownload() = updateManager.pause()
    fun resumeDownload() = updateManager.resume()
    fun cancelDownload() = updateManager.cancelDownload()
    fun openRepoIntent(): Intent = updateManager.repoIntent()
    fun openReleaseIntent(r: ReleaseInfo): Intent = updateManager.openReleaseIntent(r)

    /** Install, or route the user to the "install unknown apps" grant first. */
    fun installDownloaded() {
        val file = updateManager.state.value.dlFile ?: return
        if (updateManager.canInstallUnknownApps()) {
            context.startActivity(updateManager.installIntent(file))
        } else {
            context.startActivity(updateManager.unknownSourcesIntent())
        }
    }
}
