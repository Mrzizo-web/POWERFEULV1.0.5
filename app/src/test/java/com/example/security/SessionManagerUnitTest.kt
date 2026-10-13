package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerUnitTest {
    @Test
    fun defaultIdleTimeoutIsFiveMinutesAndLocksOnInactivity() {
        var now = 1_000L
        val manager = SessionManager(nowProvider = { now })
        manager.unlock()

        now += 4 * 60_000L
        manager.updateActivity()
        now += 4 * 60_000L
        assertFalse(manager.checkTimeout())

        now += 5 * 60_000L + 1L
        assertTrue(manager.checkTimeout())
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun maximumLifetimeLocksEvenWhenUserKeepsBeingActive() {
        var now = 5_000L
        val manager = SessionManager(nowProvider = { now })
        manager.unlock()

        now += 6 * 60 * 60 * 1000L
        manager.updateActivity()
        assertFalse(manager.checkTimeout())

        now += 6 * 60 * 60 * 1000L
        manager.updateActivity()
        assertTrue(manager.checkTimeout())
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun configuredIdleTimeoutIsUsedAndSuccessfulReauthenticationStartsNewLifetime() {
        var now = 10_000L
        val manager = SessionManager(nowProvider = { now })
        manager.setIdleTimeoutMinutes(10)
        assertEquals(600_000L, manager.timeoutMillis)

        manager.unlock()
        now += SessionManager.DEFAULT_MAX_SESSION_MILLIS
        assertTrue(manager.checkTimeout())

        manager.unlock()
        assertFalse(manager.checkTimeout())
        assertFalse(manager.isLocked.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun idleTimeoutOutsideSupportedRangeIsRejected() {
        SessionManager().setIdleTimeoutMinutes(0)
    }
}
