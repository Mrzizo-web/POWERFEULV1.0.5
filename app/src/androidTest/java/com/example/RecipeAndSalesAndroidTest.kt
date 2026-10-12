package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecipeAndSalesAndroidTest {

    private lateinit var db: AppDatabase
    private lateinit var inventoryEngine: InventoryEngine
    private lateinit var salesEngine: SalesEngine

    private val testUser = UserEntity(
        id = "cashier-1",
        name = "كاشير تجريبي",
        username = "cashier_test",
        role = UserRole.CASHIER
    )

    private val testShift = ShiftEntity(
        id = "shift-1",
        shiftNumber = 1,
        userId = "cashier-1",
        userName = "كاشير تجريبي",
        openingCash = 5000.0,
        expectedCash = 5000.0,
        status = ShiftStatus.OPEN
    )

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        inventoryEngine = InventoryEngine(
            db.rawMaterialDao(),
            db.recipeDao(),
            db.mixtureDao(),
            db.inventoryTransactionDao(),
            db.productDao()
        )
        salesEngine = SalesEngine(db, inventoryEngine)

        db.userDao().insertUser(testUser)
        db.shiftDao().insertShift(testShift)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPowerFullRecipeSellingThreePortionsDeductsExactQuantitiesAndRestoresOnVoid() = runBlocking {
        // 1. Setup raw materials with initial stock
        val banana = RawMaterialEntity(id = "mat-banana", name = "موز", baseUnit = "g", currentStock = 1000.0, avgCostPerUnit = 0.05)
        val milk = RawMaterialEntity(id = "mat-milk", name = "حليب", baseUnit = "ml", currentStock = 1000.0, avgCostPerUnit = 0.02)
        val dates = RawMaterialEntity(id = "mat-dates", name = "تمر", baseUnit = "g", currentStock = 500.0, avgCostPerUnit = 0.1)
        val nuts = RawMaterialEntity(id = "mat-nuts", name = "مكسرات", baseUnit = "g", currentStock = 500.0, avgCostPerUnit = 0.2)
        val oats = RawMaterialEntity(id = "mat-oats", name = "شوفان", baseUnit = "g", currentStock = 500.0, avgCostPerUnit = 0.04)
        val honey = RawMaterialEntity(id = "mat-honey", name = "عسل", baseUnit = "g", currentStock = 500.0, avgCostPerUnit = 0.15)

        listOf(banana, milk, dates, nuts, oats, honey).forEach { db.rawMaterialDao().insertRawMaterial(it) }

        // 2. Setup product "Power Full"
        val powerFullProduct = ProductEntity(
            id = "prod-power-full",
            name = "Power Full",
            categoryId = "cat-shakes",
            price = 1500.0,
            costPrice = 800.0,
            isActive = true
        )
        db.productDao().insertProduct(powerFullProduct)

        // 3. Setup recipe with required components:
        // موز: 100 g, حليب: 130 ml, تمر: 50 g, مكسرات: 20 g, شوفان: 30 g, عسل: 20 g
        val recipeId = "recipe-power-full"
        val recipe = RecipeEntity(
            id = recipeId,
            productId = powerFullProduct.id,
            name = "خلطة باور فول",
            calculatedCost = 800.0,
            isActive = true
        )
        db.recipeDao().insertRecipe(recipe)

        val recipeItems = listOf(
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-banana", name = "موز", amount = 100.0, quantity = 100.0, unit = "g"),
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-milk", name = "حليب", amount = 130.0, quantity = 130.0, unit = "ml"),
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-dates", name = "تمر", amount = 50.0, quantity = 50.0, unit = "g"),
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-nuts", name = "مكسرات", amount = 20.0, quantity = 20.0, unit = "g"),
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-oats", name = "شوفان", amount = 30.0, quantity = 30.0, unit = "g"),
            RecipeItemEntity(recipeId = recipeId, rawMaterialId = "mat-honey", name = "عسل", amount = 20.0, quantity = 20.0, unit = "g")
        )
        db.recipeDao().insertRecipeItems(recipeItems)

        // 4. Sell 3 portions of "Power Full"
        val cartItems = listOf(CartItem(product = powerFullProduct, quantity = 3))
        val saleResult = salesEngine.executeSale(
            shift = testShift,
            user = testUser,
            items = cartItems,
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 5000.0
        )

        assertTrue("Sale must succeed", saleResult is SaleResult.Success)
        val success = saleResult as SaleResult.Success
        assertEquals(4500.0, success.sale.netAmount, 0.001)

        // 5. Verify exact deductions:
        // Banana: 1000 - (100 * 3) = 700 g (Deduction: 300 g)
        // Milk: 1000 - (130 * 3) = 610 ml (Deduction: 390 ml)
        // Dates: 500 - (50 * 3) = 350 g (Deduction: 150 g)
        // Nuts: 500 - (20 * 3) = 440 g (Deduction: 60 g)
        // Oats: 500 - (30 * 3) = 410 g (Deduction: 90 g)
        // Honey: 500 - (20 * 3) = 440 g (Deduction: 60 g)
        val updatedBanana = db.rawMaterialDao().getRawMaterialById("mat-banana")!!
        val updatedMilk = db.rawMaterialDao().getRawMaterialById("mat-milk")!!
        val updatedDates = db.rawMaterialDao().getRawMaterialById("mat-dates")!!
        val updatedNuts = db.rawMaterialDao().getRawMaterialById("mat-nuts")!!
        val updatedOats = db.rawMaterialDao().getRawMaterialById("mat-oats")!!
        val updatedHoney = db.rawMaterialDao().getRawMaterialById("mat-honey")!!

        assertEquals(700.0, updatedBanana.currentStock, 0.001)
        assertEquals(610.0, updatedMilk.currentStock, 0.001)
        assertEquals(350.0, updatedDates.currentStock, 0.001)
        assertEquals(440.0, updatedNuts.currentStock, 0.001)
        assertEquals(410.0, updatedOats.currentStock, 0.001)
        assertEquals(440.0, updatedHoney.currentStock, 0.001)

        // 6. Verify inventory transaction records
        val bananaTxs = db.inventoryTransactionDao().getTransactionsForMaterial("mat-banana").first()
        assertEquals(1, bananaTxs.size)
        assertEquals(1000.0, bananaTxs[0].previousQuantity, 0.001)
        assertEquals(-300.0, bananaTxs[0].quantityChange, 0.001)
        assertEquals(700.0, bananaTxs[0].newQuantity, 0.001)

        // 7. Verify voiding sale restores stock back to 100%
        val voidResult = salesEngine.voidSale(success.sale.id, "إلغاء تجريبي", testUser)
        assertTrue(voidResult.isSuccess)

        val restoredBanana = db.rawMaterialDao().getRawMaterialById("mat-banana")!!
        val restoredMilk = db.rawMaterialDao().getRawMaterialById("mat-milk")!!
        assertEquals(1000.0, restoredBanana.currentStock, 0.001)
        assertEquals(1000.0, restoredMilk.currentStock, 0.001)
    }
    @Test
    fun testInsufficientRecipeStockRejectsSaleWithoutPartialWrites() = runBlocking {
        val banana = RawMaterialEntity(id = "mat-low-banana", name = "موز", baseUnit = "g", currentStock = 100.0)
        val milk = RawMaterialEntity(id = "mat-low-milk", name = "حليب", baseUnit = "ml", currentStock = 1000.0)
        db.rawMaterialDao().insertRawMaterial(banana)
        db.rawMaterialDao().insertRawMaterial(milk)
        val product = ProductEntity(id = "prod-low-stock", name = "اختبار مخزون ناقص", categoryId = "cat-test", price = 100.0, recipeId = "recipe-low-stock")
        db.productDao().insertProduct(product)
        db.recipeDao().insertRecipe(RecipeEntity(id = "recipe-low-stock", productId = product.id, name = "وصفة اختبار", isActive = true))
        db.recipeDao().insertRecipeItems(listOf(
            RecipeItemEntity(recipeId = "recipe-low-stock", rawMaterialId = banana.id, name = banana.name, amount = 150.0, quantity = 150.0, unit = "g"),
            RecipeItemEntity(recipeId = "recipe-low-stock", rawMaterialId = milk.id, name = milk.name, amount = 100.0, quantity = 100.0, unit = "ml")
        ))

        val result = salesEngine.executeSale(
            shift = testShift,
            user = testUser,
            items = listOf(CartItem(product = product, quantity = 1)),
            paymentMethod = PaymentMethod.CASH,
            cashReceived = 100.0
        )

        assertTrue("Sale should be rejected for insufficient raw material", result is SaleResult.Error)
        assertEquals(100.0, db.rawMaterialDao().getRawMaterialById(banana.id)!!.currentStock, 0.001)
        assertEquals(1000.0, db.rawMaterialDao().getRawMaterialById(milk.id)!!.currentStock, 0.001)
        assertEquals(0, db.saleDao().getSalesCount())
        assertTrue(db.inventoryTransactionDao().getRecentTransactions().first().isEmpty())
        assertEquals(5000.0, db.shiftDao().getShiftById(testShift.id)!!.expectedCash, 0.001)
    }

    @Test
    fun recipeManagerStoresAllComponentsAndPreservesVersionsAtomically() = runBlocking {
        val banana = RawMaterialEntity(
            id = "manager-banana", name = "موز", baseUnit = "g",
            currentStock = 2000.0, avgCostPerUnit = 0.5
        )
        val oats = RawMaterialEntity(
            id = "manager-oats", name = "شوفان", baseUnit = "g",
            currentStock = 1000.0, avgCostPerUnit = 0.2
        )
        db.rawMaterialDao().insertRawMaterial(banana)
        db.rawMaterialDao().insertRawMaterial(oats)
        val product = ProductEntity(
            id = "manager-product", name = "وصفة متعددة", categoryId = "cat-test",
            price = 1000.0, recipeId = ""
        )
        db.productDao().insertProduct(product)
        val manager = RecipeManager(db)

        val first = manager.saveRecipe(
            name = "وصفة أولى",
            productId = product.id,
            items = listOf(
                RecipeItemEntity(recipeId = "", rawMaterialId = banana.id, name = banana.name, amount = 100.0, quantity = 100.0, unit = "g"),
                RecipeItemEntity(recipeId = "", rawMaterialId = oats.id, name = oats.name, amount = 50.0, quantity = 50.0, unit = "g")
            ),
            notes = "اختبار الإصدار الأول",
            user = testUser
        )

        assertEquals(1, first.version)
        assertEquals(2, db.recipeDao().getRecipeItemsSync(first.id).size)
        assertEquals(first.id, db.productDao().getProductById(product.id)!!.recipeId)
        assertEquals(60.0, first.calculatedCost, 0.001)

        val second = manager.saveRecipe(
            name = "وصفة معدلة",
            productId = product.id,
            items = listOf(
                RecipeItemEntity(recipeId = "", rawMaterialId = banana.id, name = banana.name, amount = 200.0, quantity = 200.0, unit = "g")
            ),
            notes = "اختبار الإصدار الثاني",
            user = testUser
        )

        assertEquals(2, second.version)
        assertEquals(1, db.recipeDao().getRecipeItemsSync(second.id).size)
        assertFalse(db.recipeDao().getRecipeById(first.id)!!.isActive)
        assertTrue(db.recipeDao().getRecipeById(second.id)!!.isActive)
        assertEquals(second.id, db.productDao().getProductById(product.id)!!.recipeId)
        assertEquals(2, db.recipeDao().getAllRecipesSync().size)
    }

}
