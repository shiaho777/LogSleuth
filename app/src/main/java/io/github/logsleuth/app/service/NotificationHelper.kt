package io.github.logsleuth.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.logsleuth.app.MainActivity
import io.github.logsleuth.app.R
import io.github.logsleuth.app.core.detect.CrashSignal
import io.github.logsleuth.app.core.detect.CrashType

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_CRASHES = "crashes"
        const val ID_RECORDING = 1001
        const val ID_CRASH_BASE = 2000
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                context.getString(R.string.notif_channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRASHES,
                context.getString(R.string.notif_channel_crashes),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun recordingNotification(lineCount: Long): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RecordService::class.java).setAction(RecordService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bookmarkIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, RecordService::class.java).setAction(RecordService.ACTION_BOOKMARK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_recording_title))
            .setContentText(context.getString(R.string.notif_recording_text, lineCount))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.notif_action_bookmark), bookmarkIntent)
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    fun notifyCrash(signal: CrashSignal) {
        val label = signal.packageName ?: context.getString(R.string.unknown_app)
        val title = when (signal.type) {
            CrashType.CRASH -> context.getString(R.string.notif_crash_title, label)
            CrashType.ANR -> context.getString(R.string.notif_anr_title, label)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            4,
            MainActivity.crashesIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CRASHES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(signal.firstLine)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_CRASH_BASE + (signal.timeMillis % 100_000).toInt(), notification)
    }
}
