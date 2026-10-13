package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.StockAdjustmentEntity
import com.example.data.local.entity.UserEntity
import com.example.data.local.entity.WasteTransactionEntity
import com.example.domain.model.InventoryTxType
import com.example.domain.model.WasteReason
import com.example.security.AppPermission
import com.example.security.PermissionChecker

/** Atomic stock maintenance with a ledger entry for every real stock change. */
class InventoryAdjustmentEngine(private val db: AppDatabase) {
    private fun inventoryEngine() = InventoryEngine(
        db.rawMaterialDao(), db.recipeDao(), db.mixtureDao(),
        db.inventoryTransactionDao(), db.productDao()
    )

    suspend fun addRawMaterial(
        name: String,
        sku: String,
        baseUnit: String,
        initialStock: Double,
        minStock: Double,
        unitCost: Double,
        user: UserEntity
    ): RawMaterialEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_INVENTORY)) {
            "ليس لديك صلاحية إدارة المخزون"
        }
        require(name.isNotBlank()) { "اسم المادة الخام مطلوب" }
        require(baseUnit.isNotBlank()) { "وحدة قياس المادة الخام مطلوبة" }
        require(initialStock.isFinite() && initialStock >= 0.0) { "الرصيد الافتتاحي غير صالح" }
        require(minStock.isFinite() && minStock >= 0.0) { "حد التنبيه غير صالح" }
        require(unitCost.isFinite() && unitCost >= 0.0) { "تكلفة الوحدة غير صالحة" }
        UnitConverter.convert(0.0, baseUnit, baseUnit)

        val material = RawMaterialEntity(
            name = name.trim(),
            sku = sku.trim(),
            baseUnit = baseUnit.trim(),
            currentStock = 0.0,
            minStock = minStock,
            avgCostPerUnit = unitCost,
            lastPurchasePrice = unitCost
        )
        db.rawMaterialDao().insertRawMaterial(material)
        if (initialStock > 0.0) {
            inventoryEngine().addRawMaterialStock(
                materialId = material.id,
                amount = initialStock,
                unit = material.baseUnit,
                referenceId = "OPENING_STOCK:${material.id}",
                userId = user.id,
                userName = user.name,
                txType = InventoryTxType.ADJUSTMENT,
                note = "رصيد افتتاحي عند إنشاء المادة الخام"
            )
        }
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id, userName = user.name, userRole = user.role.name,
                action = "RAW_MATERIAL_CREATED", entityType = "RAW_MATERIAL", entityId = material.id,
                newValue = "الرصيد ${initialStock} ${material.baseUnit}",
                notes = "تم إنشاء المادة الخام ${material.name} مع تسجيل الرصيد الافتتاحي"
            )
        )
        db.rawMaterialDao().getRawMaterialById(material.id)!!
    }

    suspend fun addStock(
        materialId: String,
        amount: Double,
        user: UserEntity,
        notes: String = ""
    ) = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_INVENTORY)) {
            "ليس لديك صلاحية إضافة المخزون"
        }
        require(amount.isFinite() && amount > 0.0) { "كمية الإضافة يجب أن تكون رقمًا موجبًا وصالحًا" }
        val material = db.rawMaterialDao().getRawMaterialById(materialId)
            ?: throw IllegalArgumentException("المادة الخام غير موجودة")
        inventoryEngine().addRawMaterialStock(
            materialId = materialId,
            amount = amount,
            unit = material.baseUnit,
            referenceId = "MANUAL_STOCK:${java.util.UUID.randomUUID()}",
            userId = user.id,
            userName = user.name,
            txType = InventoryTxType.ADJUSTMENT,
            note = notes.ifBlank { "إضافة مخزون يدوية" }
        )
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id, userName = user.name, userRole = user.role.name,
                action = "RAW_STOCK_ADDED", entityType = "RAW_MATERIAL", entityId = materialId,
                newValue = "+$amount ${material.baseUnit}", notes = notes
            )
        )
    }

    suspend fun recordWaste(
        materialId: String,
        quantity: Double,
        unit: String,
        reason: WasteReason,
        notes: String,
        user: UserEntity
    ): WasteTransactionEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.RECORD_WASTE)) {
            "ليس لديك صلاحية تسجيل الهدر"
        }
        require(quantity.isFinite() && quantity > 0.0) { "كمية الهدر يجب أن تكون رقمًا موجبًا وصالحًا" }
        require(unit.isNotBlank()) { "وحدة قياس الهدر مطلوبة" }
        val material = db.rawMaterialDao().getRawMaterialById(materialId)
            ?: throw IllegalArgumentException("المادة الخام غير موجودة")
        val converted = UnitConverter.convert(quantity, unit, material.baseUnit)
        val estimatedCost = converted * material.avgCostPerUnit
        require(estimatedCost.isFinite() && estimatedCost >= 0.0) { "تكلفة الهدر غير صالحة" }

        val before = material.currentStock
        inventoryEngine().deductRawMaterial(
            materialId = materialId,
            amount = quantity,
            unit = unit,
            referenceId = "WASTE:${java.util.UUID.randomUUID()}",
            userId = user.id,
            userName = user.name,
            txType = InventoryTxType.WASTE,
            note = "هدر: ${reason.name}; $notes"
        )
        val waste = WasteTransactionEntity(
            rawMaterialId = material.id,
            rawMaterialName = material.name,
            quantity = quantity,
            unit = unit,
            estimatedCost = estimatedCost,
            reason = reason,
            notes = notes.trim(),
            userId = user.id,
            userName = user.name
        )
        db.wasteDao().insertWaste(waste)
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id, userName = user.name, userRole = user.role.name,
                action = "RAW_WASTE_RECORDED", entityType = "RAW_MATERIAL", entityId = materialId,
                previousValue = before.toString(),
                newValue = (before - converted).coerceAtLeast(0.0).toString(),
                notes = "هدر ${quantity} ${unit} من ${material.name}: ${reason.name}"
            )
        )
        waste
    }

    suspend fun applyStockAdjustment(
        materialId: String,
        actualStock: Double,
        reason: String,
        user: UserEntity
    ): StockAdjustmentEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.ADJUST_STOCK)) {
            "ليس لديك صلاحية إجراء التسويات الجردية"
        }
        require(actualStock.isFinite() && actualStock >= 0.0) { "الرصيد الفعلي يجب أن يكون غير سالب وصالحًا" }
        require(reason.isNotBlank()) { "سبب التسوية مطلوب" }
        val material = db.rawMaterialDao().getRawMaterialById(materialId)
            ?: throw IllegalArgumentException("المادة الخام غير موجودة")
        require(material.currentStock.isFinite() && material.currentStock >= 0.0) {
            "الرصيد الحالي للمادة الخام غير صالح"
        }
        val difference = actualStock - material.currentStock
        when {
            difference > EPSILON -> inventoryEngine().addRawMaterialStock(
                materialId, difference, material.baseUnit, "ADJUST:${java.util.UUID.randomUUID()}",
                user.id, user.name, InventoryTxType.ADJUSTMENT, "تسوية جردية: $reason"
            )
            difference < -EPSILON -> inventoryEngine().deductRawMaterial(
                materialId, -difference, material.baseUnit, "ADJUST:${java.util.UUID.randomUUID()}",
                user.id, user.name, InventoryTxType.ADJUSTMENT, "تسوية جردية: $reason"
            )
        }
        val adjustment = StockAdjustmentEntity(
            rawMaterialId = materialId,
            rawMaterialName = material.name,
            systemStock = material.currentStock,
            actualStock = actualStock,
            discrepancy = difference,
            unit = material.baseUnit,
            reason = reason.trim(),
            userId = user.id,
            userName = user.name
        )
        db.stockAdjustmentDao().insertStockAdjustment(adjustment)
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id, userName = user.name, userRole = user.role.name,
                action = "STOCK_ADJUSTED", entityType = "RAW_MATERIAL", entityId = materialId,
                previousValue = material.currentStock.toString(), newValue = actualStock.toString(),
                notes = "تسوية جردية ${material.name}: $reason"
            )
        )
        adjustment
    }

    private companion object { const val EPSILON = 1e-9 }
}
