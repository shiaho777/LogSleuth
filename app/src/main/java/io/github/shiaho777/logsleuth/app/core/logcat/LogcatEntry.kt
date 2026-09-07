package io.github.shiaho777.logsleuth.app.core.logcat

/** Log priority as printed by `logcat -v threadtime`. */
enum class LogLevel(val letter: Char, val priority: Int) {
    V('V', 2),
    D('D', 3),
    I('I', 4),
    W('W', 5),
    E('E', 6),
    F('F', 7),
    ;

    companion object {
        fun from(letter: Char): LogLevel = entries.firstOrNull { it.letter == letter } ?: V
    }
}

/**
 * One parsed logcat line (possibly merged from multiple raw lines when the
 * message itself contains newlines).
 *
 * @param uid only present when logcat runs with `-v uid` (shell/Shizuku).
 * @param raw the original raw text, used for byte-faithful recording.
 */
data class LogcatEntry(
    val timestampMillis: Long,
    val pid: Int,
    val tid: Int,
    val uid: Int?,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String,
) {
    val displayTime: String
        get() {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
            return "%02d-%02d %02d:%02d:%02d.%03d".format(
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND),
                cal.get(java.util.Calendar.MILLISECOND),
            )
        }
}
