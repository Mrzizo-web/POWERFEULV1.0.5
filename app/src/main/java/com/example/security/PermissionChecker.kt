package com.example.security

import com.example.domain.model.UserRole

object PermissionChecker {
    fun hasPermission(role: UserRole, permission: AppPermission): Boolean {
        if (permission == AppPermission.MANAGE_SETTINGS) return role == UserRole.OWNER
        if (permission == AppPermission.MANAGE_EMPLOYEES) return role == UserRole.OWNER || role == UserRole.ADMIN
        return when (role) {
            UserRole.OWNER -> true
            UserRole.ADMIN -> permission == AppPermission.ACCESS_ADMIN
            UserRole.SUPERVISOR -> permission in setOf(
                AppPermission.ACCESS_ADMIN,
                AppPermission.PROCESS_SALE,
                AppPermission.OPERATE_SHIFT,
                AppPermission.VIEW_REPORTS,
                AppPermission.VOID_SALE,
                AppPermission.MANAGE_RECIPES,
                AppPermission.MANAGE_CUSTOMERS,
                AppPermission.MANAGE_DEBTS,
                AppPermission.RECORD_DEBT_PAYMENT,
                AppPermission.RECORD_WASTE,
                AppPermission.MANAGE_EXPENSES,
                AppPermission.MANAGE_WALLETS,
                AppPermission.VIEW_ALL_TRANSFERS,
                AppPermission.REASSIGN_TRANSFER
            )
            UserRole.INVENTORY_MANAGER -> permission in setOf(
                AppPermission.ACCESS_ADMIN,
                AppPermission.MANAGE_PRODUCTS,
                AppPermission.MANAGE_INVENTORY,
                AppPermission.ADJUST_STOCK,
                AppPermission.RECORD_WASTE,
                AppPermission.MANAGE_PURCHASES,
                AppPermission.MANAGE_RECIPES,
                AppPermission.VIEW_COSTS
            )
            UserRole.CASHIER -> permission in setOf(
                AppPermission.PROCESS_SALE,
                AppPermission.OPERATE_SHIFT,
                AppPermission.MANAGE_CUSTOMERS,
                AppPermission.RECORD_DEBT_PAYMENT
            )
        }
    }
}
