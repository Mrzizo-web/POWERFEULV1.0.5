package com.example

import com.example.security.PasswordHasher
import com.example.security.LockoutPolicy
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testPasswordHasher() {
        val hasher = PasswordHasher.DEFAULT
        val result = hasher.hash("775152713")
        assertTrue(hasher.verify("775152713", result.saltHex, result.hashHex))
        assertFalse(hasher.verify("wrong_pin", result.saltHex, result.hashHex))
    }

    @Test
    fun testLockoutPolicy() {
        val policy = LockoutPolicy(maxAttempts = 5, lockoutDurationSeconds = 300)
        assertFalse(policy.shouldLock(4))
        assertTrue(policy.shouldLock(5))
        assertTrue(policy.shouldLock(6))
    }
}
