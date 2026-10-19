package com.example

import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoRoleAuthorizationTest {
    @Test
    fun ownerCanAdministerAndCashierCannotAccessOwnerFunctions() {
        assertTrue(PermissionChecker.hasPermission(UserRole.OWNER, AppPermission.ACCESS_ADMIN))
        assertTrue(PermissionChecker.hasPermission(UserRole.OWNER, AppPermission.MANAGE_EMPLOYEES))
        assertTrue(PermissionChecker.hasPermission(UserRole.OWNER, AppPermission.VOID_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.PROCESS_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.OPERATE_SHIFT))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ACCESS_ADMIN))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.VOID_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_INVENTORY))
    }

    @Test
    fun deprecatedRolesCannotReceivePrivilegesBeforeNormalization() {
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.ACCESS_ADMIN))
        assertFalse(PermissionChecker.hasPermission(UserRole.SUPERVISOR, AppPermission.PROCESS_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_INVENTORY))
    }
}
