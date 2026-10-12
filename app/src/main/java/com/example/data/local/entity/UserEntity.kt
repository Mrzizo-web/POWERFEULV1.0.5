package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.UserRole
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String,
    val pinHash: String = "",
    val pinSalt: String = "",
    val role: UserRole,
    val phone: String = "",
    val isActive: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
