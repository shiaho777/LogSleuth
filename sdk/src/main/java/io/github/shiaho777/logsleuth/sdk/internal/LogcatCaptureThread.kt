package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.SleuthConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads this app's own process logs via `logcat --pid=<self>`. Reading your
 * own logs requires no permission on any Android version.
 *
 * If the logcat process dies (logd restart, SELinux hiccup) the capture
 * reconnects with exponential backoff instead of silently giving up —
 * the whole point of the SDK is that logs are still there when you need them.
 */
internal class LogcatCaptureThread(
    config: SleuthConfig,
    private val onLine: (String) -> Unit,
) : Thread("sleuth-logcat") {

    private val running = AtomicBoolean(true)

    @Volatile
    private var process: Process? = null

    private val filter = LineFilter(config.tagFilter)

    init {
        isDaemon = true
    }

    override fun run() {
        val pid = android.os.Process.myPid()
        var backoffMs = 1_000L
        while (running.get()) {
            try {
                val proc = ProcessBuilder("logcat", "-v", "threadtime", "--pid=$pid")
                    .redirectErrorStream(true)
                    .start()
                process = proc
                var sawLine = false
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        if (!sawLine) {
                            sawLine = true
                            backoffMs = 1_000L // healthy stream: reset backoff
                        }
                        if (filter.accepts(line)) onLine(line)
                    }
                }
            } catch (_: Throwable) {
                // Logcat unavailable (very old devices / restricted ROMs):
                // keep retrying while the SDK is alive.
                if (!running.get()) return
            }
            try {
                sleep(backoffMs)
            } catch (_: InterruptedException) {
                return
            }
            backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
        }
    }

    fun shutdown() {
        running.set(false)
        process?.destroy()
        interrupt()
    }
}
