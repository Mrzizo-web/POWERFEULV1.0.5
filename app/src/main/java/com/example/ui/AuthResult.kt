package com.example.ui

import com.example.data.local.entity.UserEntity

sealed class AuthResult {
    data class Success(val user: UserEntity) : AuthResult()
    data class Failure(val message: String, val attemptsRemaining: Int? = null) : AuthResult()
    data class AccountLocked(val remainingSeconds: Long) : AuthResult()
}
