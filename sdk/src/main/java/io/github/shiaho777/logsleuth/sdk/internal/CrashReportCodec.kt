package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.CrashReport

/**
 * Line-based serialization for [CrashReport]. Every field except the trailing
 * stack trace is escaped onto a single line so embedded newlines cannot shift
 * field boundaries on parse.
 */
internal object CrashReportCodec {

    fun serialize(r: CrashReport): String = buildString {
        appendLine(r.timeMillis)
        appendLine(r.threadName.escape())
        appendLine(r.exceptionClass.escape())
        appendLine((r.message ?: "").escape())
        append(r.stackTrace)
    }

    fun parse(text: String): CrashReport {
        val lines = text.lines()
        return CrashReport(
            timeMillis = lines.getOrNull(0)?.toLongOrNull() ?: 0L,
            threadName = lines.getOrNull(1).orEmpty().unescape(),
            exceptionClass = lines.getOrNull(2).orEmpty().unescape(),
            message = lines.getOrNull(3)?.unescape(),
            stackTrace = lines.drop(4).joinToString("\n"),
        )
    }

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\n", "\\n")

    // Single-pass unescape: `\\n` must decode to backslash+'n', not newline.
    private fun String.unescape(): String {
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            if (this[i] == '\\' && i + 1 < length) {
                when (this[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(this[i]); i++ }
                }
            } else {
                out.append(this[i]); i++
            }
        }
        return out.toString()
    }
}
