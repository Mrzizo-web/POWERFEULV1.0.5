package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.UserEntity
import com.example.security.AppPermission
import com.example.security.PermissionChecker

class ProductManager(private val db: AppDatabase) {
    suspend fun saveProduct(product: ProductEntity, user: UserEntity): ProductEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_PRODUCTS)) {
            "ليس لديك صلاحية إدارة المنتجات"
        }
        val name = product.name.trim()
        require(name.isNotBlank()) { "اسم المنتج مطلوب" }
        require(product.price.isFinite() && product.price > 0.0) { "سعر البيع يجب أن يكون موجبًا وصالحًا" }
        require(product.costPrice.isFinite() && product.costPrice >= 0.0) { "تكلفة المنتج غير صالحة" }
        require(product.minStockAlert.isFinite() && product.minStockAlert >= 0.0) { "حد تنبيه المخزون غير صالح" }

        val category = db.categoryDao().getCategoryByIdSync(product.categoryId)
            ?: throw IllegalArgumentException("التصنيف المختار غير موجود")
        require(category.isActive) { "لا يمكن ربط المنتج بتصنيف غير نشط" }

        val current = db.productDao().getProductById(product.id)
        val isNew = current == null
        val others = db.productDao().getAllProductsSync().filter { it.id != product.id }
        val sku = product.sku.trim()
        val barcode = product.barcode.trim()
        if (sku.isNotBlank()) {
            require(others.none { it.sku.trim().equals(sku, ignoreCase = true) }) {
                "SKU مستخدم بالفعل في منتج آخر"
            }
        }
        if (barcode.isNotBlank()) {
            require(others.none { it.barcode.trim() == barcode }) {
                "الباركود مستخدم بالفعل في منتج آخر"
            }
        }

        val saved = product.copy(
            name = name,
            sku = sku,
            barcode = barcode,
            categoryId = category.id
        )
        if (isNew) {
            db.productDao().insertProduct(saved)
        } else {
            db.productDao().updateProduct(saved)
        }

        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = if (isNew) "PRODUCT_CREATED" else "PRODUCT_UPDATED",
                entityType = "PRODUCT",
                entityId = saved.id,
                previousValue = current?.let {
                    "name=" + it.name + "; price=" + it.price + "; cost=" + it.costPrice +
                        "; category=" + it.categoryId + "; sku=" + it.sku + "; barcode=" + it.barcode
                }.orEmpty(),
                newValue = "name=" + saved.name + "; price=" + saved.price + "; cost=" + saved.costPrice +
                    "; category=" + saved.categoryId + "; sku=" + saved.sku + "; barcode=" + saved.barcode,
                notes = "حفظ المنتج مع التحقق من التصنيف وتفرّد SKU والباركود"
            )
        )
        saved
    }
}
