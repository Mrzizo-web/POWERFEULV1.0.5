package com.example.data.engine

import com.example.data.local.AppDatabase
import com.example.data.local.entity.PurchaseEntity
import com.example.data.local.entity.PurchaseItemEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.InventoryTxType
import java.util.UUID

class PurchaseEngine(
    private val db: AppDatabase,
    private val inventoryEngine: InventoryEngine
) {
    suspend fun createPurchase(
        supplierId: String,
        supplierName: String,
        invoiceNumber: String,
        items: List<NewPurchaseItem>,
        user: UserEntity,
        notes: String = ""
    ): String {
        val total = items.sumOf { it.totalPrice }
        val purchaseId = UUID.randomUUID().toString()
        val purchase = PurchaseEntity(
            id = purchaseId,
            invoiceNumber = invoiceNumber,
            supplierId = supplierId,
            supplierName = supplierName,
            totalAmount = total,
            notes = notes,
            createdByUserId = user.id,
            createdByUserName = user.name
        )
        db.purchaseDao().insertPurchase(purchase)

        val purchaseItems = items.map { item ->
            PurchaseItemEntity(
                purchaseId = purchaseId,
                rawMaterialId = item.rawMaterialId,
                rawMaterialName = item.rawMaterialName,
                quantity = item.quantity,
                unit = item.unit,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice
            )
        }
        db.purchaseDao().insertPurchaseItems(purchaseItems)

        for (item in items) {
            inventoryEngine.addRawMaterialStock(
                materialId = item.rawMaterialId,
                amount = item.quantity,
                unit = item.unit,
                referenceId = purchaseId,
                userId = user.id,
                userName = user.name,
                txType = InventoryTxType.PURCHASE,
                note = "فاتورة شراء $invoiceNumber"
            )
        }

        return purchaseId
    }
}
