package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.InventoryEngine
import com.example.data.engine.NewPurchaseItem
import com.example.data.engine.PurchaseEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.SupplierEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PurchaseEngineAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: PurchaseEngine
    private val user = UserEntity(
        id = "purchase-owner",
        name = "مالك الاختبار",
        username = "purchase_owner",
        role = UserRole.OWNER
    )

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val inventory = InventoryEngine(
            db.rawMaterialDao(),
            db.recipeDao(),
            db.mixtureDao(),
            db.inventoryTransactionDao(),
            db.productDao()
        )
        engine = PurchaseEngine(db, inventory)
        db.userDao().insertUser(user)
        db.supplierDao().insertSupplier(SupplierEntity(id = "supplier-1", name = "مورد الاختبار"))
        db.rawMaterialDao().insertRawMaterial(
            RawMaterialEntity(
                id = "material-1",
                name = "شوفان",
                baseUnit = "g",
                currentStock = 1000.0,
                avgCostPerUnit = 0.5,
                lastPurchasePrice = 0.5
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun purchaseAtomicallyUpdatesInvoiceStockMovementAndWeightedAverageCost() = runBlocking {
        val purchaseId = engine.createPurchase(
            supplierId = "supplier-1",
            supplierName = "مورد الاختبار",
            invoiceNumber = "SUP-100",
            items = listOf(
                NewPurchaseItem(
                    rawMaterialId = "material-1",
                    rawMaterialName = "ignored stale name",
                    quantity = 500.0,
                    unit = "g",
                    unitPrice = 2.0
                )
            ),
            user = user
        )

        val material = db.rawMaterialDao().getRawMaterialById("material-1")!!
        assertEquals(1500.0, material.currentStock, 0.001)
        assertEquals(1.0, material.avgCostPerUnit, 0.001)
        assertEquals(2.0, material.lastPurchasePrice, 0.001)
        assertEquals("supplier-1", material.supplierId)

        val purchase = db.purchaseDao().getPurchaseById(purchaseId)!!
        assertEquals("SUP-100", purchase.invoiceNumber)
        assertEquals(1000.0, purchase.totalAmount, 0.001)
        val rows = db.purchaseDao().getPurchaseItems(purchaseId).first()
        assertEquals(1, rows.size)
        assertEquals("شوفان", rows.single().rawMaterialName)

        val stockTransactions = db.inventoryTransactionDao().getTransactionsByReference(purchaseId)
        assertEquals(1, stockTransactions.size)
        assertEquals(500.0, stockTransactions.single().quantityChange, 0.001)
        assertEquals(1000.0, stockTransactions.single().previousQuantity, 0.001)
        assertEquals(1500.0, stockTransactions.single().newQuantity, 0.001)
        assertTrue(db.auditLogDao().getRecentLogs().first().any { it.action == "PURCHASE_CREATED" })
    }

    @Test
    fun duplicateSupplierInvoiceDoesNotChangeStockOrCreateSecondPurchase() = runBlocking {
        val firstItems = listOf(
            NewPurchaseItem("material-1", "شوفان", 100.0, "g", 1.0)
        )
        engine.createPurchase("supplier-1", "مورد الاختبار", "INV-DUP-1", firstItems, user)

        val result = runCatching {
            engine.createPurchase("supplier-1", "مورد الاختبار", "INV-DUP-1", firstItems, user)
        }
        assertTrue("Duplicate supplier invoice must be rejected", result.isFailure)
        assertEquals(1100.0, db.rawMaterialDao().getRawMaterialById("material-1")!!.currentStock, 0.001)
        assertEquals(1, db.purchaseDao().getPurchaseCount())
        assertEquals(1, db.inventoryTransactionDao().getTransactionsByReference(
            db.purchaseDao().getPurchaseBySupplierInvoice("supplier-1", "INV-DUP-1")!!.id
        ).size)
    }

    @Test
    fun invalidSecondLineRollsBackWholePurchaseWithoutPartialStockUpdates() = runBlocking {
        val items = listOf(
            NewPurchaseItem("material-1", "شوفان", 100.0, "g", 1.0),
            NewPurchaseItem("missing-material", "مادة غير موجودة", 50.0, "g", 2.0)
        )
        val result = runCatching {
            engine.createPurchase("supplier-1", "مورد الاختبار", "INV-INVALID-1", items, user)
        }

        assertTrue("Purchase with a missing material must fail", result.isFailure)
        assertEquals(1000.0, db.rawMaterialDao().getRawMaterialById("material-1")!!.currentStock, 0.001)
        assertEquals(0, db.purchaseDao().getPurchaseCount())
        assertTrue(db.inventoryTransactionDao().getTransactionsByReference("unused-purchase-id").isEmpty())
        assertTrue(db.auditLogDao().getRecentLogs().first().none { it.action == "PURCHASE_CREATED" })
    }
}
