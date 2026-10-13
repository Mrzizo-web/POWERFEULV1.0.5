package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.PurchaseEntity
import com.example.data.local.entity.PurchaseItemEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.InventoryTxType
import java.util.UUID

class PurchaseEngine(
    private val db: AppDatabase,
    private val inventoryEngine: InventoryEngine
) {
    private data class PreparedPurchaseItem(
        val source: NewPurchaseItem,
        val materialName: String,
        val quantityInBaseUnit: Double,
        val pricePerBaseUnit: Double,
        val totalPrice: Double
    )

    /**
     * Saves invoice, all invoice items, stock movements, updated average costs,
     * and audit event in the same Room transaction. No write occurs until all
     * input lines have been validated.
     */
    suspend fun createPurchase(
        supplierId: String,
        supplierName: String,
        invoiceNumber: String,
        items: List<NewPurchaseItem>,
        user: UserEntity,
        notes: String = ""
    ): String = db.withTransaction {
        require(supplierId.isNotBlank()) { "يجب اختيار المورد" }
        require(supplierName.isNotBlank()) { "اسم المورد مطلوب" }
        require(invoiceNumber.isNotBlank()) { "رقم فاتورة المورد مطلوب" }
        require(items.isNotEmpty()) { "يجب إضافة مادة خام واحدة على الأقل" }

        val supplier = db.supplierDao().getSupplierById(supplierId)
            ?: throw IllegalArgumentException("المورد المختار غير موجود")
        require(supplier.isActive) { "المورد غير نشط" }
        require(supplier.name == supplierName || supplier.name.isNotBlank()) {
            "بيانات المورد غير صالحة"
        }
        val duplicate = db.purchaseDao().getPurchaseBySupplierInvoice(supplierId, invoiceNumber.trim())
        require(duplicate == null) { "فاتورة شراء بهذا الرقم مسجلة مسبقًا لهذا المورد" }

        val prepared = items.map { item ->
            require(item.rawMaterialId.isNotBlank()) { "يجب اختيار مادة خام لكل عنصر" }
            require(item.quantity.isFinite() && item.quantity > 0.0) {
                "كمية الشراء يجب أن تكون أكبر من صفر"
            }
            require(item.unit.isNotBlank()) { "وحدة قياس الشراء مطلوبة" }
            require(item.unitPrice.isFinite() && item.unitPrice >= 0.0) {
                "سعر الوحدة غير صالح"
            }
            val lineTotal = item.totalPrice
            require(lineTotal.isFinite() && lineTotal >= 0.0) { "إجمالي سطر الشراء غير صالح" }

            val material = db.rawMaterialDao().getRawMaterialById(item.rawMaterialId)
                ?: throw IllegalArgumentException("المادة الخام غير موجودة: " + item.rawMaterialId)
            require(material.currentStock.isFinite() && material.currentStock >= 0.0) {
                "رصيد المخزون الحالي غير صالح للمادة " + material.name
            }
            val baseQuantity = UnitConverter.convert(item.quantity, item.unit, material.baseUnit)
            require(baseQuantity.isFinite() && baseQuantity > 0.0) {
                "تعذر تحويل كمية الشراء إلى وحدة المخزون للمادة " + material.name
            }
            val baseUnitPrice = lineTotal / baseQuantity
            require(baseUnitPrice.isFinite() && baseUnitPrice >= 0.0) {
                "تكلفة وحدة المخزون غير صالحة للمادة " + material.name
            }
            PreparedPurchaseItem(
                source = item,
                materialName = material.name,
                quantityInBaseUnit = baseQuantity,
                pricePerBaseUnit = baseUnitPrice,
                totalPrice = lineTotal
            )
        }

        val total = prepared.sumOf { it.totalPrice }
        require(total.isFinite() && total >= 0.0) { "إجمالي فاتورة الشراء غير صالح" }

        val purchaseId = UUID.randomUUID().toString()
        val cleanedInvoiceNumber = invoiceNumber.trim()
        val purchase = PurchaseEntity(
            id = purchaseId,
            invoiceNumber = cleanedInvoiceNumber,
            supplierId = supplierId,
            supplierName = supplier.name,
            totalAmount = total,
            notes = notes,
            createdByUserId = user.id,
            createdByUserName = user.name
        )
        db.purchaseDao().insertPurchase(purchase)

        val purchaseItems = prepared.map { line ->
            PurchaseItemEntity(
                purchaseId = purchaseId,
                rawMaterialId = line.source.rawMaterialId,
                rawMaterialName = line.materialName,
                quantity = line.source.quantity,
                unit = line.source.unit,
                unitPrice = line.source.unitPrice,
                totalPrice = line.totalPrice
            )
        }
        db.purchaseDao().insertPurchaseItems(purchaseItems)

        for (line in prepared) {
            val before = db.rawMaterialDao().getRawMaterialById(line.source.rawMaterialId)
                ?: throw IllegalArgumentException("المادة الخام لم تعد موجودة: " + line.source.rawMaterialId)
            val existingUnitCost = when {
                before.avgCostPerUnit.isFinite() && before.avgCostPerUnit > 0.0 -> before.avgCostPerUnit
                before.lastPurchasePrice.isFinite() && before.lastPurchasePrice > 0.0 -> before.lastPurchasePrice
                else -> 0.0
            }

            inventoryEngine.addRawMaterialStock(
                materialId = line.source.rawMaterialId,
                amount = line.source.quantity,
                unit = line.source.unit,
                referenceId = purchaseId,
                userId = user.id,
                userName = user.name,
                txType = InventoryTxType.PURCHASE,
                note = "فاتورة شراء " + cleanedInvoiceNumber
            )

            val updatedStock = db.rawMaterialDao().getRawMaterialById(line.source.rawMaterialId)
                ?: throw IllegalStateException("تعذر إعادة قراءة المخزون بعد الشراء")
            val denominator = before.currentStock + line.quantityInBaseUnit
            val weightedCost = if (denominator > 0.0) {
                ((before.currentStock * existingUnitCost) +
                    (line.quantityInBaseUnit * line.pricePerBaseUnit)) / denominator
            } else {
                line.pricePerBaseUnit
            }
            require(weightedCost.isFinite() && weightedCost >= 0.0) {
                "تعذر حساب متوسط التكلفة للمادة " + line.materialName
            }
            db.rawMaterialDao().updateRawMaterial(
                updatedStock.copy(
                    avgCostPerUnit = weightedCost,
                    lastPurchasePrice = line.pricePerBaseUnit,
                    supplierId = supplierId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // Update current recipe/product costs from the weighted-average material costs.
        // This remains in the same Room transaction as the purchase and stock movements.
        CostEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao(), db.productDao())
            .refreshActiveRecipeAndProductCosts()

        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "PURCHASE_CREATED",
                entityType = "PURCHASE",
                entityId = purchaseId,
                newValue = "Invoice " + cleanedInvoiceNumber + "; total " + total,
                notes = "تم تسجيل فاتورة شراء من " + supplier.name +
                    " بعدد " + prepared.size + " أسطر؛ تم تحديث المخزون ومتوسط التكلفة ذريًا"
            )
        )
        purchaseId
    }
}
