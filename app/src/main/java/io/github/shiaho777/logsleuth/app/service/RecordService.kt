package io.github.shiaho777.logsleuth.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a recording session alive and exposes
 * stop / bookmark actions from the notification.
 */
@AndroidEntryPoint
class RecordService : Service() {

    companion object {
        const val ACTION_START = "io.github.shiaho777.logsleuth.app.action.START_RECORDING"
        const val ACTION_STOP = "io.github.shiaho777.logsleuth.app.action.STOP_RECORDING"
        const val ACTION_BOOKMARK = "io.github.shiaho777.logsleuth.app.action.BOOKMARK"
        const val EXTRA_FILTER_PACKAGE = "extra_filter_package"
    }

    @Inject lateinit var recordingManager: RecordingManager
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pkg = intent.getStringExtra(EXTRA_FILTER_PACKAGE)
                scope.launch {
                    // The tile/bubble start without filter context: use the
                    // package only; text filters are a stream-screen concern.
                    recordingManager.start(
                        name = null,
                        filter = LogFilter(packageName = pkg),
                    )
                    startForegroundWithNotification()
                    observeRecording()
                }
            }

            ACTION_STOP -> {
                scope.launch {
                    recordingManager.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            ACTION_BOOKMARK -> {
                scope.launch { recordingManager.addBookmark() }
            }

            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = notificationHelper.recordingNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.ID_RECORDING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationHelper.ID_RECORDING, notification)
        }
    }

    private fun observeRecording() {
        scope.launch {
            var lastNotifyAt = 0L
            recordingManager.state.collectLatest { state ->
                if (!state.isRecording) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    // Posting a notification is expensive; throttle to ~1/second.
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotifyAt >= 1_000L) {
                        lastNotifyAt = now
                        val nm = getSystemService(android.app.NotificationManager::class.java)
                        nm.notify(
                            NotificationHelper.ID_RECORDING,
                            notificationHelper.recordingNotification(state.lineCount),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
