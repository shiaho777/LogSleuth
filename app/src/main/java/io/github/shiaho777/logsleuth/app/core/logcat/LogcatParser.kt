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
 * The uid column may be a decimal, a multi-user token (`u0a123`), or a
 * well-known name (`system`). See [UidNames].
 *
 * threadtime has no year; it is inferred as the current year and corrected
 * around New Year (a "future" date is moved one year back).
 */
object LogcatParser {

    private val THREADTIME =
        Regex("""^(\d{2}-\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(.*)$""")

    /**
     * Optional uid token, then pid, tid, level, tag, message.
     * The uid alternative backtracks when the line has no uid column:
     * `4567 4589 D Tag: msg` fails the three-field shape and retries as two.
     */
    private val FIELDS =
        Regex("""^(?:(\S+)\s+)?(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?)\s*:\s(.*)$""")

    fun parse(line: String, nowMillis: Long = System.currentTimeMillis()): ParsedLine {
        // Readers that split on '\n' alone leave a stray '\r' on CRLF files.
        val clean = line.removeSuffix("\r")
        val head = THREADTIME.matchEntire(clean) ?: return ParsedLine.Continuation(clean)
        val fields = FIELDS.matchEntire(head.groupValues[6]) ?: return ParsedLine.Continuation(clean)

        val uidToken = fields.groupValues[1].takeIf { it.isNotEmpty() }
        val uid = uidToken?.let { UidNames.resolve(it) }
        // A uid-looking token that resolves to nothing is still a real column
        // (OEM name we don't know). Dropping the line would hide the log;
        // keeping it with a null uid just loses per-app grouping.
        val pid = fields.groupValues[2].toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val tid = fields.groupValues[3].toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val levelLetter = fields.groupValues[4]
        val tag = fields.groupValues[5].trim()
        val message = fields.groupValues[6]

        val monthDay = head.groupValues[1]
        val month = monthDay.substring(0, 2).toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val day = monthDay.substring(3, 5).toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val hour = head.groupValues[2].toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val minute = head.groupValues[3].toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val second = head.groupValues[4].toIntOrNull() ?: return ParsedLine.Continuation(clean)
        val milli = head.groupValues[5].toIntOrNull() ?: return ParsedLine.Continuation(clean)

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
            level = LogLevel.from(levelLetter.first()),
            tag = tag,
            message = message,
            raw = clean,
        )
        return ParsedLine.Entry(entry)
    }
}
