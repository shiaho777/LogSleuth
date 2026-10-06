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
 * - NATIVE: a `Fatal signal` line (tag `libc`, from the crashing process;
 *   tag `DEBUG` on some ROMs). The parenthetical after `pid` is
 *   `/proc/self/comm`. App processes rename that to `main`, so it is not a
 *   package name — the package is taken from the later tombstone line
 *   `pid: N, tid: N, name: … >>> com.pkg <<<` (tag `DEBUG`, crash_dump's own
 *   pid). The block stays open across unrelated lines for [nativeNameWaitMs]
 *   waiting for that line. The same fatal line is also copied into the crash
 *   buffer; a repeat with the same pid and text inside [nativeDedupeWindowMs]
 *   is the same crash.
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
    private val nativeNameWaitMs: Long = 8_000,
    private val nativeDedupeWindowMs: Long = 60_000,
) {

    private val processRegex = Regex("""Process:\s*([\w.]+)\s*,\s*PID:""")
    private val anrRegex = Regex("""ANR in\s+([\w.]+)""")
    private val fatalPidRegex = Regex("""pid\s+(\d+)\s+\(([^)]+)\)""")
    private val tombstoneRegex =
        Regex("""pid:\s*(\d+)\s*,\s*tid:\s*\d+\s*,\s*name:\s*\S+\s+>>>\s*(.+?)\s*<<<""")
    private val amAnrRegex = Regex("""\[\s*-?\d+\s*,\s*(-?\d+)\s*,\s*([\w.]+)""")

    /** Last ANR signal time per package — one ANR episode appears on both
     * the events buffer (`am_anr`) and the main buffer (`ANR in`), and a
     * continuing ANR re-logs while the dialog is up. Same-package repeats
     * inside the window are the same episode, not new events. */
    private val lastAnrAt = HashMap<String, Long>()

    /** Native fatals already emitted (pid + summary line). The libc line is
     * often duplicated into the crash buffer a moment later. */
    private val recentNative = ArrayDeque<NativeSeen>()

    private data class NativeSeen(val pid: Int, val firstLine: String, val timeMillis: Long)

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
        // Tombstone header (crash_dump, different pid/tag) names the process.
        // It arrives after unrelated log lines, so it is not a continuation.
        if (nativeAwaitingName()) {
            tombstonePackage(entry)?.let { found ->
                packageName = found
                block.appendLine(entry.raw)
                return finishBlock()
            }
        }

        // A fresh crash line always starts a new block; flush any open one
        // first (the previous process died, this is a different crash).
        val newBlock = isFatalStart(entry) || isNativeStart(entry)
        if (newBlock) {
            if (isNativeStart(entry) && isDuplicateNativeLine(entry)) return null
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
                        ?: tombstonePackage(entry)
                }
                return null
            }
            // App comm is "main"; keep the block open until the tombstone
            // names the package, without dropping ANRs that pass in between.
            if (nativeAwaitingName() && entry.timestampMillis - startTime <= nativeNameWaitMs) {
                return detectStart(entry)
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
            // `... in tid N (thread), pid M (comm)`. `comm` is /proc/self/comm
            // (the main thread), which apps rename to "main". The logcat pid
            // column on this libc line is the crashed process; crash_dump's
            // own pid only shows up on the later DEBUG tombstone.
            val m = fatalPidRegex.find(entry.message)
            pid = m?.groupValues?.get(1)?.toIntOrNull() ?: entry.pid
            packageName = m?.groupValues?.get(2)?.let(::packageFromComm)
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

    /** `/proc/self/comm` is not a package when the main thread is named `main`. */
    private fun packageFromComm(comm: String): String? {
        val name = comm.trim()
        if (name.isEmpty() || name == "main" || name == "<unknown>" || name == "<name unknown>") {
            return null
        }
        return name
    }

    /** `pid: N, tid: N, name: thread  >>> com.pkg <<<` from crash_dump. */
    private fun tombstonePackage(entry: LogcatEntry): String? {
        if (entry.tag != "DEBUG" && entry.tag != "libc") return null
        val match = tombstoneRegex.find(entry.message) ?: return null
        val linePid = match.groupValues[1].toIntOrNull() ?: return null
        if (linePid != pid && linePid != collectingPid) return null
        val name = match.groupValues[2].trim()
        if (name.isEmpty() || name == "UNKNOWN" || name == "main") return null
        return name
    }

    private fun nativeAwaitingName(): Boolean =
        collectingPid != null && blockType == CrashType.NATIVE && packageName == null

    private fun isDuplicateNativeLine(entry: LogcatEntry): Boolean {
        val parsed = fatalPidRegex.find(entry.message)
        val nativePid = parsed?.groupValues?.get(1)?.toIntOrNull() ?: entry.pid
        return isDuplicateNative(nativePid, entry.message.take(200), entry.timestampMillis)
    }

    private fun isDuplicateNative(nativePid: Int, line: String, timeMillis: Long): Boolean {
        if (collectingPid != null && blockType == CrashType.NATIVE &&
            pid == nativePid && firstLine == line
        ) {
            return true
        }
        return recentNative.any {
            it.pid == nativePid && it.firstLine == line &&
                timeMillis - it.timeMillis < nativeDedupeWindowMs
        }
    }

    private fun rememberNative(nativePid: Int, line: String, timeMillis: Long) {
        val cutoff = timeMillis - nativeDedupeWindowMs
        while (recentNative.isNotEmpty() && recentNative.first().timeMillis < cutoff) {
            recentNative.removeFirst()
        }
        recentNative.addLast(NativeSeen(nativePid, line, timeMillis))
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
        if (signal.type == CrashType.NATIVE) {
            rememberNative(signal.pid, signal.firstLine, signal.timeMillis)
        }
        collectingPid = null
        collectingTag = null
        block.clear()
        blockLines = 0
        return signal
    }
}
