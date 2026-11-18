package io.github.shiaho777.logsleuth.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.github.shiaho777.logsleuth.app.MainActivity
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.detect.CrashSignal
import io.github.shiaho777.logsleuth.app.core.detect.CrashType

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_CRASHES = "crashes"
        const val CHANNEL_WATCH = "crash_watch"
        const val CHANNEL_AUTOMATION = "automation"
        const val ID_RECORDING = 1001
        const val ID_RECORDING_LIMIT = 1002
        const val ID_RECORDING_TIMEOUT = 1003
        const val ID_WATCH = 1004
        const val ID_RECORDING_WRITE_ERROR = 1005
        const val ID_AUTOMATION = 1006
        const val ID_CRASH_BASE = 2000
        private const val NOTIFICATION_BODY = 1_500
        private const val COPY_BODY = 6_000
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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                context.getString(R.string.notif_channel_watch),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTOMATION,
                context.getString(R.string.notif_channel_automation),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun recordingNotification(lineCount: Long, paused: Boolean = false): Notification {
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
        val pauseIntent = PendingIntent.getService(
            context,
            7,
            Intent(context, RecordService::class.java).setAction(
                if (paused) RecordService.ACTION_RESUME else RecordService.ACTION_PAUSE,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (paused) {
            context.getString(R.string.notif_recording_paused)
        } else {
            context.getString(R.string.notif_recording_title)
        }
        val pauseLabel = if (paused) {
            context.getString(R.string.notif_action_resume)
        } else {
            context.getString(R.string.notif_action_pause)
        }
        return NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_recording_text, lineCount))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, context.getString(R.string.notif_action_bookmark), bookmarkIntent)
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    /** Low-priority notice while the boot watch holds the logcat engine open. */
    fun watchNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            8,
            Intent(context, CrashWatchService::class.java).setAction(CrashWatchService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            9,
            MainActivity.crashesIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_watch_title))
            .setContentText(context.getString(R.string.notif_watch_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    /** Low-priority notice while automation broadcasts are being dispatched. */
    fun automationNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            11,
            Intent(context, AutomationService::class.java)
                .setAction(AutomationService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            12,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_AUTOMATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_automation_title))
            .setContentText(context.getString(R.string.notif_automation_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    /**
     * [eventId] is the Room row id when the crash was stored. Zero means
     * the user asked to be told without keeping a row, so the tap opens
     * the crash list.
     *
     * With [separateChannels], each app and crash type gets its own channel
     * so system settings can mute one package and leave the record in place.
     */
    fun notifyCrash(
        signal: CrashSignal,
        eventId: Long,
        appLabel: String?,
        separateChannels: Boolean,
        mergedCount: Int = 1,
    ) {
        val label = appLabel?.takeIf { it.isNotBlank() }
            ?: signal.packageName
            ?: context.getString(R.string.unknown_app)
        val baseTitle = when (signal.type) {
            CrashType.CRASH -> context.getString(R.string.notif_crash_title, label)
            CrashType.NATIVE -> context.getString(R.string.notif_native_title, label)
            CrashType.ANR -> context.getString(R.string.notif_anr_title, label)
        }
        // A crash loop is merged into one updating notification; the count
        // says how many crashes happened since the previous announcement.
        val title = if (mergedCount > 1) "$baseTitle ×$mergedCount" else baseTitle
        val channel = channelId(signal.type.name, signal.packageName, separateChannels)
        ensureChannel(channel, signal.type, label, separateChannels)
        val open = if (eventId > 0) {
            MainActivity.crashIntent(context, eventId)
        } else {
            MainActivity.crashesIntent(context)
        }
        // Stable per app + type: a merged loop updates the same notification
        // instead of stacking a new one per crash.
        val slot = Math.floorMod(
            (signal.type.name + "|" + signal.packageName.orEmpty()).hashCode(),
            1_000_000,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            slot + 20,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = signal.snippet.take(NOTIFICATION_BODY)
        val copy = PendingIntent.getBroadcast(
            context,
            slot + 40,
            Intent(context, CopyCrashReceiver::class.java)
                .putExtra(Intent.EXTRA_TEXT, signal.snippet.take(COPY_BODY)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(signal.firstLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.copy), copy)
            .build()
        manager.notify(ID_CRASH_BASE + slot, notification)
    }

    /** Opens the system page for this crash's channel, creating it first. */
    fun openCrashChannel(type: String, packageName: String?, separateChannels: Boolean) {
        val channel = channelId(type, packageName, separateChannels)
        val label = packageName ?: context.getString(R.string.unknown_app)
        val crashType = runCatching { CrashType.valueOf(type) }.getOrDefault(CrashType.CRASH)
        ensureChannel(channel, crashType, label, separateChannels)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channel)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun channelId(type: String, packageName: String?, separateChannels: Boolean): String {
        if (!separateChannels || packageName.isNullOrBlank()) return CHANNEL_CRASHES
        return "crash_${type}_$packageName"
    }

    private fun ensureChannel(
        channelId: String,
        type: CrashType,
        label: String,
        separateChannels: Boolean,
    ) {
        if (!separateChannels || channelId == CHANNEL_CRASHES) {
            if (manager.getNotificationChannel(CHANNEL_CRASHES) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_CRASHES,
                        context.getString(R.string.notif_channel_crashes),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
            return
        }
        val typeName = when (type) {
            CrashType.CRASH -> context.getString(R.string.crash_type_java)
            CrashType.NATIVE -> context.getString(R.string.crash_type_native)
            CrashType.ANR -> context.getString(R.string.crash_type_anr)
        }
        // Group id stays on the enum name so a locale change does not
        // fork a second group for the same crash type.
        val groupId = "crashes_${type.name}"
        manager.createNotificationChannelGroup(
            android.app.NotificationChannelGroup(groupId, typeName),
        )
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_crash_app, typeName, label),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { group = groupId },
        )
    }

    /**
     * Posted when a recording auto-stops after hitting the size limit.
     * Tapping it opens the just-finished session, not the app root.
     */
    fun notifyRecordingLimit(maxMb: Int, sessionId: Long?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_recording_limit_title))
            .setContentText(context.getString(R.string.notif_recording_limit_text, maxMb))
            .setContentIntent(sessionIntent(5, sessionId))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_RECORDING_LIMIT, notification)
    }

    /** Posted when a recording auto-stops after hitting the duration cap. */
    fun notifyRecordingTimeout(maxHours: Int, sessionId: Long?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_recording_limit_title))
            .setContentText(context.getString(R.string.notif_recording_timeout_text, maxHours))
            .setContentIntent(sessionIntent(6, sessionId))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_RECORDING_TIMEOUT, notification)
    }

    /** Posted when a recording auto-stops because the session file refused
     *  a write (disk full). Tapping opens the partially saved session. */
    fun notifyRecordingWriteError(sessionId: Long?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_recording_limit_title))
            .setContentText(context.getString(R.string.notif_recording_write_error_text))
            .setContentIntent(sessionIntent(10, sessionId))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_RECORDING_WRITE_ERROR, notification)
    }

    /** Opens [MainActivity] at the finished session's detail page (or the
     *  app root when no session is attached). */
    private fun sessionIntent(requestCode: Int, sessionId: Long?): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            MainActivity.sessionIntent(context, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
