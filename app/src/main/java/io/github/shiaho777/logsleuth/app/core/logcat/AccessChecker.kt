package io.github.shiaho777.logsleuth.app.core.logcat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus

/** How the app can access device logs. */
enum class AccessKind {
    /** Via Shizuku: full logcat, including the uid column. */
    SHIZUKU,

    /** Via an opt-in root shell (libsu). Same logcat as Shizuku, including uid. */
    ROOT,

    /** Via the ADB-granted READ_LOGS permission. */
    READ_LOGS,

    /** No usable grant yet. */
    NONE,
}

data class AccessState(
    val kind: AccessKind,
    val shizukuStatus: ShizukuStatus,
    val readLogsGranted: Boolean,
    val rootReady: Boolean = false,
) {
    val granted: Boolean get() = kind != AccessKind.NONE
}

/** Decides which log access path is currently usable. */
class AccessChecker(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
) {
    fun readLogsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) ==
            PackageManager.PERMISSION_GRANTED

    fun currentState(
        preference: String = AccessPolicy.AUTO,
        rootReady: Boolean = false,
    ): AccessState {
        // Grants toggled inside the Shizuku app raise no callback, so the
        // cached status would go stale — re-ping on every access check.
        shizukuManager.refresh()
        val shizuku = shizukuManager.status.value
        val readLogs = readLogsGranted()
        val kind = AccessPolicy.choose(
            preference = preference,
            shizukuReady = shizuku == ShizukuStatus.READY,
            rootReady = rootReady,
            readLogs = readLogs,
        )
        return AccessState(kind, shizuku, readLogs, rootReady)
    }
}
