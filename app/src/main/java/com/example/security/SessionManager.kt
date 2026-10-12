package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(
    val timeoutMillis: Long = 5 * 60 * 1000L // 5 minutes
) {
    private var lastActivityTime: Long = System.currentTimeMillis()
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
        if (_isLocked.value) {
            // keep locked until explicit unlock
        }
    }

    fun checkTimeout(): Boolean {
        if (_isLocked.value) return true
        val now = System.currentTimeMillis()
        if (now - lastActivityTime >= timeoutMillis) {
            _isLocked.value = true
            return true
        }
        return false
    }

    fun lockManually() {
        _isLocked.value = true
    }

    fun unlock() {
        lastActivityTime = System.currentTimeMillis()
        _isLocked.value = false
    }
}
