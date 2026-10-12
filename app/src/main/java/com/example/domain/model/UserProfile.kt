package com.example.domain.model

data class UserProfile(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val phone: String,
    val isLocked: Boolean,
    val remainingLockSeconds: Long
)
