package com.example.security

class LockoutPolicy(
    val maxAttempts: Int = 5,
    val lockoutDurationSeconds: Long = 300
) {
    fun shouldLock(failedAttempts: Int): Boolean {
        return failedAttempts >= maxAttempts
    }

    fun calculateLockoutUntil(): Long {
        return System.currentTimeMillis() + (lockoutDurationSeconds * 1000)
    }

    fun isCurrentlyLocked(lockedUntil: Long?): Boolean {
        if (lockedUntil == null) return false
        return System.currentTimeMillis() < lockedUntil
    }

    fun getRemainingSeconds(lockedUntil: Long?): Long {
        if (lockedUntil == null) return 0
        val remaining = (lockedUntil - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining else 0
    }
}
