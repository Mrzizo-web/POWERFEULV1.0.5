package com.example.data.engine

import com.example.data.local.dao.InventoryTransactionDao
import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.InventoryTransactionEntity
import com.example.domain.model.InventoryTxType

class InventoryEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val recipeDao: RecipeDao,
    private val mixtureDao: MixtureDao,
    private val inventoryTransactionDao: InventoryTransactionDao
) {
    suspend fun consumeForProduct(
        productId: String,
        quantity: Int,
        saleId: String,
        userId: String,
        userName: String
    ) {
        val recipe = recipeDao.getActiveRecipeForProductSync(productId) ?: return
        val items = recipeDao.getRecipeItemsSync(recipe.id)

        for (item in items) {
            val itemAmount = if (item.amount > 0) item.amount else item.quantity
            val totalNeeded = itemAmount * quantity

            if (item.mixtureId.isNotEmpty()) {
                val mixtureItems = mixtureDao.getMixtureItemsSync(item.mixtureId)
                val mixture = mixtureDao.getMixtureById(item.mixtureId)
                val mixOut = if (mixture != null && mixture.outputQuantity > 0) mixture.outputQuantity else 1.0
                val ratio = totalNeeded / mixOut
                for (mItem in mixtureItems) {
                    val matNeed = mItem.quantity * ratio
                    deductRawMaterial(
                        materialId = mItem.rawMaterialId,
                        amount = matNeed,
                        unit = mItem.unit,
                        referenceId = saleId,
                        userId = userId,
                        userName = userName,
                        txType = InventoryTxType.CONSUMPTION,
                        note = "استهلاك خلطة لبيع فاتورة $saleId"
                    )
                }
            } else if (item.rawMaterialId.isNotEmpty()) {
                deductRawMaterial(
                    materialId = item.rawMaterialId,
                    amount = totalNeeded,
                    unit = item.unit,
                    referenceId = saleId,
                    userId = userId,
                    userName = userName,
                    txType = InventoryTxType.CONSUMPTION,
                    note = "استهلاك بيع فاتورة $saleId"
                )
            }
        }
    }

    suspend fun restoreForProduct(
        productId: String,
        quantity: Int,
        saleId: String,
        userId: String,
        userName: String
    ) {
        val recipe = recipeDao.getActiveRecipeForProductSync(productId) ?: return
        val items = recipeDao.getRecipeItemsSync(recipe.id)

        for (item in items) {
            val itemAmount = if (item.amount > 0) item.amount else item.quantity
            val totalToRestore = itemAmount * quantity
            if (item.mixtureId.isNotEmpty()) {
                val mixtureItems = mixtureDao.getMixtureItemsSync(item.mixtureId)
                val mixture = mixtureDao.getMixtureById(item.mixtureId)
                val mixOut = if (mixture != null && mixture.outputQuantity > 0) mixture.outputQuantity else 1.0
                val ratio = totalToRestore / mixOut
                for (mItem in mixtureItems) {
                    addRawMaterialStock(
                        materialId = mItem.rawMaterialId,
                        amount = mItem.quantity * ratio,
                        unit = mItem.unit,
                        referenceId = saleId,
                        userId = userId,
                        userName = userName,
                        txType = InventoryTxType.RESTORE,
                        note = "استرجاع إلغاء بيع فاتورة $saleId"
                    )
                }
            } else if (item.rawMaterialId.isNotEmpty()) {
                addRawMaterialStock(
                    materialId = item.rawMaterialId,
                    amount = totalToRestore,
                    unit = item.unit,
                    referenceId = saleId,
                    userId = userId,
                    userName = userName,
                    txType = InventoryTxType.RESTORE,
                    note = "استرجاع إلغاء بيع فاتورة $saleId"
                )
            }
        }
    }

    suspend fun deductRawMaterial(
        materialId: String,
        amount: Double,
        unit: String,
        referenceId: String,
        userId: String,
        userName: String,
        txType: InventoryTxType,
        note: String
    ) {
        val mat = rawMaterialDao.getRawMaterialById(materialId) ?: return
        val converted = UnitConverter.convert(amount, unit, mat.baseUnit)
        val prevStock = mat.currentStock
        val newStock = prevStock - converted

        rawMaterialDao.updateRawMaterial(mat.copy(currentStock = newStock, updatedAt = System.currentTimeMillis()))
        inventoryTransactionDao.insertTransaction(
            InventoryTransactionEntity(
                rawMaterialId = materialId,
                rawMaterialName = mat.name,
                type = txType,
                quantityChange = -converted,
                unit = mat.baseUnit,
                previousQuantity = prevStock,
                newQuantity = newStock,
                referenceId = referenceId,
                notes = note,
                userId = userId,
                userName = userName
            )
        )
    }

    suspend fun addRawMaterialStock(
        materialId: String,
        amount: Double,
        unit: String,
        referenceId: String,
        userId: String,
        userName: String,
        txType: InventoryTxType,
        note: String
    ) {
        val mat = rawMaterialDao.getRawMaterialById(materialId) ?: return
        val converted = UnitConverter.convert(amount, unit, mat.baseUnit)
        val prevStock = mat.currentStock
        val newStock = prevStock + converted

        rawMaterialDao.updateRawMaterial(mat.copy(currentStock = newStock, updatedAt = System.currentTimeMillis()))
        inventoryTransactionDao.insertTransaction(
            InventoryTransactionEntity(
                rawMaterialId = materialId,
                rawMaterialName = mat.name,
                type = txType,
                quantityChange = converted,
                unit = mat.baseUnit,
                previousQuantity = prevStock,
                newQuantity = newStock,
                referenceId = referenceId,
                notes = note,
                userId = userId,
                userName = userName
            )
        )
    }
}
