package com.example.ui.admin

import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker

enum class AdminTab(val titleAr: String) {
    DASHBOARD("لوحة التحكم"),
    PRODUCTS("المنتجات"),
    RECIPES("الوصفات والخلطات"),
    INVENTORY("المخزون"),
    SALES("المبيعات"),
    PURCHASES("المشتريات"),
    DEBTS("العملاء والديون"),
    EXPENSES("المصروفات"),
    SHIFTS("الورديات"),
    WALLETS("الحوالات والمحافظ"),
    AI_ASSISTANT("الذكاء والبحث"),
    BACKUP("النسخ الاحتياطي والتصدير"),
    EMPLOYEES("الموظفين"),
    REPORTS("التقارير"),
    AUDIT_LOG("سجل التدقيق"),
    SETTINGS("الإعدادات");

    fun requiredPermission(): AppPermission = when (this) {
        DASHBOARD -> AppPermission.VIEW_REPORTS
        PRODUCTS -> AppPermission.MANAGE_PRODUCTS
        RECIPES -> AppPermission.MANAGE_RECIPES
        INVENTORY -> AppPermission.MANAGE_INVENTORY
        SALES -> AppPermission.VOID_SALE
        PURCHASES -> AppPermission.MANAGE_PURCHASES
        DEBTS -> AppPermission.MANAGE_DEBTS
        EXPENSES -> AppPermission.MANAGE_EXPENSES
        SHIFTS -> AppPermission.OPERATE_SHIFT
        WALLETS -> AppPermission.MANAGE_WALLETS
        AI_ASSISTANT -> AppPermission.VIEW_REPORTS
        BACKUP -> AppPermission.BACKUP_DATA
        EMPLOYEES -> AppPermission.MANAGE_EMPLOYEES
        REPORTS -> AppPermission.VIEW_REPORTS
        AUDIT_LOG -> AppPermission.VIEW_AUDIT_LOGS
        SETTINGS -> AppPermission.MANAGE_SETTINGS
    }
}

fun visibleAdminTabs(role: UserRole): List<AdminTab> {
    if (!PermissionChecker.hasPermission(role, AppPermission.ACCESS_ADMIN)) return emptyList()
    return AdminTab.values().filter { tab ->
        PermissionChecker.hasPermission(role, tab.requiredPermission())
    }
}
