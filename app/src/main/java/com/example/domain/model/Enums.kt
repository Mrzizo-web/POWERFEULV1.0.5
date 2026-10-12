package com.example.domain.model

enum class CustomerStatus(val titleAr: String) {
    ACTIVE("نشط"),
    BLOCKED("محظور"),
    SETTLED("تمت التسوية")
}

enum class PaymentMethod(val titleAr: String) {
    CASH("نقداً"),
    E_WALLET("محفظة إلكترونية"),
    DEBT("آجل / ذمم")
}

enum class InventoryTxType(val titleAr: String) {
    PURCHASE("شراء / توريد"),
    SALE("بيع"),
    WASTE("هدر / تالف"),
    ADJUSTMENT("تسوية جردية"),
    CONSUMPTION("استهلاك تشغيلي"),
    RESTORE("استرجاع")
}

enum class ShiftStatus(val titleAr: String) {
    OPEN("مفتوحة"),
    CLOSED("مغلقة"),
    HANDED_OVER("تم التسليم")
}

enum class WasteReason(val titleAr: String) {
    EXPIRED("منتهي الصلاحية"),
    SPOILED("تالف"),
    DAMAGED("مكسور / متضرر"),
    OTHER("أخرى")
}

enum class UserRole(val titleAr: String) {
    OWNER("المالك"),
    ADMIN("مدير عام"),
    SUPERVISOR("مشرف"),
    CASHIER("محاسب / كاشير"),
    INVENTORY_MANAGER("أمين مخزن")
}

enum class UnitType(val symbolAr: String) {
    KG("كجم"),
    G("جرام"),
    L("لتر"),
    ML("مل"),
    PIECE("حبة")
}

enum class WalletTransferStatus(val titleAr: String) {
    RECEIVED("تم الاستلام"),
    MATCHED("تمت المطابقة"),
    DUPLICATE("مكررة"),
    CONFIRMED("مؤكدة"),
    REJECTED("مرفوضة"),
    OUT_OF_SHIFT("خارج الدوام"),
    NEEDS_REVIEW("تحتاج مراجعة")
}

enum class SmsProcessingStatus(val titleAr: String) {
    PROCESSED("تمت المعالجة"),
    DUPLICATE("مكررة"),
    UNMATCHED("غير مطابقة"),
    ERROR("خطأ تحليل")
}
