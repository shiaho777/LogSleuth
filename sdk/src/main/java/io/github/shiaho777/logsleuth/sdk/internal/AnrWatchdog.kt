package io.github.shiaho777.logsleuth.sdk.internal

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Classic watchdog: a background thread pings the main looper; if the main
 * thread doesn't respond within [thresholdMs], its stack is dumped.
 */
internal class AnrWatchdog(
    private val thresholdMs: Long,
    private val onAnr: (String) -> Unit,
) : Thread("sleuth-anr") {

    private val running = AtomicBoolean(true)
    private val tick = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { tick.incrementAndGet() }

    init {
        isDaemon = true
    }

    override fun run() {
        var lastReported = 0L
        while (running.get()) {
            val before = tick.get()
            mainHandler.post(ticker)
            try {
                sleep(thresholdMs)
            } catch (_: InterruptedException) {
                return
            }
            if (!running.get()) return
            if (tick.get() == before && System.currentTimeMillis() - lastReported > thresholdMs * 4) {
                lastReported = System.currentTimeMillis()
                val stack = Looper.getMainLooper().thread.stackTrace.joinToString("\n") {
                    "    at $it"
                }
                onAnr("===== ANR: main thread blocked > ${thresholdMs}ms =====\n$stack")
            }
        }
    }

    fun shutdown() {
        running.set(false)
        interrupt()
    }
}
