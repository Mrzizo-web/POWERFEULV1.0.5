package com.example.security

import com.example.domain.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCheckerUnitTest {
    @Test
    fun ownerRetainsAllCapabilitiesIncludingSettingsAndEmployeeManagement() {
        for (permission in AppPermission.values()) {
            assertTrue("OWNER should allow ${permission.name}", PermissionChecker.hasPermission(UserRole.OWNER, permission))
        }
    }

    @Test
    fun cashierCanSellAndOperateShiftButCannotManageOwnerFunctions() {
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.PROCESS_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.OPERATE_SHIFT))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_CUSTOMERS))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.RECORD_DEBT_PAYMENT))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ACCESS_ADMIN))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.VOID_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ADJUST_STOCK))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_WALLETS))
    }

    @Test
    fun legacyRolesHaveNoPrivilegesAndMustBeNormalizedToTheTwoRoleModel() {
        for (legacyRole in listOf(UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.INVENTORY_MANAGER)) {
            for (permission in AppPermission.values()) {
                assertFalse(
                    "${legacyRole.name} must not retain ${permission.name}",
                    PermissionChecker.hasPermission(legacyRole, permission)
                )
            }
        }
    }
}
