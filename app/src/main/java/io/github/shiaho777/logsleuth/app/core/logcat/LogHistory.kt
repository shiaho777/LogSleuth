package io.github.shiaho777.logsleuth.app.core.logcat

internal class LogHistory {
    private data class Cursor(val sequence: Long, val stamp: String)

    private val entries = ArrayDeque<LogcatEntry>()
    private val cursors = HashMap<String?, Cursor>()

    @Synchronized
    fun append(entry: LogcatEntry, capacity: Int): LogcatEntry {
        val origin = entry.remoteEndpoint
        val seq = (cursors[origin]?.sequence ?: 0L) + 1
        val stamped = entry.copy(seq = seq)
        entries.addLast(stamped)
        while (entries.size > capacity) entries.removeFirst()
        cursors[origin] = Cursor(seq, UidNames.formatSince(entry.timestampMillis))
        return stamped
    }

    @Synchronized
    fun stamp(remoteEndpoint: String?): String? = cursors[remoteEndpoint]?.stamp

    @Synchronized
    fun snapshot(remoteEndpoint: String?): LogcatEngine.Snapshot =
        LogcatEngine.Snapshot(
            entries.filter { it.remoteEndpoint == remoteEndpoint },
            cursors[remoteEndpoint]?.sequence ?: 0L,
        )

    @Synchronized
    fun watermark(remoteEndpoint: String?): Long = cursors[remoteEndpoint]?.sequence ?: 0L

    @Synchronized
    fun retained(remoteEndpoint: String?, after: Long, through: Long): List<LogcatEntry> =
        entries.filter { it.remoteEndpoint == remoteEndpoint && it.seq > after && it.seq <= through }

    @Synchronized
    fun clear(remoteEndpoint: String?) {
        entries.removeAll { it.remoteEndpoint == remoteEndpoint }
    }
}
