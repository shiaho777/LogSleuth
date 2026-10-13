package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.core.detect.CrashType

/**
 * Saving a crash and pinging about it are separate. The master switch
 * turns every notification off; the per-type flags choose which kinds
 * still notify when the master is on.
 */
data class CrashCapture(
    val collectJava: Boolean = true,
    val collectNative: Boolean = true,
    val collectAnr: Boolean = true,
    val notifyMaster: Boolean = true,
    val notifyJava: Boolean = true,
    val notifyNative: Boolean = true,
    val notifyAnr: Boolean = true,
)

object CrashPolicy {
    fun stores(type: CrashType, capture: CrashCapture): Boolean = when (type) {
        CrashType.CRASH -> capture.collectJava
        CrashType.NATIVE -> capture.collectNative
        CrashType.ANR -> capture.collectAnr
    }

    fun notifies(type: CrashType, capture: CrashCapture): Boolean {
        if (!capture.notifyMaster) return false
        return when (type) {
            CrashType.CRASH -> capture.notifyJava
            CrashType.NATIVE -> capture.notifyNative
            CrashType.ANR -> capture.notifyAnr
        }
    }
}
