package com.example.security

enum class AppPermission(val titleAr: String) {
    ACCESS_ADMIN("دخول لوحة الإدارة"),
    MANAGE_PRODUCTS("إدارة المنتجات"),
    MANAGE_RECIPES("إدارة الوصفات والخلطات"),
    VIEW_COSTS("عرض التكاليف والأرباح"),
    MANAGE_INVENTORY("إدارة المخزون والتوريد"),
    ADJUST_STOCK("التسويات الجردية"),
    MANAGE_CUSTOMERS("إدارة العملاء"),
    MANAGE_DEBTS("إدارة الديون والتحصيلات"),
    OVERRIDE_CREDIT("تجاوز السقف الائتماني"),
    MANAGE_EMPLOYEES("إدارة الموظفين"),
    VIEW_REPORTS("عرض التقارير والتحليلات"),
    VIEW_AUDIT_LOGS("عرض سجلات التدقيق"),
    VOID_SALE("إلغاء واسترجاع الفواتير"),
    MANAGE_SETTINGS("إدارة إعدادات النظام"),
    MANAGE_WALLETS("إدارة المحافظ والحوالات"),
    VIEW_ALL_TRANSFERS("عرض حوالات جميع الشفتات والأيام"),
    REASSIGN_TRANSFER("إعادة ربط الحوالة وتعديل حالتها"),
    BACKUP_DATA("تصدير واستيراد النسخ الاحتياطية")
}
