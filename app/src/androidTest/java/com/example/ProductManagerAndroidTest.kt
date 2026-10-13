package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.ProductManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductManagerAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var manager: ProductManager
    private val owner = UserEntity(
        id = "product-test-owner",
        name = "مالك اختبار المنتجات",
        username = "product_test_owner",
        role = UserRole.OWNER
    )

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = ProductManager(db)
        db.userDao().insertUser(owner)
        db.categoryDao().insertCategory(CategoryEntity(id = "category-drinks", name = "مشروبات"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createAndEditProductPersistCategorySkuBarcodeCostAndAvailability() = runBlocking {
        val created = manager.saveProduct(
            ProductEntity(
                id = "product-manager-test",
                name = "Power Full",
                categoryId = "category-drinks",
                price = 1200.0,
                costPrice = 450.0,
                sku = "PF-001",
                barcode = "629123450001",
                minStockAlert = 4.0,
                isAvailable = true
            ),
            owner
        )
        assertEquals("category-drinks", created.categoryId)
        assertEquals("PF-001", created.sku)
        assertEquals("629123450001", created.barcode)
        assertEquals(450.0, created.costPrice, 0.001)

        val updated = manager.saveProduct(
            created.copy(price = 1300.0, costPrice = 500.0, sku = "PF-002", isAvailable = false),
            owner
        )
        assertEquals(created.id, updated.id)
        assertEquals(1300.0, updated.price, 0.001)
        assertEquals(500.0, updated.costPrice, 0.001)
        assertEquals("PF-002", updated.sku)
        assertEquals("629123450001", updated.barcode)
        assertTrue(!updated.isAvailable)
        assertEquals(2, db.auditLogDao().getRecentLogs().first().count {
            it.entityType == "PRODUCT" && it.entityId == created.id
        })
    }

    @Test
    fun duplicateSkuOrBarcodeIsRejectedWithoutChangingExistingProduct() = runBlocking {
        manager.saveProduct(
            ProductEntity(id = "product-one", name = "منتج أول", categoryId = "category-drinks", price = 10.0, sku = "SKU-1", barcode = "BAR-1"),
            owner
        )
        val duplicateSku = runCatching {
            manager.saveProduct(
                ProductEntity(id = "product-two", name = "منتج ثان", categoryId = "category-drinks", price = 11.0, sku = "sku-1"),
                owner
            )
        }
        assertTrue(duplicateSku.isFailure)
        val duplicateBarcode = runCatching {
            manager.saveProduct(
                ProductEntity(id = "product-three", name = "منتج ثالث", categoryId = "category-drinks", price = 12.0, barcode = "BAR-1"),
                owner
            )
        }
        assertTrue(duplicateBarcode.isFailure)
        assertEquals(1, db.productDao().getAllProductsSync().size)
        assertEquals("منتج أول", db.productDao().getProductById("product-one")!!.name)
    }

    @Test
    fun inactiveOrMissingCategoryIsRejectedWithoutCreatingProduct() = runBlocking {
        db.categoryDao().insertCategory(
            CategoryEntity(id = "inactive-category", name = "تصنيف معطل", isActive = false)
        )
        val missing = runCatching {
            manager.saveProduct(
                ProductEntity(id = "missing-category-product", name = "منتج", categoryId = "missing", price = 10.0),
                owner
            )
        }
        val inactive = runCatching {
            manager.saveProduct(
                ProductEntity(id = "inactive-category-product", name = "منتج", categoryId = "inactive-category", price = 10.0),
                owner
            )
        }
        assertTrue(missing.isFailure)
        assertTrue(inactive.isFailure)
        assertTrue(db.productDao().getAllProductsSync().isEmpty())
    }
}
