package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local session guard. An authenticated session locks after inactivity and also
 * enforces a maximum lifetime between PIN authentications.
 */
class SessionManager(
    timeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val maxSessionMillis: Long = DEFAULT_MAX_SESSION_MILLIS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    var timeoutMillis: Long = timeoutMillis
        private set

    private var lastActivityTime: Long = nowProvider()
    private var sessionStartedAt: Long? = null

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    init {
        require(timeoutMillis > 0L) { "Idle timeout must be positive" }
        require(maxSessionMillis > 0L) { "Maximum session lifetime must be positive" }
    }

    fun setIdleTimeoutMinutes(minutes: Int) {
        require(minutes in 1..MAX_IDLE_TIMEOUT_MINUTES) {
            "Idle timeout must be between 1 and 60 minutes"
        }
        timeoutMillis = minutes * 60_000L
    }

    fun updateActivity() {
        if (!_isLocked.value) lastActivityTime = nowProvider()
    }

    fun checkTimeout(): Boolean {
        if (_isLocked.value) return true
        val now = nowProvider()
        val startedAt = sessionStartedAt ?: now.also { sessionStartedAt = it }
        if (now - lastActivityTime >= timeoutMillis ||
            now - startedAt >= maxSessionMillis
        ) {
            _isLocked.value = true
            return true
        }
        return false
    }

    fun lockManually() {
        _isLocked.value = true
    }

    /** Called only after a successful PIN authentication, including lock-screen reauthentication. */
    fun unlock() {
        val now = nowProvider()
        lastActivityTime = now
        sessionStartedAt = now
        _isLocked.value = false
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 5
        const val MAX_IDLE_TIMEOUT_MINUTES = 60
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = DEFAULT_IDLE_TIMEOUT_MINUTES * 60_000L
        const val DEFAULT_MAX_SESSION_MILLIS = 12 * 60 * 60 * 1000L
    }
}
