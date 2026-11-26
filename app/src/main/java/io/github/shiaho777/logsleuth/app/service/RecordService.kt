package io.github.shiaho777.logsleuth.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.filter.FilterExchange
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        const val ACTION_PAUSE = "io.github.shiaho777.logsleuth.app.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "io.github.shiaho777.logsleuth.app.action.RESUME_RECORDING"
        const val ACTION_BOOKMARK = "io.github.shiaho777.logsleuth.app.action.BOOKMARK"
        /** The whole [LogFilter], in [FilterExchange] form, so no field can be left behind. */
        const val EXTRA_FILTER = "extra_filter"

        /** An [ACTION_START] intent that records through [filter]. */
        fun startIntent(context: Context, filter: LogFilter): Intent =
            Intent(context, RecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FILTER, FilterExchange.encodeSingle(filter))

        private const val EXTRA_REQUEST_ID = "recording_request_id"
        private const val EXTRA_ENDPOINT = "recording_endpoint"
        private const val EXTRA_RESULT = "recording_result"
        const val EXTRA_SESSION_ID = "recording_session_id"
        const val STARTED = 1

        fun startRequested(
            context: Context,
            filter: LogFilter,
            requestId: String,
            endpoint: String?,
            receiver: ResultReceiver,
        ): Intent = startIntent(context, filter)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_ENDPOINT, endpoint)
            .putExtra(EXTRA_RESULT, receiver)

        fun stopRequested(context: Context, sessionId: Long): Intent =
            Intent(context, RecordService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SESSION_ID, sessionId)
    }

    @Inject lateinit var recordingManager: RecordingManager
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** True once [observeRecording] is running — a second ACTION_START while
     * already recording must not stack another state collector. */
    private var observing = false
    private var latestStartId = 0
    private var startsInFlight = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_START -> {
                // Tile and bubble send no filter and record everything; the
                // wizard sends a package; the stream screen sends its full
                // active filter so recordings match what the user sees.
                val filter = intent.getStringExtra(EXTRA_FILTER)
                    ?.let { FilterExchange.decodeSingle(it) }
                    ?: LogFilter()
                val receiver = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT, ResultReceiver::class.java)
                startsInFlight++
                scope.launch {
                    try {
                        startRecording(intent, filter, receiver)
                    } finally {
                        startsInFlight--
                        if (startsInFlight == 0) stopWhenIdle(latestStartId)
                    }
                }
            }

            ACTION_STOP -> {
                scope.launch {
                    val expectedId = intent.getLongExtra(EXTRA_SESSION_ID, -1L).takeIf { it > 0L }
                    recordingManager.stop(expectedId)
                    stopWhenIdle(startId)
                }
            }

            ACTION_PAUSE -> scope.launch { recordingManager.pause() }
            ACTION_RESUME -> scope.launch { recordingManager.resume() }
            ACTION_BOOKMARK -> scope.launch { recordingManager.addBookmark() }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun startRecording(intent: Intent, filter: LogFilter, receiver: ResultReceiver?) {
        if (!observing && !startForegroundWithNotification()) {
            notifyStartFailure(receiver)
            return
        }
        val result = recordingManager.start(
            name = null,
            filter = filter,
            requestId = intent.getStringExtra(EXTRA_REQUEST_ID),
            expectedEndpoint = intent.getStringExtra(EXTRA_ENDPOINT),
        )
        val sessionId = result.getOrNull()
        if (sessionId == null) {
            if (receiver != null || !recordingManager.state.value.isRecording) notifyStartFailure(receiver)
        } else {
            receiver?.send(STARTED, Bundle().apply { putLong(EXTRA_SESSION_ID, sessionId) })
        }
        if (recordingManager.state.value.isRecording && !observing) {
            observing = true
            observeRecording()
        }
    }

    private fun notifyStartFailure(receiver: ResultReceiver?) {
        if (receiver != null) {
            receiver.send(0, null)
        } else {
            android.widget.Toast.makeText(this, R.string.recording_start_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun stopWhenIdle(startId: Int) {
        if (startsInFlight == 0 && !recordingManager.state.value.isRecording && stopSelfResult(startId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startForegroundWithNotification(): Boolean {
        val notification = notificationHelper.recordingNotification(0)
        return startForegroundOrFalse(
            NotificationHelper.ID_RECORDING,
            notification,
            FGS_TYPE_LONG_RUNNING,
        )
    }

    /**
     * A recording can outlast any cap the system decides to apply. Stop it
     * cleanly so the session is finished and saved, instead of being killed
     * with the file half written.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) = onRecordingTimeout()

    @RequiresApi(36)
    override fun onTimeout(startId: Int, fgsType: Int) = onRecordingTimeout()

    private fun onRecordingTimeout() {
        scope.launch {
            runCatching { recordingManager.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun observeRecording() {
        scope.launch {
            var lastNotifyAt = 0L
            var lastPaused: Boolean? = null
            recordingManager.state.collectLatest { state ->
                if (!state.isRecording) {
                    stopWhenIdle(latestStartId)
                } else {
                    val pausedChanged = lastPaused != state.paused
                    val sinceLastNotification = SystemClock.elapsedRealtime() - lastNotifyAt
                    if (!pausedChanged && sinceLastNotification < 1_000L) {
                        delay(1_000L - sinceLastNotification)
                    }
                    lastNotifyAt = SystemClock.elapsedRealtime()
                    lastPaused = state.paused
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    nm.notify(
                        NotificationHelper.ID_RECORDING,
                        notificationHelper.recordingNotification(state.lineCount, state.paused),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
