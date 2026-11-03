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
            record(
                CrashReport(
                    timeMillis = System.currentTimeMillis(),
                    threadName = thread.name,
                    exceptionClass = throwable.javaClass.name,
                    message = throwable.message,
                    stackTrace = throwable.stackTraceToString(),
                ),
            )
        }
        previous?.uncaughtException(thread, throwable)
    }

    /**
     * Persists [raw] and, in immediate mode, delivers it. The stored report
     * and the listener both get the redacted text, so `crash.txt` in the
     * shared zip never carries secrets the ring files already mask.
     */
    internal fun record(raw: CrashReport) {
        val report = raw.copy(
            message = raw.message?.let(store::redact),
            stackTrace = store.redact(raw.stackTrace),
        )
        // appendBlock redacts per line itself, so it takes the raw trace.
        store.appendBlock("\n===== FATAL EXCEPTION on thread ${raw.threadName} =====\n${raw.stackTrace}\n")
        store.crashFile().writeText(CrashReportCodec.serialize(report))
        store.pendingCrashFlag().createNewFile()
        if (!config.onCrashInvokedOnNextStart) {
            onCrashImmediate(report)
        }
    }
}
