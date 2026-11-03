package io.github.shiaho777.logsleuth.sdk.internal

import java.time.LocalDate

/**
 * Lets a reconnecting `logcat` pick up where the previous one stopped.
 *
 * A fresh `logcat` first prints everything still buffered for the pid, so a
 * plain reconnect would write the whole buffer to the log file a second time.
 * The cursor remembers the newest header timestamp it let through and the
 * lines that carried it. The next connection starts at that timestamp with
 * `-T`, which is inclusive, so the lines it replays at that exact millisecond
 * are dropped once each.
 */
internal class LogcatResumeCursor {

    private var lastStamp: String? = null
    private val linesAtLastStamp = ArrayList<String>()
    private var replay: MutableList<String>? = null

    fun command(pid: Int, today: LocalDate = LocalDate.now()): List<String> {
        val base = listOf("logcat", "-v", "threadtime", "--pid=$pid")
        val stamp = lastStamp ?: return base
        return base + listOf("-T", withYear(stamp, today))
    }

    /** Call once per logcat process, before reading its output. */
    fun beginConnection() {
        replay = if (lastStamp != null) ArrayList(linesAtLastStamp) else null
    }

    /** False for a line an earlier connection already delivered. */
    fun isNew(line: String): Boolean {
        val stamp = stampOf(line)
        val pending = replay
        if (pending != null) {
            // Until the first new entry, header-less lines are logcat's own
            // "--------- beginning of" banners being printed again.
            if (stamp == null) return false
            if (stamp == lastStamp && pending.remove(line)) return false
            replay = null
        }
        if (stamp != null) {
            if (stamp != lastStamp) {
                lastStamp = stamp
                linesAtLastStamp.clear()
            }
            linesAtLastStamp.add(line)
        }
        return true
    }

    private companion object {
        val STAMP = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s""")

        fun stampOf(line: String): String? = STAMP.find(line)?.groupValues?.get(1)

        // threadtime has no year, and logcat reads a year-less -T as the
        // current year: a stamp from Dec 31 replayed on Jan 1 would point a
        // year ahead and capture nothing. A month-day later than today can
        // only belong to last year.
        fun withYear(stamp: String, today: LocalDate): String {
            val month = stamp.substring(0, 2).toInt()
            val day = stamp.substring(3, 5).toInt()
            val future = month > today.monthValue ||
                (month == today.monthValue && day > today.dayOfMonth)
            val year = if (future) today.year - 1 else today.year
            return "$year-$stamp"
        }
    }
}
