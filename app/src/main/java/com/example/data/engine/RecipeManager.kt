package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeItemEntity
import com.example.data.local.entity.UserEntity

/**
 * Saves a complete recipe and its active product binding as one Room transaction.
 * Previous recipe versions remain stored and become inactive.
 */
class RecipeManager(private val db: AppDatabase) {
    suspend fun saveRecipe(
        name: String,
        productId: String,
        items: List<RecipeItemEntity>,
        notes: String,
        user: UserEntity
    ): RecipeEntity = db.withTransaction {
        require(name.isNotBlank()) { "اسم الوصفة مطلوب" }
        require(productId.isNotBlank()) { "يجب اختيار منتج للوصفة" }
        require(items.isNotEmpty()) { "يجب إضافة مكوّن واحد على الأقل" }

        val product = db.productDao().getProductById(productId)
            ?: throw IllegalArgumentException("المنتج المرتبط بالوصفة غير موجود")
        val recipeId = java.util.UUID.randomUUID().toString()
        val nextVersion = db.recipeDao().getMaxVersion(productId) + 1
        val normalizedItems = items.map { item ->
            val material = db.rawMaterialDao().getRawMaterialById(item.rawMaterialId)
                ?: throw IllegalArgumentException("المادة الخام غير موجودة: " + item.rawMaterialId)
            val amount = if (item.amount > 0.0) item.amount else item.quantity
            require(amount.isFinite() && amount > 0.0) { "كمية غير صالحة للمادة " + material.name }
            val baseAmount = UnitConverter.convert(amount, item.unit, material.baseUnit)
            val cost = baseAmount * material.avgCostPerUnit
            require(cost.isFinite() && cost >= 0.0) { "تكلفة غير صالحة للمادة " + material.name }
            item.copy(
                recipeId = recipeId,
                name = material.name,
                amount = amount,
                quantity = amount,
                costContribution = cost
            )
        }
        val totalCost = normalizedItems.sumOf { it.costContribution }
        require(totalCost.isFinite() && totalCost >= 0.0) { "تعذر حساب تكلفة الوصفة" }

        db.recipeDao().deactivateActiveRecipesForProduct(productId)
        val recipe = RecipeEntity(
            id = recipeId,
            productId = productId,
            name = name.trim(),
            calculatedCost = totalCost,
            version = nextVersion,
            isActive = true,
            notes = notes.trim()
        )
        db.recipeDao().insertRecipe(recipe)
        db.recipeDao().insertRecipeItems(normalizedItems)
        db.productDao().updateProduct(product.copy(recipeId = recipeId, costPrice = totalCost))
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "إضافة وصفة",
                entityType = "RECIPE",
                entityId = recipeId,
                newValue = "الإصدار " + nextVersion + "؛ التكلفة " + totalCost,
                notes = "تم حفظ " + normalizedItems.size + " مكوّنات للوصفة " + name
            )
        )
        recipe
    }
}
