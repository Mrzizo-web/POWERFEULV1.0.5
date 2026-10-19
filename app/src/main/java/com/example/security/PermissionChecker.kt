package com.example.security

import com.example.domain.model.UserRole

/**
 * Central authorization policy. Owner-only administration is enforced by the
 * same checker used by ViewModels and financial engines, not by the UI alone.
 */
object PermissionChecker {
    fun hasPermission(role: UserRole, permission: AppPermission): Boolean {
        if (role == UserRole.OWNER) return true
        if (role != UserRole.CASHIER) return false
        return permission in setOf(
            AppPermission.PROCESS_SALE,
            AppPermission.OPERATE_SHIFT,
            AppPermission.MANAGE_CUSTOMERS,
            AppPermission.RECORD_DEBT_PAYMENT
        )
    }
}
