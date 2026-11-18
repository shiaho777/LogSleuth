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
import javax.inject.Inject

/**
 * Holds one [LogcatEngine] client so automation rules keep matching when the
 * UI is gone, and runs [AutomationManager] on that stream. Same contract as
 * [CrashWatchService]: `specialUse` (never the capped `dataSync`),
 * `startForegroundOrFalse` so a refused start stops the service instead of
 * crash-looping the process.
 *
 * Started from Settings when the user turns automation on, and restored
 * after process death / boot while the switch stays on.
 */
@AndroidEntryPoint
class AutomationService : Service() {

    companion object {
        const val ACTION_STOP = "io.github.shiaho777.logsleuth.app.action.STOP_AUTOMATION"

        fun start(context: Context) {
            val intent = Intent(context, AutomationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    @Inject lateinit var engine: LogcatEngine
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var automationManager: AutomationManager

    private var acquired = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            automationManager.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notificationHelper.automationNotification()
        if (!startForegroundOrFalse(
                NotificationHelper.ID_AUTOMATION,
                notification,
                FGS_TYPE_LONG_RUNNING,
            )
        ) {
            // Refused (background start, revoked type, system cap): stop
            // cleanly; the next foreground launch or boot tries again.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!acquired) {
            acquired = true
            engine.acquireClient()
        }
        automationManager.start()
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) = onForegroundTimeout()

    @RequiresApi(36)
    override fun onTimeout(startId: Int, fgsType: Int) = onForegroundTimeout()

    private fun onForegroundTimeout() {
        automationManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        automationManager.stop()
        if (acquired) {
            acquired = false
            engine.releaseClient()
        }
        super.onDestroy()
    }
}