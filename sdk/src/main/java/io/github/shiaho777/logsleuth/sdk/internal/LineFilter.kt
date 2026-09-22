package io.github.shiaho777.logsleuth.sdk.internal

/**
 * Decides which raw `logcat -v threadtime` lines get recorded when a tag
 * filter is configured. Stateful: continuation lines (multi-line message
 * bodies, e.g. stack traces) keep the verdict of the entry they belong to,
 * otherwise a filtered stream would lose every crash's stack frames.
 *
 * Error-or-fatal lines always pass so crashes survive any filter.
 */
internal class LineFilter(private val tagFilter: String?) {

    private var keepContinuations = false

    fun accepts(line: String): Boolean {
        val filter = tagFilter ?: return true
        val m = HEADER.find(line)
        if (m == null) return keepContinuations
        val keep = m.groupValues[2].contains(filter, ignoreCase = true) ||
            m.groupValues[1].first() in ERROR_LEVELS
        keepContinuations = keep
        return keep
    }

    private companion object {
        // MM-DD HH:MM:SS.mmm [UID] PID TID LEVEL TAG: ... — the numeric block
        // may carry an extra uid column.
        val HEADER = Regex(
            """^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+(?:\d+\s+)+([VDIWEFA])\s+(.*?)\s*:""",
        )
        val ERROR_LEVELS = setOf('E', 'F', 'A')
    }
}
