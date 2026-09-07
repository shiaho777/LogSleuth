package io.github.logsleuth.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.logsleuth.app.data.prefs.Settings
import io.github.logsleuth.app.data.prefs.SettingsRepository
import io.github.logsleuth.app.service.BubbleService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBufferSize(value: Int) = viewModelScope.launch {
        settingsRepository.setBufferSize(value)
    }

    fun setCrashNotifications(value: Boolean) = viewModelScope.launch {
        settingsRepository.setCrashNotifications(value)
    }

    fun setTheme(value: String) = viewModelScope.launch {
        settingsRepository.setTheme(value)
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
}
