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
    val remoteEndpoint: String? = null,
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
 * Blocks are tracked **per process**: two apps crashing at the same moment
 * interleave their stack lines in logcat, and a single-slot detector used to
 * cut the first crash short when the second one's FATAL line arrived. Each
 * open block only consumes its own pid's continuation lines; unrelated lines
 * pass through to the other blocks and to ANR detection.
 *
 * Feed every entry via [onEntry]; it returns a completed [CrashSignal] or
 * null. Call [flush] when the stream stops, then [drain] until it returns
 * null, to emit every still-open block.
 */
class CrashDetector(
    private val maxBlockLines: Int = 100,
    private val anrDedupeWindowMs: Long = 60_000,
    private val nativeNameWaitMs: Long = 8_000,
    private val nativeDedupeWindowMs: Long = 60_000,
    private val maxOpenBlocks: Int = 8,
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

    /** One in-progress crash block, keyed by the emitting logcat pid. */
    private class Block(val key: Int, val entry: LogcatEntry, maxBlockLines: Int) {
        val native = isNativeStart(entry)
        val tag = entry.tag
        val minPriority = if (native) LogLevel.V.priority else LogLevel.E.priority
        val type = if (native) CrashType.NATIVE else CrashType.CRASH
        val text = StringBuilder(entry.raw).append('\n')
        var lines = 1
        var firstLine = entry.message.take(200)
        var startTime = entry.timestampMillis
        var packageName: String? = null
        val pid: Int

        init {
            if (native) {
                // `... in tid N (thread), pid M (comm)`. `comm` is
                // /proc/self/comm (the main thread), which apps rename to
                // "main". The logcat pid column on this libc line is the
                // crashed process; crash_dump's own pid only shows up on
                // the later DEBUG tombstone.
                val m = FATAL_PID.find(entry.message)
                pid = m?.groupValues?.get(1)?.toIntOrNull() ?: entry.pid
                packageName = m?.groupValues?.get(2)?.let(::packageFromComm)
            } else {
                pid = entry.pid
            }
        }

        val awaitingName: Boolean get() = native && packageName == null

        fun accepts(entry: LogcatEntry, maxLines: Int): Boolean =
            entry.tag == tag &&
                entry.level.priority >= minPriority &&
                lines < maxLines

        fun append(entry: LogcatEntry) {
            text.appendLine(entry.raw)
            lines++
            if (packageName == null) {
                packageName = PROCESS_NAME.find(entry.message)?.groupValues?.get(1)
            }
        }

        fun signal(): CrashSignal = CrashSignal(
            type = type,
            pid = pid,
            packageName = packageName,
            firstLine = firstLine,
            snippet = text.toString().trimEnd(),
            timeMillis = startTime,
        )
    }

    /** Open blocks in arrival order; the first is the "primary" one whose
     * completion [onEntry] reports, mirroring the old single-slot contract. */
    private val blocks = LinkedHashMap<Int, Block>()

    fun onEntry(entry: LogcatEntry): CrashSignal? {
        // Tombstone header (crash_dump, different pid/tag) names the process.
        // It arrives after unrelated log lines, so it is not a continuation.
        for (block in blocks.values.toList()) {
            if (!block.awaitingName) continue
            val found = tombstonePackage(block, entry) ?: continue
            block.packageName = found
            block.text.appendLine(entry.raw)
            return close(block)
        }

        // A fresh crash line starts a block for its own process. Another
        // process crashing at the same time must not cut this one short —
        // its stack lines keep arriving interleaved. A second fatal from
        // the *same* process, though, means the previous crash is over.
        if (isFatalStart(entry) || isNativeStart(entry)) {
            if (isNativeStart(entry) && isDuplicateNativeLine(entry)) return null
            val samePid = blocks.remove(entry.pid)
            // A new crash ends an open *native* block of another process
            // (its dump is over once something else dies loudly), but Java
            // blocks keep receiving their interleaved stack lines — two
            // apps crashing at once must both keep their full stacks.
            val nativeOther = blocks.values.firstOrNull { it.type == CrashType.NATIVE }
            val flushed = samePid?.let { close(it) } ?: nativeOther?.let { close(it) }
            if (blocks.size >= maxOpenBlocks) {
                // A wedged stream must not grow blocks without bound; the
                // oldest one is the least likely to still receive lines.
                close(blocks.values.first())
            }
            blocks[entry.pid] = Block(entry.pid, entry, maxBlockLines)
            return flushed
        }

        val block = blocks[entry.pid]
        if (block != null) {
            if (block.accepts(entry, maxBlockLines)) {
                block.append(entry)
                tombstonePackage(block, entry)?.let { block.packageName = it }
                return null
            }
            // App comm is "main"; keep the block open until the tombstone
            // names the package, without dropping ANRs that pass in between.
            if (block.awaitingName && entry.timestampMillis - block.startTime <= nativeNameWaitMs) {
                return detectStart(entry)
            }
            val done = close(block)
            // The current line may itself be an ANR; handle it too.
            return done ?: detectStart(entry)
        }
        // Unrelated line while blocks are open. A native block still waiting
        // for its tombstone tolerates the traffic inside the wait window
        // (the tombstone comes from crash_dump's own pid); every other open
        // block ends here, exactly as the single-slot detector did.
        val waiting = blocks.values.firstOrNull {
            it.awaitingName && entry.timestampMillis - it.startTime <= nativeNameWaitMs
        }
        if (waiting != null) return detectStart(entry)
        val oldest = blocks.values.firstOrNull()
        if (oldest != null) {
            val done = close(oldest)
            return done ?: detectStart(entry)
        }
        return detectStart(entry)
    }

    /** Emits the oldest still-open block, or null when none remain. */
    fun flush(): CrashSignal? {
        val block = blocks.values.firstOrNull() ?: return null
        return close(block)
    }

    /** Repeated [flush] until null — callers that need every open block. */
    fun drain(): List<CrashSignal> {
        val out = ArrayList<CrashSignal>()
        while (true) out += flush() ?: break
        return out
    }

    private fun close(block: Block): CrashSignal {
        blocks.remove(block.key)
        val signal = block.signal()
        if (signal.type == CrashType.NATIVE) {
            rememberNative(signal.pid, signal.firstLine, signal.timeMillis)
        }
        return signal
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

    /** `pid: N, tid: N, name: thread  >>> com.pkg <<<` from crash_dump. */
    private fun tombstonePackage(block: Block, entry: LogcatEntry): String? {
        if (entry.tag != "DEBUG" && entry.tag != "libc") return null
        val match = tombstoneRegex.find(entry.message) ?: return null
        val linePid = match.groupValues[1].toIntOrNull() ?: return null
        if (linePid != block.pid && linePid != block.key) return null
        val name = match.groupValues[2].trim()
        if (name.isEmpty() || name == "UNKNOWN" || name == "main") return null
        return name
    }

    private fun isDuplicateNativeLine(entry: LogcatEntry): Boolean {
        val parsed = fatalPidRegex.find(entry.message)
        val nativePid = parsed?.groupValues?.get(1)?.toIntOrNull() ?: entry.pid
        return isDuplicateNative(nativePid, entry.message.take(200), entry.timestampMillis)
    }

    private fun isDuplicateNative(nativePid: Int, line: String, timeMillis: Long): Boolean {
        if (blocks.values.any {
                it.type == CrashType.NATIVE && it.pid == nativePid && it.firstLine == line
            }
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

    private companion object {
        val PROCESS_NAME = Regex("""Process:\s*([\w.]+)\s*,\s*PID:""")
        val FATAL_PID = Regex("""pid\s+(\d+)\s+\(([^)]+)\)""")

        fun isFatalStart(entry: LogcatEntry): Boolean =
            entry.tag == "AndroidRuntime" &&
                entry.level.priority >= LogLevel.E.priority &&
                entry.message.startsWith("FATAL EXCEPTION")

        fun isNativeStart(entry: LogcatEntry): Boolean =
            (entry.tag == "DEBUG" || entry.tag == "libc") &&
                entry.message.startsWith("Fatal signal")

        /** `/proc/self/comm` is not a package when the main thread is named `main`. */
        fun packageFromComm(comm: String): String? {
            val name = comm.trim()
            if (name.isEmpty() || name == "main" || name == "<unknown>" || name == "<name unknown>") {
                return null
            }
            return name
        }
    }
}