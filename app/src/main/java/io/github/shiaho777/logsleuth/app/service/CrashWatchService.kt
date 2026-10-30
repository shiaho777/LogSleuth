package io.github.shiaho777.logsleuth.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject

/**
 * Holds one [LogcatEngine] client so crash detection keeps running when the
 * UI is gone. Started from boot when the user opts in, and from Settings.
 *
 * The service is long-lived by design, so it declares `specialUse`: the
 * `dataSync` type is capped at six hours per 24 on Android 15+ and a watcher
 * can never replenish that quota (#118). A refused foreground start is handled
 * as "stop and record why" — the exception used to escape [onStartCommand],
 * and the sticky restart turned it into a crash loop.
 */
@AndroidEntryPoint
class CrashWatchService : Service() {

    companion object {
        const val ACTION_STOP = "io.github.shiaho777.logsleuth.app.action.STOP_WATCH"

        fun start(context: Context) {
            val intent = Intent(context, CrashWatchService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    @Inject lateinit var engine: LogcatEngine
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private var acquired = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settingsRepository.setWatchOnBootAsync(false)
            settingsRepository.clearWatchBlockedAsync()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notificationHelper.watchNotification()
        if (!startForegroundOrFalse(NotificationHelper.ID_WATCH, notification, FGS_TYPE_LONG_RUNNING)) {
            // Not allowed to run right now: started from the background, the
            // type was revoked, or the system capped it. Staying alive would
            // only throw on the next start, so stop and let the next
            // foreground launch try again.
            settingsRepository.setWatchBlockedAtAsync(System.currentTimeMillis())
            stopSelf()
            return START_NOT_STICKY
        }
        settingsRepository.clearWatchBlockedAsync()
        if (!acquired) {
            acquired = true
            engine.acquireClient()
        }
        return START_STICKY
    }

    /**
     * Android 15+ can put a deadline on a foreground service. Nothing here has
     * one any more, but if the system asks us to stop we stop on our own terms
     * and leave the flag that keeps the next start from repeating a doomed
     * attempt.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) = onForegroundTimeout()

    @RequiresApi(36)
    override fun onTimeout(startId: Int, fgsType: Int) = onForegroundTimeout()

    private fun onForegroundTimeout() {
        settingsRepository.setWatchBlockedAtAsync(System.currentTimeMillis())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (acquired) {
            acquired = false
            engine.releaseClient()
        }
        super.onDestroy()
    }
}
