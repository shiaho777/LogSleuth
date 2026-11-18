package io.github.shiaho777.logsleuth.app.core.crashes

/**
 * A crash-looping process (a system service dying every few seconds) used to
 * post one fresh notification per crash — hundreds a day, each with its own
 * id, none replaceable. The record still lands for every crash; only the
 * *announcement* is throttled: within [windowMs] of the last notification
 * for the same app + crash type, further crashes update a running count and
 * the next notification after the window says how many happened.
 *
 * Pure logic, unit-tested; the recorder owns one instance behind its write
 * mutex, so no locking is needed here.
 */
class CrashNotifyThrottle(
    private val windowMs: Long = 60_000,
) {
    data class Decision(
        /** Post (or update) the notification now. */
        val notify: Boolean,
        /** Crashes of this key since the previous notification, incl. this one. */
        val count: Int,
    )

    private class State(var lastNotifiedAt: Long, var suppressed: Int)

    private val states = HashMap<String, State>()

    /** Call for every crash that would otherwise notify. */
    fun onCrash(key: String, timeMillis: Long): Decision {
        val state = states[key]
        if (state == null) {
            states[key] = State(timeMillis, 0)
            trim(timeMillis)
            return Decision(notify = true, count = 1)
        }
        return if (timeMillis - state.lastNotifiedAt >= windowMs) {
            val count = state.suppressed + 1
            state.lastNotifiedAt = timeMillis
            state.suppressed = 0
            Decision(notify = true, count = count)
        } else {
            state.suppressed++
            Decision(notify = false, count = state.suppressed)
        }
    }

    /** Forget keys long past their window so the map cannot grow forever. */
    private fun trim(now: Long) {
        if (states.size <= MAX_KEYS) return
        val cutoff = now - windowMs * 4
        states.entries.removeAll { it.value.lastNotifiedAt < cutoff }
    }

    private companion object {
        const val MAX_KEYS = 256
    }
}