package com.example

import com.example.security.PasswordHasher
import com.example.security.SessionManager
import org.junit.Assert.*
import org.junit.Test

class SecurityUnitTest {
    @Test
    fun passwordHasherUsesPbkdf2AndRejectsWrongPin() {
        val hasher = PasswordHasher()
        val result = hasher.hash("1234")
        assertTrue(result.hashHex.isNotBlank())
        assertTrue(result.saltHex.isNotBlank())
        assertTrue(hasher.verify("1234", result.saltHex, result.hashHex))
        assertFalse(hasher.verify("1235", result.saltHex, result.hashHex))
    }

    @Test
    fun sessionLocksAfterConfiguredTimeout() {
        val manager = SessionManager(timeoutMillis = 20L)
        assertFalse(manager.isLocked.value)
        Thread.sleep(30L)
        assertTrue(manager.checkTimeout())
        assertTrue(manager.isLocked.value)

        manager.unlock()
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun unlockResetsTimeout() {
        val manager = SessionManager(timeoutMillis = 1000L)
        Thread.sleep(10L)
        manager.updateActivity()
        assertFalse(manager.checkTimeout())
    }
}
