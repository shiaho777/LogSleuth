package io.github.shiaho777.logsleuth.app.service

/**
 * When a background start is refused, remember it and stay quiet for a while
 * instead of retrying on every process creation. The refusal is usually the
 * system saying "not from the background, not now" — hammering it again from
 * the same background process cannot succeed.
 */
internal object WatchRecovery {

    /** Long enough that a real foreground launch is the next attempt. */
    const val RETRY_COOLDOWN_MS = 15 * 60 * 1000L

    /**
     * [lastFailureAt] is wall-clock millis, 0 when nothing ever failed.
     * A clock that moved backwards is treated as "try again": a wrong clock
     * must not disable the feature.
     */
    fun shouldRetry(
        lastFailureAt: Long,
        now: Long,
        cooldownMs: Long = RETRY_COOLDOWN_MS,
    ): Boolean {
        if (lastFailureAt <= 0L) return true
        if (now < lastFailureAt) return true
        return now - lastFailureAt >= cooldownMs
    }
}
