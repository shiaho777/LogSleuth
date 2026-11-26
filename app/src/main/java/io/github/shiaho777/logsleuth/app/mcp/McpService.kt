package io.github.shiaho777.logsleuth.app.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.MainActivity
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.service.FGS_TYPE_LONG_RUNNING
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import io.github.shiaho777.logsleuth.app.service.startForegroundOrFalse
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
internal class McpService : Service() {
    @Inject lateinit var listener: McpListener
    @Inject lateinit var capture: McpCapture
    @Inject lateinit var files: McpFiles
    @Inject lateinit var identity: McpIdentity
    @Inject lateinit var status: McpStatus
    @Inject lateinit var engine: LogcatEngine
    @Inject lateinit var recording: RecordingManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var run: Job? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                listener.close()
                run?.cancel()
                if (run == null) stopSelf()
            }
            ACTION_REVOKE -> scope.launch {
                identity.revoke()
                listener.disconnect()
                files.clear()
                status.paired(false)
            }
            ACTION_START -> if (run == null) {
                status.set(McpState(starting = true, paired = identity.isPaired()))
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, getString(R.string.mcp_title), NotificationManager.IMPORTANCE_LOW)
                )
                if (!startForegroundOrFalse(NOTIFICATION, notification(0L, false), FGS_TYPE_LONG_RUNNING)) {
                    status.set(McpState(failed = true, paired = identity.isPaired()))
                    stopSelf()
                    return START_NOT_STICKY
                }
                run = scope.launch { runServer(manager) }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runServer(manager: NotificationManager) {
        var acquired = false
        var failed = false
        try {
            val socket = listener.start()
            files.clear()
            capture.enable()
            engine.acquireClient()
            acquired = true
            status.set(McpState(active = true, paired = identity.isPaired()))
            coroutineScope {
                launch {
                    engine.crashes.collect { capture.onCrash(it.packageName, it.remoteEndpoint, it.firstLine) }
                }
                launch {
                    recording.state.map { it.isRecording }.distinctUntilChanged().collect { active ->
                        manager.notify(NOTIFICATION, notification(recording.state.value.lineCount, active))
                    }
                }
                try {
                    listener.serve(this, socket)
                } finally {
                    coroutineContext.cancel()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable) {
                listener.close()
                identity.cancelPairing()
                try {
                    capture.disable()
                } finally {
                    if (acquired) engine.releaseClient()
                    try {
                        files.clear()
                    } finally {
                        withContext(Dispatchers.Main) {
                            status.set(McpState(failed = failed, paired = identity.isPaired()))
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun notification(lines: Long, recording: Boolean): Notification {
        val open = PendingIntent.getActivity(this, NOTIFICATION, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, NOTIFICATION, Intent(this, McpService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.mcp_title))
            .setContentText(if (recording) getString(R.string.mcp_notification_recording) else getString(R.string.mcp_running))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.mcp_stop), stop)
            .build()
    }

    override fun onDestroy() {
        listener.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "mcp.start"
        const val ACTION_STOP = "mcp.stop"
        const val ACTION_REVOKE = "mcp.revoke"
        private const val CHANNEL = "local_mcp"
        private const val NOTIFICATION = 1008
    }
}
