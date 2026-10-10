package io.github.shiaho777.logsleuth.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Holds one [LogcatEngine] client so crash detection keeps running when the
 * UI is gone. Started from boot when the user opts in, and from Settings.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var acquired = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { settingsRepository.setWatchOnBoot(false) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notificationHelper.watchNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.ID_WATCH,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationHelper.ID_WATCH, notification)
        }
        if (!acquired) {
            acquired = true
            engine.acquireClient()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (acquired) {
            acquired = false
            engine.releaseClient()
        }
        scope.cancel()
        super.onDestroy()
    }
}
