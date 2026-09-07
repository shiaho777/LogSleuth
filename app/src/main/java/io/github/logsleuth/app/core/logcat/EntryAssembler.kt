package io.github.logsleuth.app.core.logcat

/**
 * Merges raw logcat lines into entries, gluing continuation lines onto the
 * pending entry's message. An entry is emitted when the *next* entry starts
 * or when [flush] is called (stream idle/end).
 *
 * Pure and synchronous: easy to unit-test.
 */
class EntryAssembler(private val nowMillis: () -> Long = { System.currentTimeMillis() }) {

    private var pending: LogcatEntry? = null

    /** @return entries that became complete because of this line (0 or 1). */
    fun onLine(line: String): List<LogcatEntry> {
        return when (val parsed = LogcatParser.parse(line, nowMillis())) {
            is ParsedLine.Entry -> {
                val out = pending
                pending = parsed.entry
                if (out != null) listOf(out) else emptyList()
            }

            is ParsedLine.Continuation -> {
                val p = pending
                if (p == null || parsed.text.isBlank()) {
                    emptyList()
                } else {
                    pending = p.copy(
                        message = p.message + "\n" + parsed.text,
                        raw = p.raw + "\n" + parsed.text,
                    )
                    emptyList()
                }
            }
        }
    }

    /** Emits the pending entry if any (stream ended or idle timeout). */
    fun flush(): LogcatEntry? {
        val out = pending
        pending = null
        return out
    }
}
