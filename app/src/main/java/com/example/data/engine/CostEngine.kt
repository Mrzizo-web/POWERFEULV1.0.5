package com.example.data.engine

import com.example.data.local.dao.MixtureDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.RawMaterialDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.entity.RecipeItemEntity

class CostEngine(
    private val rawMaterialDao: RawMaterialDao,
    private val recipeDao: RecipeDao,
    private val mixtureDao: MixtureDao,
    private val productDao: ProductDao? = null
) {
    suspend fun calculateRecipeTotalCost(recipeId: String): Double {
        val recipe = recipeDao.getRecipeById(recipeId)
            ?: throw IllegalArgumentException("الوصفة غير موجودة: " + recipeId)
        val items = recipeDao.getRecipeItemsSync(recipeId)
        require(items.isNotEmpty()) { "الوصفة " + recipe.name + " لا تحتوي على مكونات" }
        val total = items.sumOf { calculateItemCost(it) }
        require(total.isFinite() && total >= 0.0) { "إجمالي تكلفة الوصفة غير صالح" }
        return total
    }

    suspend fun calculateItemCost(item: RecipeItemEntity): Double {
        val qty = if (item.amount > 0.0) item.amount else item.quantity
        require(qty.isFinite() && qty > 0.0) {
            "كمية غير صالحة في مكوّن الوصفة " + item.name
        }

        if (item.mixtureId.isNotBlank()) {
            val mixture = mixtureDao.getMixtureById(item.mixtureId)
                ?: throw IllegalArgumentException("الخلطة غير موجودة: " + item.mixtureId)
            require(mixture.outputQuantity.isFinite() && mixture.outputQuantity > 0.0) {
                "كمية إنتاج الخلطة غير صالحة: " + mixture.name
            }
            val normalizedQty = UnitConverter.convert(qty, item.unit, mixture.unit)
            val unitCost = calculateMixtureUnitCost(item.mixtureId)
            val cost = unitCost * normalizedQty
            require(cost.isFinite() && cost >= 0.0) {
                "تكلفة غير صالحة لمكوّن الخلطة " + mixture.name
            }
            return cost
        }

        if (item.rawMaterialId.isNotBlank()) {
            val material = rawMaterialDao.getRawMaterialById(item.rawMaterialId)
                ?: throw IllegalArgumentException("المادة الخام غير موجودة: " + item.rawMaterialId)
            val normalizedQty = UnitConverter.convert(qty, item.unit, material.baseUnit)
            val unitPrice = when {
                material.avgCostPerUnit.isFinite() && material.avgCostPerUnit > 0.0 -> material.avgCostPerUnit
                material.lastPurchasePrice.isFinite() && material.lastPurchasePrice > 0.0 -> material.lastPurchasePrice
                else -> throw IllegalArgumentException("تكلفة الوحدة غير صالحة للمادة " + material.name)
            }
            val cost = unitPrice * normalizedQty
            require(cost.isFinite() && cost >= 0.0) { "تكلفة غير صالحة للمادة " + material.name }
            return cost
        }

        throw IllegalArgumentException("مكوّن الوصفة لا يرتبط بمادة خام أو خلطة: " + item.name)
    }

    /**
     * Refresh the current cost shown on active recipes/products after raw-material
     * purchase prices change. Historical SaleEntity/SaleItemEntity rows are
     * immutable snapshots and are never rewritten by this operation.
     * Call inside the purchase transaction so cost and stock-price changes commit together.
     */
    suspend fun refreshActiveRecipeAndProductCosts() {
        val products = productDao
        for (recipe in recipeDao.getAllRecipesSync().filter { it.isActive }) {
            val totalCost = calculateRecipeTotalCost(recipe.id)
            if (!totalCost.isFinite() || totalCost < 0.0) {
                throw IllegalArgumentException("تكلفة الوصفة غير صالحة: " + recipe.name)
            }
            if (recipe.calculatedCost != totalCost) {
                recipeDao.updateRecipe(recipe.copy(calculatedCost = totalCost))
            }
            val product = products?.getProductById(recipe.productId)
            if (product != null && (product.costPrice != totalCost || product.recipeId != recipe.id)) {
                products.updateProduct(product.copy(costPrice = totalCost, recipeId = recipe.id))
            }
        }
    }

    suspend fun calculateMixtureUnitCost(mixtureId: String): Double {
        val mixture = mixtureDao.getMixtureById(mixtureId)
            ?: throw IllegalArgumentException("الخلطة غير موجودة: " + mixtureId)
        require(mixture.outputQuantity.isFinite() && mixture.outputQuantity > 0.0) {
            "كمية إنتاج الخلطة غير صالحة: " + mixture.name
        }
        // Recompute from current raw-material costs instead of trusting cached unitCost,
        // which can become stale after a supplier purchase changes material prices.
        val items = mixtureDao.getMixtureItemsSync(mixtureId)
        require(items.isNotEmpty()) { "الخلطة " + mixture.name + " لا تحتوي على مواد خام" }
        var sum = 0.0
        for (item in items) {
            require(item.quantity.isFinite() && item.quantity > 0.0) {
                "كمية غير صالحة في الخلطة " + mixture.name
            }
            val material = rawMaterialDao.getRawMaterialById(item.rawMaterialId)
                ?: throw IllegalArgumentException("مادة خام غير موجودة في الخلطة: " + item.rawMaterialId)
            val normalizedQty = UnitConverter.convert(item.quantity, item.unit, material.baseUnit)
            val price = when {
                material.avgCostPerUnit.isFinite() && material.avgCostPerUnit > 0.0 -> material.avgCostPerUnit
                material.lastPurchasePrice.isFinite() && material.lastPurchasePrice > 0.0 -> material.lastPurchasePrice
                else -> throw IllegalArgumentException("تكلفة الوحدة غير صالحة للمادة " + material.name)
            }
            sum += price * normalizedQty
        }
        val unitCost = sum / mixture.outputQuantity
        require(unitCost.isFinite() && unitCost >= 0.0) {
            "متوسط تكلفة الخلطة غير صالح: " + mixture.name
        }
        return unitCost
    }
}
