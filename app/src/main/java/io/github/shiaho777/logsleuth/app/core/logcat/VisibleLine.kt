package io.github.shiaho777.logsleuth.app.core.logcat

/**
 * Columns the user currently sees. Copy and export of a selection use this
 * so the shared text matches the on-screen row, not the raw logcat line.
 */
data class VisibleColumns(
    val showDate: Boolean = false,
    val showTime: Boolean = true,
    val showUid: Boolean = false,
    val showPid: Boolean = true,
    val showTid: Boolean = true,
    val showPackage: Boolean = true,
    val showTag: Boolean = true,
    val datePattern: String = DateTimePatterns.DATE_PRESET,
    val timePattern: String = DateTimePatterns.TIME_PRESET,
    /** `"time"` | `"datetime"` | `"epoch"` — same values the live row uses. */
    val timeFormat: String = "time",
)

/**
 * Time cell the live row draws: custom pattern, full threadtime stamp, or
 * epoch millis. Copy uses this so clipboard text matches the screen.
 */
fun formatLogTime(
    entry: LogcatEntry,
    timeFormat: String,
    timePattern: String,
): String = when (timeFormat) {
    "datetime" -> entry.displayTime
    "epoch" -> entry.timestampMillis.toString()
    else -> DateTimePatterns.format(
        entry.timestampMillis,
        timePattern,
        DateTimePatterns.TIME_PRESET,
    )
}

/** Formats one entry the way the live row draws it. */
fun formatVisible(
    entry: LogcatEntry,
    columns: VisibleColumns,
    appLabel: String? = null,
): String = buildString {
    if (columns.showDate) {
        append(
            DateTimePatterns.format(
                entry.timestampMillis,
                columns.datePattern,
                DateTimePatterns.DATE_PRESET,
            ),
        )
        append(' ')
    }
    if (columns.showTime) {
        append(formatLogTime(entry, columns.timeFormat, columns.timePattern))
        append(' ')
    }
    if (columns.showUid) {
        val uid = entry.uid
        append(
            when {
                uid == null -> "-"
                else -> UidNames.wellKnownName(uid) ?: uid.toString()
            },
        )
        append(' ')
    }
    if (columns.showPid && columns.showTid) {
        append(entry.pid)
        append('-')
        append(entry.tid)
        append(' ')
    } else if (columns.showPid) {
        append(entry.pid)
        append(' ')
    } else if (columns.showTid) {
        append(entry.tid)
        append(' ')
    }
    if (columns.showPackage && !appLabel.isNullOrBlank()) {
        append(appLabel)
        append(' ')
    }
    append(entry.level.letter)
    append(' ')
    if (columns.showTag && entry.tag.isNotEmpty()) {
        append(entry.tag)
        append(": ")
    }
    append(entry.message)
}
