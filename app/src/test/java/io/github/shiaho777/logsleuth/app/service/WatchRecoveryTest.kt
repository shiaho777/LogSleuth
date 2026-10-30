package io.github.shiaho777.logsleuth.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRecoveryTest {

    private val cooldown = WatchRecovery.RETRY_COOLDOWN_MS

    @Test
    fun `never blocked means try now`() {
        assertTrue(WatchRecovery.shouldRetry(lastFailureAt = 0L, now = 1_000L))
    }

    @Test
    fun `a fresh refusal holds the retry back`() {
        val now = 10_000_000L
        assertFalse(WatchRecovery.shouldRetry(now - 1_000L, now))
        assertFalse(WatchRecovery.shouldRetry(now, now))
    }

    @Test
    fun `the retry unlocks exactly at the cooldown`() {
        val now = 10_000_000L
        assertFalse(WatchRecovery.shouldRetry(now - cooldown + 1, now))
        assertTrue(WatchRecovery.shouldRetry(now - cooldown, now))
    }

    @Test
    fun `a clock that jumped backwards does not disable the watch`() {
        // Wall clock moved back past the recorded failure: we cannot reason
        // about the interval, so treat it as elapsed rather than staying stuck.
        assertTrue(WatchRecovery.shouldRetry(lastFailureAt = 5_000L, now = 1_000L))
    }

    @Test
    fun `an explicitly short cooldown is honoured`() {
        val now = 1_000L
        assertTrue(WatchRecovery.shouldRetry(lastFailureAt = 900L, now = now, cooldownMs = 100L))
        assertFalse(WatchRecovery.shouldRetry(lastFailureAt = 901L, now = now, cooldownMs = 100L))
    }
}
