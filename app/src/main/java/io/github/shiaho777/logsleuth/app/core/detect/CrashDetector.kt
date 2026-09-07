package io.github.shiaho777.logsleuth.app.core.detect

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

enum class CrashType { CRASH, ANR }

data class CrashSignal(
    val type: CrashType,
    val pid: Int,
    val packageName: String?,
    val firstLine: String,
    val snippet: String,
    val timeMillis: Long,
)

/**
 * Stateful detector for app crashes and ANRs inside the logcat stream.
 *
 * - CRASH: an `AndroidRuntime` error line starting with `FATAL EXCEPTION`,
 *   followed by the exception block (same pid, AndroidRuntime, E-level).
 *   The block ends on the first non-matching line or after [maxBlockLines].
 * - ANR: any line containing `ANR in <package>` (or `am_anr`).
 *
 * Feed every entry via [onEntry]; it returns a completed [CrashSignal] or null.
 * Call [flush] when the stream stops to emit a still-open crash block.
 */
class CrashDetector(private val maxBlockLines: Int = 100) {

    private val processRegex = Regex("""Process:\s*([\w.]+)\s*,\s*PID:""")
    private val anrRegex = Regex("""ANR in\s+([\w.]+)""")

    private var collectingPid: Int? = null
    private var collectingTag: String? = null
    private val block = StringBuilder()
    private var blockLines = 0
    private var firstLine = ""
    private var packageName: String? = null
    private var startTime = 0L
    private var pid = -1

    fun onEntry(entry: LogcatEntry): CrashSignal? {
        // A fresh FATAL line always starts a new block; flush any open one first
        // (the previous process died, this is a different crash).
        if (isFatalStart(entry)) {
            val flushed = if (collectingPid != null) finishBlock() else null
            startBlock(entry)
            return flushed
        }

        val pidBeingCollected = collectingPid
        if (pidBeingCollected != null) {
            val stillInBlock = entry.pid == pidBeingCollected &&
                entry.tag == collectingTag &&
                entry.level.priority >= LogLevel.E.priority &&
                blockLines < maxBlockLines
            if (stillInBlock) {
                block.appendLine(entry.raw)
                blockLines++
                if (packageName == null) {
                    packageName = processRegex.find(entry.message)?.groupValues?.get(1)
                }
                return null
            }
            val done = finishBlock()
            // The current line may itself be an ANR; handle it too.
            return done ?: detectStart(entry)
        }
        return detectStart(entry)
    }

    fun flush(): CrashSignal? = if (collectingPid != null) finishBlock() else null

    private fun isFatalStart(entry: LogcatEntry): Boolean =
        entry.tag == "AndroidRuntime" &&
            entry.level.priority >= LogLevel.E.priority &&
            entry.message.startsWith("FATAL EXCEPTION")

    private fun startBlock(entry: LogcatEntry) {
        collectingPid = entry.pid
        collectingTag = entry.tag
        block.clear()
        block.appendLine(entry.raw)
        blockLines = 1
        firstLine = entry.message.take(200)
        packageName = null
        startTime = entry.timestampMillis
        pid = entry.pid
    }

    private fun detectStart(entry: LogcatEntry): CrashSignal? {
        // ANR is a single-line event.
        anrRegex.find(entry.message)?.let { match ->
            return CrashSignal(
                type = CrashType.ANR,
                pid = entry.pid,
                packageName = match.groupValues[1],
                firstLine = entry.message.take(200),
                snippet = entry.raw,
                timeMillis = entry.timestampMillis,
            )
        }
        return null
    }

    private fun finishBlock(): CrashSignal {
        val signal = CrashSignal(
            type = CrashType.CRASH,
            pid = pid,
            packageName = packageName,
            firstLine = firstLine,
            snippet = block.toString().trimEnd(),
            timeMillis = startTime,
        )
        collectingPid = null
        collectingTag = null
        block.clear()
        blockLines = 0
        return signal
    }
}
