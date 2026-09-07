package io.github.logsleuth.sdk.internal

import io.github.logsleuth.sdk.SleuthConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads this app's own process logs via `logcat --pid=<self>`. Reading your
 * own logs requires no permission on any Android version.
 */
internal class LogcatCaptureThread(
    private val config: SleuthConfig,
    private val onLine: (String) -> Unit,
) : Thread("sleuth-logcat") {

    private val running = AtomicBoolean(true)

    @Volatile
    private var process: Process? = null

    init {
        isDaemon = true
    }

    override fun run() {
        val pid = android.os.Process.myPid()
        try {
            val proc = ProcessBuilder("logcat", "-v", "threadtime", "--pid=$pid")
                .redirectErrorStream(true)
                .start()
            process = proc
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    if (accepts(line)) onLine(line)
                }
            }
        } catch (_: Throwable) {
            // Logcat unavailable (very old devices / restricted ROMs): SDK silently degrades.
        }
    }

    private fun accepts(line: String): Boolean {
        val filter = config.tagFilter ?: return true
        if (line.contains(filter)) return true
        // Always keep errors and our own crash marker lines.
        return line.contains(" E ") || line.contains(" F ") || line.contains("AndroidRuntime")
    }

    fun shutdown() {
        running.set(false)
        process?.destroy()
        interrupt()
    }
}
