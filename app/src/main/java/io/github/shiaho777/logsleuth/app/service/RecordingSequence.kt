package io.github.shiaho777.logsleuth.app.service

import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

internal class RecordingSequence(
    start: Long,
    private val retained: (after: Long, through: Long) -> List<LogcatEntry>,
    private val write: (LogcatEntry) -> Boolean,
    private val gap: (Long) -> Boolean,
) {
    private var through = start

    fun accept(entry: LogcatEntry) {
        if (entry.seq <= through) return
        if (recoverThrough(entry.seq - 1) && write(entry)) through = entry.seq
    }

    fun recoverThrough(target: Long): Boolean {
        if (target <= through) return true
        for (entry in retained(through, target)) {
            if (!skipGap(entry.seq - 1) || !write(entry)) return false
            through = entry.seq
        }
        return skipGap(target)
    }

    fun discardThrough(target: Long) {
        through = maxOf(through, target)
    }

    private fun skipGap(target: Long): Boolean {
        if (target <= through) return true
        if (!gap(target - through)) return false
        through = target
        return true
    }
}
