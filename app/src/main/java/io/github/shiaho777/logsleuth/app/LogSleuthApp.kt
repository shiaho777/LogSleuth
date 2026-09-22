package io.github.shiaho777.logsleuth.app

import android.app.Application
import android.content.Context
import android.content.Intent
import dagger.hilt.android.HiltAndroidApp
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.BubbleService
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class LogSleuthApp : Application() {

    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var recordingManager: RecordingManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        shizukuManager.attach()
        // A previous process may have died mid-recording.
        recordingManager.reconcileInterruptedSessions()
        restoreBubble()
    }

    /** Restarts the floating controls after a process restart when the
     *  toggle is on and the overlay permission still holds. Background
     *  start failures are swallowed — the activity retries on next open. */
    private fun restoreBubble() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.bubbleEnabled &&
                android.provider.Settings.canDrawOverlays(this@LogSleuthApp)
            ) {
                runCatching {
                    startService(Intent(this@LogSleuthApp, BubbleService::class.java))
                }
            }
        }
    }
}
