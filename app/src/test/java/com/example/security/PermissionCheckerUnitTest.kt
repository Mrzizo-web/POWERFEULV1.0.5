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
    fun managerCanManageEmployeesButCannotChangeSettingsOrBusinessData() {
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.ACCESS_ADMIN))
        assertTrue(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.PROCESS_SALE))
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.MANAGE_INVENTORY))
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.VIEW_REPORTS))
        assertFalse(PermissionChecker.hasPermission(UserRole.ADMIN, AppPermission.REASSIGN_TRANSFER))
    }

    @Test
    fun cashierCanSellAndOperateShiftButCannotManageInventoryOrWallets() {
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.PROCESS_SALE))
        assertTrue(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.OPERATE_SHIFT))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_SETTINGS))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.ADJUST_STOCK))
        assertFalse(PermissionChecker.hasPermission(UserRole.CASHIER, AppPermission.MANAGE_WALLETS))
    }

    @Test
    fun inventoryManagerCanAdjustStockButCannotManageEmployeesOrSettings() {
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.ADJUST_STOCK))
        assertTrue(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_RECIPES))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_EMPLOYEES))
        assertFalse(PermissionChecker.hasPermission(UserRole.INVENTORY_MANAGER, AppPermission.MANAGE_SETTINGS))
    }
}
