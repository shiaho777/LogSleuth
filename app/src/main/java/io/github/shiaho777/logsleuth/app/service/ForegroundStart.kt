package io.github.shiaho777.logsleuth.app.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log

private const val TAG = "LogSleuthFgs"

/**
 * Starts the service in the foreground, reporting refusal instead of throwing.
 *
 * `Service.startForeground` throws
 * [android.app.ForegroundServiceStartNotAllowedException] whenever the system
 * declines the start: an exhausted `dataSync` quota (Android 15+), a start from
 * the background without an exemption, or a missing type permission. Letting
 * that escape `onStartCommand` takes the whole process down, which is exactly
 * how the crash watch turned an exhausted quota into a restart loop (#118).
 *
 * Callers must treat `false` as "stop cleanly", never as "retry immediately".
 */
internal fun Service.startForegroundOrFalse(
    id: Int,
    notification: Notification,
    type: Int,
): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(id, notification, type)
    } else {
        startForeground(id, notification)
    }
    true
} catch (e: Exception) {
    // Not fatal: the service is simply not allowed to run right now. The
    // caller stops itself and leaves a note for the next foreground start.
    Log.w(TAG, "startForeground($type) refused", e)
    false
}

/** The type both long-running services declare in the manifest. */
internal const val FGS_TYPE_LONG_RUNNING = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
