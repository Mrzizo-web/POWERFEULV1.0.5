package com.example.security

import com.example.domain.model.UserRole

object PermissionChecker {
    fun hasPermission(role: UserRole, permission: AppPermission): Boolean {
        return when (role) {
            UserRole.OWNER -> true
            UserRole.ADMIN -> true
            UserRole.SUPERVISOR -> when (permission) {
                AppPermission.ACCESS_ADMIN,
                AppPermission.VIEW_REPORTS,
                AppPermission.VOID_SALE,
                AppPermission.MANAGE_RECIPES,
                AppPermission.MANAGE_CUSTOMERS,
                AppPermission.MANAGE_DEBTS,
                AppPermission.MANAGE_WALLETS,
                AppPermission.VIEW_ALL_TRANSFERS,
                AppPermission.REASSIGN_TRANSFER -> true
                else -> false
            }
            UserRole.INVENTORY_MANAGER -> when (permission) {
                AppPermission.ACCESS_ADMIN,
                AppPermission.MANAGE_INVENTORY,
                AppPermission.ADJUST_STOCK,
                AppPermission.MANAGE_RECIPES,
                AppPermission.VIEW_COSTS -> true
                else -> false
            }
            UserRole.CASHIER -> when (permission) {
                AppPermission.MANAGE_CUSTOMERS -> true
                else -> false
            }
        }
    }
}
