package io.github.shiaho777.logsleuth.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.shiaho777.logsleuth.sdk.internal.AnrWatchdog
import io.github.shiaho777.logsleuth.sdk.internal.CrashReportCodec
import io.github.shiaho777.logsleuth.sdk.internal.LogFileStore
import io.github.shiaho777.logsleuth.sdk.internal.LogSharer
import io.github.shiaho777.logsleuth.sdk.internal.LogcatCaptureThread
import io.github.shiaho777.logsleuth.sdk.internal.SleuthCrashHandler
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * LogSleuth SDK: embeds lightweight, permission-free logging into your app.
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Sleuth.init(this)
 *     }
 * }
 *
 * // Anywhere:
 * Sleuth.shareLogs(activity)
 * ```
 */
object Sleuth {

    @Volatile
    private var initialized = false

    private var config: SleuthConfig = SleuthConfig.Builder().build()
    private lateinit var store: LogFileStore
    private var logcatThread: LogcatCaptureThread? = null
    private var anrWatchdog: AnrWatchdog? = null
    private var crashHandler: SleuthCrashHandler? = null

    @Volatile
    private var crashListener: ((CrashReport) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sleuth-writer").apply { isDaemon = true }
    }

    /**
     * Starts logging. Call from Application.onCreate, as early as possible.
     * Safe to call more than once (subsequent calls are ignored); if a call
     * throws, the SDK stays un-initialized and can be retried.
     */
    @Synchronized
    @JvmOverloads
    fun init(context: Context, config: SleuthConfig = SleuthConfig.Builder().build()) {
        if (initialized) return
        this.config = config

        val appContext = context.applicationContext
        try {
            store = LogFileStore.forContext(appContext, config)
            // Header first on the single writer thread — FIFO order guarantees
            // it lands before any capture lines.
            writer.execute { store.writeHeader() }

            crashHandler = SleuthCrashHandler(config, store) { report ->
                // Immediate mode fires on the dying thread — posting to the
                // main looper here could outlive neither process nor caller.
                crashListener?.invoke(report)
            }.also { it.install() }

            if (config.captureLogcat) {
                logcatThread = LogcatCaptureThread(config) { line ->
                    writer.execute { store.append(line + "\n") }
                }.also { it.start() }
            }

            if (config.watchAnr) {
                anrWatchdog = AnrWatchdog(thresholdMs = 5_000) { block ->
                    writer.execute { store.appendBlock("\n$block\n") }
                }.also { it.start() }
            }

            initialized = true
            deliverPendingCrash()
        } catch (t: Throwable) {
            logcatThread?.shutdown()
            anrWatchdog?.shutdown()
            logcatThread = null
            anrWatchdog = null
            crashHandler = null
            throw t
        }
    }

    /**
     * Registers a callback for captured crashes. Depending on
     * [SleuthConfig.onCrashInvokedOnNextStart] it fires on the next app start
     * (default, recommended) or immediately in the crashing process.
     *
     * With the default mode the callback fires whenever both conditions hold:
     * a crash is pending from a previous run *and* a listener is registered —
     * so it works whether you register before or after [init].
     */
    fun onCrash(listener: ((CrashReport) -> Unit)?) {
        crashListener = listener
        deliverPendingCrash()
    }

    /**
     * Delivers a crash report left by a previous run, once a listener exists.
     * Serialized on the writer executor; the flag file doubles as the
     * exactly-once guard (first deleter wins).
     */
    private fun deliverPendingCrash() {
        if (!initialized || !config.onCrashInvokedOnNextStart) return
        writer.execute {
            val listener = crashListener ?: return@execute
            val flag = store.pendingCrashFlag()
            if (!flag.exists()) return@execute
            runCatching {
                val report = CrashReportCodec.parse(store.crashFile().readText())
                if (flag.delete()) {
                    mainHandler.post { listener.invoke(report) }
                }
            }
        }
    }

    /**
     * Manually records an event into the log without crashing.
     * Useful for breadcrumbs: `Sleuth.log("checkout", "user tapped pay")`.
     * Emitted in threadtime format so it shows up as a normal entry when the
     * zip is replayed in the LogSleuth viewer.
     */
    @JvmOverloads
    fun log(tag: String = "Sleuth", message: String) {
        if (!initialized) return
        writer.execute { store.append(syntheticLine('I', tag, message) + "\n") }
    }

    /** Records a handled exception. */
    fun logException(tag: String = "Sleuth", throwable: Throwable) {
        if (!initialized) return
        writer.execute {
            store.append(syntheticLine('E', tag, "HANDLED exception:") + "\n")
            store.appendBlock(throwable.stackTraceToString() + "\n")
        }
    }

    /** Current log files, oldest first. For tests and custom reporters. */
    fun logFiles(): List<File> =
        if (initialized) store.logFiles() else emptyList()

    /**
     * Bundles the captured logs into a zip and opens the system share sheet.
     * Typically wired to a "Send logs to support" button.
     *
     * Fully asynchronous: the zip is built on the SDK's writer thread (strictly
     * after all pending log writes) and the share sheet is posted to the main
     * looper, so calling this from the UI thread is safe.
     */
    fun shareLogs(context: Context) {
        check(initialized) { "Call Sleuth.init() first" }
        writer.execute {
            val zip = LogSharer.buildZip(context.applicationContext, store)
            LogSharer.share(context, zip)
        }
    }

    /** `MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message` — the format logcat
     * emits and the LogSleuth viewer parses. */
    private fun syntheticLine(level: Char, tag: String, message: String): String {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "%02d-%02d %02d:%02d:%02d.%03d %5d %5d %c %s: %s".format(
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            cal.get(java.util.Calendar.MILLISECOND),
            android.os.Process.myPid(),
            android.os.Process.myTid(),
            level,
            tag,
            message,
        )
    }
}
