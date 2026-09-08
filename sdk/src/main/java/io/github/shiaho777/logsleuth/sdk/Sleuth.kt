package io.github.shiaho777.logsleuth.sdk

import android.content.Context
import io.github.shiaho777.logsleuth.sdk.internal.AnrWatchdog
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

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sleuth-writer").apply { isDaemon = true }
    }

    /**
     * Starts logging. Call from Application.onCreate, as early as possible.
     * Safe to call more than once (subsequent calls are ignored).
     */
    @Synchronized
    @JvmOverloads
    fun init(context: Context, config: SleuthConfig = SleuthConfig.Builder().build()) {
        if (initialized) return
        initialized = true
        this.config = config

        val appContext = context.applicationContext
        store = LogFileStore(appContext, config)
        store.writeHeader()

        crashHandler = SleuthCrashHandler(config, store) { report ->
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
    }

    /**
     * Registers a callback for captured crashes. Depending on
     * [SleuthConfig.onCrashInvokedOnNextStart] it fires on the next app start
     * (default, recommended) or immediately in the crashing process.
     */
    fun onCrash(listener: ((CrashReport) -> Unit)?) {
        crashListener = listener
    }

    /**
     * Manually records an event/exception into the log without crashing.
     * Useful for breadcrumbs: `Sleuth.log("checkout", "user tapped pay")`.
     */
    @JvmOverloads
    fun log(tag: String = "Sleuth", message: String) {
        if (!initialized) return
        writer.execute { store.append("MANUAL $tag: $message\n") }
    }

    /** Records a handled exception. */
    fun logException(tag: String = "Sleuth", throwable: Throwable) {
        if (!initialized) return
        writer.execute { store.appendBlock("HANDLED $tag:\n${throwable.stackTraceToString()}\n") }
    }

    /** Current log files (oldest first). For tests and custom reporters. */
    fun logFiles(): List<File> =
        if (initialized) store.logFiles().sortedBy { it.name } else emptyList()

    /**
     * Bundles the captured logs into a zip and opens the system share sheet.
     * Typically wired to a "Send logs to support" button.
     */
    fun shareLogs(context: Context) {
        check(initialized) { "Call Sleuth.init() first" }
        // submit+get: execute the zip build on the single writer thread so it
        // happens strictly after all pending log writes have flushed.
        writer.submit {
            val zip = LogSharer.buildZip(context.applicationContext, store)
            LogSharer.share(context, zip)
        }.get()
    }
}
