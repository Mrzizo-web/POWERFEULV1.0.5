package com.example.data.engine

import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.RecipeItemEntity

class CostEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val recipeDao: RecipeDao,
    private val mixtureDao: MixtureDao
) {
    suspend fun calculateRecipeTotalCost(recipeId: String): Double {
        val items = recipeDao.getRecipeItemsSync(recipeId)
        var total = 0.0
        for (item in items) {
            total += calculateItemCost(item)
        }
        return total
    }

    suspend fun calculateItemCost(item: RecipeItemEntity): Double {
        val qty = if (item.amount > 0) item.amount else item.quantity
        if (item.mixtureId.isNotEmpty()) {
            val mixtureCostPerUnit = calculateMixtureUnitCost(item.mixtureId)
            return mixtureCostPerUnit * qty
        }
        if (item.rawMaterialId.isNotEmpty()) {
            val mat = rawMaterialDao.getRawMaterialById(item.rawMaterialId) ?: return 0.0
            val normalizedQty = UnitConverter.convert(qty, item.unit, mat.baseUnit)
            val unitPrice = if (mat.avgCostPerUnit > 0) mat.avgCostPerUnit else mat.lastPurchasePrice
            return unitPrice * normalizedQty
        }
        return 0.0
    }

    suspend fun calculateMixtureUnitCost(mixtureId: String): Double {
        val mixture = mixtureDao.getMixtureById(mixtureId) ?: return 0.0
        if (mixture.unitCost > 0) return mixture.unitCost
        val items = mixtureDao.getMixtureItemsSync(mixtureId)
        var sum = 0.0
        for (item in items) {
            val mat = rawMaterialDao.getRawMaterialById(item.rawMaterialId)
            if (mat != null) {
                val normalizedQty = UnitConverter.convert(item.quantity, item.unit, mat.baseUnit)
                val price = if (mat.avgCostPerUnit > 0) mat.avgCostPerUnit else mat.lastPurchasePrice
                sum += price * normalizedQty
            }
        }
        val outQty = if (mixture.outputQuantity > 0) mixture.outputQuantity else 1.0
        return sum / outQty
    }
}
