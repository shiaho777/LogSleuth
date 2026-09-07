package io.github.logsleuth.sdk.internal

import io.github.logsleuth.sdk.CrashReport
import io.github.logsleuth.sdk.SleuthConfig

/**
 * Uncaught-exception handler that records the crash, then chains to the
 * previously installed handler so crash reporting tools / the system still
 * see the crash.
 */
internal class SleuthCrashHandler(
    private val config: SleuthConfig,
    private val store: LogFileStore,
    private val onCrashImmediate: (CrashReport) -> Unit,
) : Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        // Deliver a crash from a previous run if configured for next-start callbacks.
        if (config.onCrashInvokedOnNextStart) {
            val flag = store.pendingCrashFlag()
            if (flag.exists()) {
                runCatching {
                    val report = parseReport(store.crashFile().readText())
                    flag.delete()
                    onCrashImmediate(report)
                }
            }
        }
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
            store.crashFile().writeText(serialize(report))
            store.pendingCrashFlag().createNewFile()
            if (!config.onCrashInvokedOnNextStart) {
                onCrashImmediate(report)
            }
        }
        previous?.uncaughtException(thread, throwable)
    }

    private fun serialize(r: CrashReport): String = buildString {
        appendLine(r.timeMillis)
        appendLine(r.threadName)
        appendLine(r.exceptionClass)
        appendLine(r.message ?: "")
        append(r.stackTrace)
    }

    private fun parseReport(text: String): CrashReport {
        val lines = text.lines()
        return CrashReport(
            timeMillis = lines.getOrNull(0)?.toLongOrNull() ?: 0L,
            threadName = lines.getOrNull(1).orEmpty(),
            exceptionClass = lines.getOrNull(2).orEmpty(),
            message = lines.getOrNull(3),
            stackTrace = lines.drop(4).joinToString("\n"),
        )
    }
}
