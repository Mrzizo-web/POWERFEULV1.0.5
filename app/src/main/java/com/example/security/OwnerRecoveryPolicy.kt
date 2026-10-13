package com.example.security

import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole

/**
 * Allows recovery only for the known local owner, when no account is active and a
 * salted PIN already exists. The policy never activates an account by itself.
 */
object OwnerRecoveryPolicy {
    const val OWNER_ID = "user-owner-ziad"

    fun isEligible(user: UserEntity?, activeUserCount: Int): Boolean {
        if (user == null || user.isActive || activeUserCount != 0) return false
        return user.id == OWNER_ID &&
            user.role == UserRole.OWNER &&
            user.pinHash.isNotBlank() &&
            user.pinSalt.isNotBlank()
    }
}
