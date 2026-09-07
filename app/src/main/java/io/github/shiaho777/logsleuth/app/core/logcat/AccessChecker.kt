package io.github.shiaho777.logsleuth.app.core.logcat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus

/** How the app can access device logs. */
enum class AccessKind {
    /** Via Shizuku: full logcat incl. `-v uid` server-side per-app filtering. */
    SHIZUKU,

    /** Via the ADB-granted READ_LOGS permission: full logcat, no uid column. */
    READ_LOGS,

    /** No usable grant yet. */
    NONE,
}

data class AccessState(
    val kind: AccessKind,
    val shizukuStatus: ShizukuStatus,
    val readLogsGranted: Boolean,
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

    fun currentState(): AccessState {
        val shizuku = shizukuManager.status.value
        val readLogs = readLogsGranted()
        val kind = when {
            shizuku == ShizukuStatus.READY -> AccessKind.SHIZUKU
            readLogs -> AccessKind.READ_LOGS
            else -> AccessKind.NONE
        }
        return AccessState(kind, shizuku, readLogs)
    }
}
