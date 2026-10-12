package com.example.data.engine

import com.example.data.local.dao.InventoryTransactionDao
import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.InventoryTransactionEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.domain.model.InventoryTxType

class InventoryValidationException(message: String) : IllegalArgumentException(message)

class InventoryEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val recipeDao: RecipeDao,
    private val mixtureDao: MixtureDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val productDao: ProductDao? = null
) {
    private data class RequestedLine(val materialId: String, val amount: Double, val unit: String)
    private data class AggregatedNeed(val material: RawMaterialEntity, val amountInBaseUnit: Double)

    /**
     * Validate the full recipe before stock changes. SalesEngine calls this inside
     * the same Room transaction that creates the sale.
     */
    suspend fun consumeForProduct(
        productId: String,
        quantity: Int,
        saleId: String,
        userId: String,
        userName: String
    ) {
        if (quantity <= 0) throw InventoryValidationException("كمية البيع يجب أن تكون أكبر من صفر")

        val recipe = recipeDao.getActiveRecipeForProductSync(productId)
        if (recipe == null) {
            val product = productDao?.getProductById(productId)
            if (!product?.recipeId.isNullOrBlank()) {
                throw InventoryValidationException("الوصفة المرتبطة بالمنتج ${product?.name ?: productId} غير موجودة أو غير نشطة")
            }
            // Products intentionally sold without a recipe do not consume raw materials.
            return
        }

        val recipeItems = recipeDao.getRecipeItemsSync(recipe.id)
        if (recipeItems.isEmpty()) throw InventoryValidationException("الوصفة ${recipe.name} لا تحتوي على مكونات")

        val requested = mutableListOf<RequestedLine>()
        for (item in recipeItems) {
            val perPortion = if (item.amount > 0.0) item.amount else item.quantity
            if (!perPortion.isFinite() || perPortion <= 0.0) {
                throw InventoryValidationException("كمية غير صالحة في الوصفة ${recipe.name}: ${item.name}")
            }
            val requiredForSale = perPortion * quantity
            if (!requiredForSale.isFinite() || requiredForSale <= 0.0) {
                throw InventoryValidationException("تعذر حساب استهلاك المكوّن ${item.name}")
            }

            if (item.mixtureId.isNotBlank()) {
                val mixture = mixtureDao.getMixtureById(item.mixtureId)
                    ?: throw InventoryValidationException("الخلطة المرتبطة بالمكوّن ${item.name} غير موجودة")
                if (!mixture.outputQuantity.isFinite() || mixture.outputQuantity <= 0.0) {
                    throw InventoryValidationException("كمية إنتاج الخلطة ${mixture.name} غير صالحة")
                }
                val mixtureItems = mixtureDao.getMixtureItemsSync(item.mixtureId)
                if (mixtureItems.isEmpty()) throw InventoryValidationException("الخلطة ${mixture.name} لا تحتوي على مواد خام")
                val requiredOutput = UnitConverter.convert(requiredForSale, item.unit, mixture.unit)
                val ratio = requiredOutput / mixture.outputQuantity
                for (mixtureItem in mixtureItems) {
                    val needed = mixtureItem.quantity * ratio
                    if (mixtureItem.rawMaterialId.isBlank() || !needed.isFinite() || needed <= 0.0) {
                        throw InventoryValidationException("مكوّن غير صالح في الخلطة ${mixture.name}")
                    }
                    requested += RequestedLine(mixtureItem.rawMaterialId, needed, mixtureItem.unit)
                }
            } else if (item.rawMaterialId.isNotBlank()) {
                requested += RequestedLine(item.rawMaterialId, requiredForSale, item.unit)
            } else {
                throw InventoryValidationException("المكوّن ${item.name} لا يرتبط بمادة خام أو خلطة")
            }
        }

        // Aggregate repeated material lines and validate every balance before any writes.
        val aggregatedAmounts = linkedMapOf<String, Double>()
        val materials = linkedMapOf<String, RawMaterialEntity>()
        for (line in requested) {
            val material = rawMaterialDao.getRawMaterialById(line.materialId)
                ?: throw InventoryValidationException("المادة الخام المرتبطة بالوصفة غير موجودة (ID: ${line.materialId})")
            val converted = try {
                UnitConverter.convert(line.amount, line.unit, material.baseUnit)
            } catch (e: IllegalArgumentException) {
                throw InventoryValidationException("وحدة قياس غير متوافقة للمادة ${material.name}: ${line.unit} ← ${material.baseUnit}")
            }
            if (!converted.isFinite() || converted <= 0.0) {
                throw InventoryValidationException("كمية استهلاك غير صالحة للمادة ${material.name}")
            }
            materials[line.materialId] = material
            aggregatedAmounts[line.materialId] = (aggregatedAmounts[line.materialId] ?: 0.0) + converted
        }

        val needs = aggregatedAmounts.map { (id, amount) -> AggregatedNeed(materials.getValue(id), amount) }
        for (need in needs) {
            val available = need.material.currentStock
            if (!available.isFinite() || available + STOCK_EPSILON < need.amountInBaseUnit) {
                throw InventoryValidationException(
                    "المخزون غير كافٍ للمادة ${need.material.name}: المطلوب ${format(need.amountInBaseUnit)} ${need.material.baseUnit}، المتاح ${format(available)} ${need.material.baseUnit}"
                )
            }
        }

        for (need in needs) {
            deductRawMaterial(
                materialId = need.material.id,
                amount = need.amountInBaseUnit,
                unit = need.material.baseUnit,
                referenceId = saleId,
                userId = userId,
                userName = userName,
                txType = InventoryTxType.CONSUMPTION,
                note = "استهلاك وصفة ${recipe.name} لبيع فاتورة $saleId"
            )
        }
    }

    /**
     * Restore the exact consumption transactions recorded for the sale. Do not
     * recalculate from today's recipe, which may differ from the recipe sold.
     */
    suspend fun restoreSaleConsumption(saleId: String, userId: String, userName: String) {
        val consumed = inventoryTransactionDao.getTransactionsByReference(saleId)
            .filter { it.type == InventoryTxType.CONSUMPTION && it.quantityChange < 0.0 }
        for (transaction in consumed) {
            addRawMaterialStock(
                materialId = transaction.rawMaterialId,
                amount = -transaction.quantityChange,
                unit = transaction.unit,
                referenceId = saleId,
                userId = userId,
                userName = userName,
                txType = InventoryTxType.RESTORE,
                note = "استرجاع الكمية المسجلة وقت البيع عند إلغاء فاتورة $saleId"
            )
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
        if (!amount.isFinite() || amount <= 0.0) throw InventoryValidationException("كمية الخصم غير صالحة")
        val mat = rawMaterialDao.getRawMaterialById(materialId)
            ?: throw InventoryValidationException("المادة الخام غير موجودة: $materialId")
        val converted = try {
            UnitConverter.convert(amount, unit, mat.baseUnit)
        } catch (e: IllegalArgumentException) {
            throw InventoryValidationException("وحدة قياس غير متوافقة للمادة ${mat.name}: $unit ← ${mat.baseUnit}")
        }
        val previous = mat.currentStock
        if (!previous.isFinite() || previous + STOCK_EPSILON < converted) {
            throw InventoryValidationException(
                "المخزون غير كافٍ للمادة ${mat.name}: المطلوب ${format(converted)} ${mat.baseUnit}، المتاح ${format(previous)} ${mat.baseUnit}"
            )
        }
        val next = (previous - converted).coerceAtLeast(0.0)
        rawMaterialDao.updateRawMaterial(mat.copy(currentStock = next, updatedAt = System.currentTimeMillis()))
        inventoryTransactionDao.insertTransaction(
            InventoryTransactionEntity(
                rawMaterialId = materialId,
                rawMaterialName = mat.name,
                type = txType,
                quantityChange = -converted,
                unit = mat.baseUnit,
                previousQuantity = previous,
                newQuantity = next,
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
        if (!amount.isFinite() || amount <= 0.0) throw InventoryValidationException("كمية الإضافة غير صالحة")
        val mat = rawMaterialDao.getRawMaterialById(materialId)
            ?: throw InventoryValidationException("المادة الخام غير موجودة: $materialId")
        val converted = try {
            UnitConverter.convert(amount, unit, mat.baseUnit)
        } catch (e: IllegalArgumentException) {
            throw InventoryValidationException("وحدة قياس غير متوافقة للمادة ${mat.name}: $unit ← ${mat.baseUnit}")
        }
        val previous = mat.currentStock
        val next = previous + converted
        if (!next.isFinite()) throw InventoryValidationException("الرصيد الجديد غير صالح للمادة ${mat.name}")
        rawMaterialDao.updateRawMaterial(mat.copy(currentStock = next, updatedAt = System.currentTimeMillis()))
        inventoryTransactionDao.insertTransaction(
            InventoryTransactionEntity(
                rawMaterialId = materialId,
                rawMaterialName = mat.name,
                type = txType,
                quantityChange = converted,
                unit = mat.baseUnit,
                previousQuantity = previous,
                newQuantity = next,
                referenceId = referenceId,
                notes = note,
                userId = userId,
                userName = userName
            )
        )
    }

    private fun format(value: Double): String = "%.3f".format(java.util.Locale.ROOT, value)

    private companion object {
        const val STOCK_EPSILON = 1e-9
    }
}
