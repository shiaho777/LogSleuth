package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.CrashReport
import io.github.shiaho777.logsleuth.sdk.SleuthConfig

/**
 * Uncaught-exception handler that records the crash, then chains to the
 * previously installed handler so crash reporting tools / the system still
 * see the crash.
 *
 * Pending-crash delivery is *not* done here: the listener is registered via
 * `Sleuth.onCrash` after `Sleuth.init` returns, so delivering inside install()
 * would fire before anyone could possibly listen. `Sleuth` drains the pending
 * flag itself once a listener exists.
 */
internal class SleuthCrashHandler(
    private val config: SleuthConfig,
    private val store: LogFileStore,
    private val onCrashImmediate: (CrashReport) -> Unit,
) : Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            val trace = throwable.stackTraceToString()
            val report = CrashReport(
                timeMillis = System.currentTimeMillis(),
                threadName = thread.name,
                exceptionClass = throwable.javaClass.name,
                message = throwable.message,
                stackTrace = trace,
            )
            store.appendBlock("\n===== FATAL EXCEPTION on thread ${thread.name} =====\n$trace\n")
            store.crashFile().writeText(CrashReportCodec.serialize(report))
            store.pendingCrashFlag().createNewFile()
            if (!config.onCrashInvokedOnNextStart) {
                onCrashImmediate(report)
            }
        }
        previous?.uncaughtException(thread, throwable)
    }
}
