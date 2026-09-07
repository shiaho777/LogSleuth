package io.github.shiaho777.logsleuth.app.core.logcat

import java.util.Calendar

/** Result of parsing a single raw logcat line. */
sealed interface ParsedLine {
    data class Entry(val entry: LogcatEntry) : ParsedLine

    /** A line that does not start a new entry: continuation of a multiline
     * message, a "beginning of ..." marker, or noise. */
    data class Continuation(val text: String) : ParsedLine
}

/**
 * Parser for `logcat -v threadtime` output, with optional `-v uid` prefix:
 *
 *   `MM-DD HH:MM:SS.mmm [UID] PID TID LEVEL TAG      : message`
 *
 * threadtime has no year; it is inferred as the current year and corrected
 * around New Year (a "future" date is moved one year back).
 */
object LogcatParser {

    private val THREADTIME =
        Regex("""^(\d{2}-\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+((?:\d+\s+)+)([VDIWEF])\s+(.*?)\s*:\s(.*)$""")

    fun parse(line: String, nowMillis: Long = System.currentTimeMillis()): ParsedLine {
        val m = THREADTIME.matchEntire(line) ?: return ParsedLine.Continuation(line)

        val nums = m.groupValues[6].trim().split(Regex("""\s+""")).mapNotNull { it.toIntOrNull() }
        val (uid, pid, tid) = when (nums.size) {
            2 -> Triple(null, nums[0], nums[1])
            3 -> Triple(nums[0], nums[1], nums[2])
            else -> return ParsedLine.Continuation(line)
        }

        val monthDay = m.groupValues[1]
        val month = monthDay.substring(0, 2).toIntOrNull() ?: return ParsedLine.Continuation(line)
        val day = monthDay.substring(3, 5).toIntOrNull() ?: return ParsedLine.Continuation(line)
        val hour = m.groupValues[2].toIntOrNull() ?: return ParsedLine.Continuation(line)
        val minute = m.groupValues[3].toIntOrNull() ?: return ParsedLine.Continuation(line)
        val second = m.groupValues[4].toIntOrNull() ?: return ParsedLine.Continuation(line)
        val milli = m.groupValues[5].toIntOrNull() ?: return ParsedLine.Continuation(line)

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, milli)
        }
        // Year-boundary correction: entries stamped "in the future" belong to last year.
        if (cal.timeInMillis - nowMillis > 24L * 60 * 60 * 1000) {
            cal.add(Calendar.YEAR, -1)
        }

        val entry = LogcatEntry(
            timestampMillis = cal.timeInMillis,
            pid = pid,
            tid = tid,
            uid = uid,
            level = LogLevel.from(m.groupValues[7].first()),
            tag = m.groupValues[8].trim(),
            message = m.groupValues[9],
            raw = line,
        )
        return ParsedLine.Entry(entry)
    }
}
