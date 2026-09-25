package io.github.shiaho777.logsleuth.app.core.detect

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

enum class CrashType { CRASH, ANR, NATIVE }

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
 * - NATIVE: a `Fatal signal` line from the crash buffer (tag `DEBUG`,
 *   emitted by crash_dump; `libc` on some ROMs), followed by the tombstone
 *   dump lines from the same pid/tag at any level.
 * - ANR: `ANR in <package>` on the main buffer (tag `ActivityManager`), or
 *   an `am_anr` event line whose payload is `[user,pid,package,flags,reason]`.
 *   One episode typically produces both lines ~simultaneously and a stalled
 *   app re-logs `ANR in` while unresponsive — same-package signals within
 *   [anrDedupeWindowMs] are deduplicated.
 *
 * Feed every entry via [onEntry]; it returns a completed [CrashSignal] or null.
 * Call [flush] when the stream stops to emit a still-open crash block.
 */
class CrashDetector(
    private val maxBlockLines: Int = 100,
    private val anrDedupeWindowMs: Long = 60_000,
) {

    private val processRegex = Regex("""Process:\s*([\w.]+)\s*,\s*PID:""")
    private val anrRegex = Regex("""ANR in\s+([\w.]+)""")
    private val fatalPidRegex = Regex("""pid\s+(\d+)\s+\(([\w.]+)\)""")
    private val amAnrRegex = Regex("""\[\s*-?\d+\s*,\s*(-?\d+)\s*,\s*([\w.]+)""")

    /** Last ANR signal time per package — one ANR episode appears on both
     * the events buffer (`am_anr`) and the main buffer (`ANR in`), and a
     * continuing ANR re-logs while the dialog is up. Same-package repeats
     * inside the window are the same episode, not new events. */
    private val lastAnrAt = HashMap<String, Long>()

    private var collectingPid: Int? = null
    private var collectingTag: String? = null

    /** Minimum level a continuation line needs — E for Java fatals, any
     * level for native dumps (crash_dump writes them at I/D). */
    private var blockMinPriority = LogLevel.E.priority
    private val block = StringBuilder()
    private var blockLines = 0
    private var blockType = CrashType.CRASH
    private var firstLine = ""
    private var packageName: String? = null
    private var startTime = 0L
    private var pid = -1

    fun onEntry(entry: LogcatEntry): CrashSignal? {
        // A fresh crash line always starts a new block; flush any open one
        // first (the previous process died, this is a different crash).
        val newBlock = isFatalStart(entry) || isNativeStart(entry)
        if (newBlock) {
            val flushed = if (collectingPid != null) finishBlock() else null
            startBlock(entry)
            return flushed
        }

        val pidBeingCollected = collectingPid
        if (pidBeingCollected != null) {
            val stillInBlock = entry.pid == pidBeingCollected &&
                entry.tag == collectingTag &&
                entry.level.priority >= blockMinPriority &&
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

    private fun isNativeStart(entry: LogcatEntry): Boolean =
        (entry.tag == "DEBUG" || entry.tag == "libc") &&
            entry.message.startsWith("Fatal signal")

    private fun startBlock(entry: LogcatEntry) {
        val native = isNativeStart(entry)
        collectingPid = entry.pid
        collectingTag = entry.tag
        blockMinPriority = if (native) LogLevel.V.priority else LogLevel.E.priority
        blockType = if (native) CrashType.NATIVE else CrashType.CRASH
        block.clear()
        block.appendLine(entry.raw)
        blockLines = 1
        firstLine = entry.message.take(200)
        startTime = entry.timestampMillis
        if (native) {
            // `... in tid N (thread), pid M (com.pkg)` — the payload names the
            // crashed process; the logcat pid column is crash_dump's own.
            val m = fatalPidRegex.find(entry.message)
            pid = m?.groupValues?.get(1)?.toIntOrNull() ?: entry.pid
            packageName = m?.groupValues?.get(2)
        } else {
            pid = entry.pid
            packageName = null
        }
    }

    private fun detectStart(entry: LogcatEntry): CrashSignal? {
        // "ANR in" is an ActivityManager line — an app quoting the marker in
        // its own message must not raise a false event.
        if (entry.tag == "ActivityManager") {
            anrRegex.find(entry.message)?.let { match ->
                return anrSignal(
                    entry = entry,
                    pid = entry.pid,
                    packageName = match.groupValues[1],
                )
            }
        }
        // `am_anr` from the events buffer: [userId,pid,package,flags,reason].
        // The pid/package in the payload identify the stalled app, not
        // system_server which emits the line.
        if (entry.tag == "am_anr") {
            amAnrRegex.find(entry.message)?.let { match ->
                return anrSignal(
                    entry = entry,
                    pid = match.groupValues[1].toIntOrNull() ?: entry.pid,
                    packageName = match.groupValues[2],
                )
            }
        }
        return null
    }

    /** Builds an ANR signal unless the same package already signalled within
     * [anrDedupeWindowMs] — the two buffers report one episode together. */
    private fun anrSignal(entry: LogcatEntry, pid: Int, packageName: String): CrashSignal? {
        val last = lastAnrAt[packageName]
        if (last != null && entry.timestampMillis - last < anrDedupeWindowMs) {
            return null
        }
        lastAnrAt[packageName] = entry.timestampMillis
        return CrashSignal(
            type = CrashType.ANR,
            pid = pid,
            packageName = packageName,
            firstLine = entry.message.take(200),
            snippet = entry.raw,
            timeMillis = entry.timestampMillis,
        )
    }

    private fun finishBlock(): CrashSignal {
        val signal = CrashSignal(
            type = blockType,
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
