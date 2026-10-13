package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

/**
 * Lines from the live buffer saved beside a crash.
 *
 * The slice is the mixed tail. ANR lines are written by system_server, so
 * keeping only the crash pid would drop the app that actually stalled.
 */
object CrashContext {
    const val MAX_LINES = 200
    private const val SLACK_MS = 1_000L

    fun lines(
        entries: List<LogcatEntry>,
        timeMillis: Long,
        maxLines: Int = MAX_LINES,
    ): List<LogcatEntry> {
        if (entries.isEmpty() || maxLines <= 0) return emptyList()
        val cutoff = timeMillis + SLACK_MS
        return entries.filter { it.timestampMillis <= cutoff }.takeLast(maxLines)
    }

    fun format(entries: List<LogcatEntry>): String =
        entries.joinToString("\n") { it.raw }
}
