package com.example

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtTransactionEntity
import com.example.data.local.entity.InventoryTransactionEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeItemEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.data.local.migration.MIGRATION_1_2
import com.example.data.local.migration.MIGRATION_2_3
import com.example.data.local.migration.MIGRATION_3_4
import com.example.data.local.migration.MIGRATION_4_5
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class DatabaseMigrationAndroidTest {
    @Test
    fun versionTwoToFiveMigrationPreservesBusinessDataAndDeduplicatesEventIds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration-check-" + UUID.randomUUID().toString() + ".db"
        val oldDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        val user = UserEntity(
            id = "migration-user",
            name = "مستخدم اختبار الترحيل",
            username = "migration_user",
            role = UserRole.OWNER
        )
        val product = ProductEntity(
            id = "migration-product",
            name = "منتج اختبار الترحيل",
            categoryId = "migration-category",
            price = 150.0,
            costPrice = 55.0,
            recipeId = "migration-recipe"
        )
        val material = RawMaterialEntity(
            id = "migration-material",
            name = "مادة اختبار الترحيل",
            baseUnit = "g",
            currentStock = 700.0,
            avgCostPerUnit = 0.5
        )
        val recipe = RecipeEntity(
            id = "migration-recipe",
            productId = product.id,
            name = "وصفة اختبار الترحيل",
            calculatedCost = 50.0
        )
        val recipeItem = RecipeItemEntity(
            recipeId = recipe.id,
            rawMaterialId = material.id,
            name = material.name,
            amount = 100.0,
            quantity = 100.0,
            unit = "g",
            costContribution = 50.0
        )
        val shift = ShiftEntity(
            id = "migration-shift",
            shiftNumber = 3,
            userId = user.id,
            userName = user.name,
            openingCash = 1000.0,
            expectedCash = 1250.0,
            status = ShiftStatus.OPEN
        )
        val sale = SaleEntity(
            id = "migration-sale",
            invoiceNumber = "INV-MIGRATION-0001",
            shiftId = shift.id,
            userId = user.id,
            userName = user.name,
            totalAmount = 150.0,
            netAmount = 150.0,
            paymentMethod = PaymentMethod.CASH
        )
        val customer = CustomerEntity(
            id = "migration-customer",
            name = "عميل اختبار",
            currentDebt = 45.0,
            creditLimit = 500.0
        )

        oldDb.userDao().insertUser(user)
        oldDb.productDao().insertProduct(product)
        oldDb.rawMaterialDao().insertRawMaterial(material)
        oldDb.recipeDao().insertRecipe(recipe)
        oldDb.recipeDao().insertRecipeItems(listOf(recipeItem))
        oldDb.shiftDao().insertShift(shift)
        oldDb.saleDao().insertSale(sale)
        oldDb.customerDao().insertCustomer(customer)
        oldDb.inventoryTransactionDao().insertTransaction(
            InventoryTransactionEntity(
                rawMaterialId = material.id,
                rawMaterialName = material.name,
                type = InventoryTxType.CONSUMPTION,
                quantityChange = -100.0,
                unit = "g",
                previousQuantity = 800.0,
                newQuantity = 700.0,
                referenceId = sale.id,
                userId = user.id,
                userName = user.name
            )
        )
        oldDb.debtTransactionDao().insertDebtTransaction(
            DebtTransactionEntity(
                customerId = customer.id,
                customerName = customer.name,
                type = "SALE",
                amount = 45.0,
                balanceAfter = 45.0,
                paymentMethod = PaymentMethod.DEBT,
                referenceId = "INV-MIGRATION-DEBT",
                userId = user.id,
                userName = user.name
            )
        )
        oldDb.walletDao().insertTransaction(
            WalletTransactionEntity(
                id = "migration-wallet-tx",
                walletCode = "FLOOSAK",
                amount = 45.0,
                transactionId = "",
                sender = "مستخدم اختبار",
                receivedAt = 1_500L,
                status = com.example.domain.model.WalletTransferStatus.MATCHED,
                originalSmsId = "migration-event-id"
            )
        )
        oldDb.close()

        // Simulate the documented v2 schema: same business tables, before the v3 queue table.
        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL("DROP TABLE IF EXISTS gateway_offline_queue")
        raw.execSQL("DROP INDEX IF EXISTS index_wallet_transactions_originalSmsId")
        raw.execSQL("ALTER TABLE debt_transactions DROP COLUMN shiftId")
        raw.execSQL("ALTER TABLE debt_transactions DROP COLUMN balanceBefore")
        raw.execSQL("ALTER TABLE shifts DROP COLUMN totalWalletDebtPayments")
        raw.execSQL("ALTER TABLE shifts DROP COLUMN totalCashDebtPayments")
        raw.execSQL(
            """
            INSERT INTO wallet_transactions (
                id, walletCode, amount, transactionId, sender, receivedAt,
                shiftId, cashierId, cashierName, status, originalSmsId,
                notes, rejectionReason, createdAt, updatedAt
            ) VALUES (
                'migration-wallet-tx-duplicate', 'FLOOSAK', 45.0, '', 'مستخدم اختبار',
                1500, NULL, NULL, NULL, 'MATCHED', 'migration-event-id',
                '', '', 1500, 1500
            )
            """.trimIndent()
        )
        raw.execSQL("PRAGMA user_version = 2")
        raw.close()

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(user.id, upgraded.userDao().getUserById(user.id)!!.id)
            assertEquals(product.id, upgraded.productDao().getProductById(product.id)!!.id)
            assertEquals(700.0, upgraded.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
            assertEquals(recipe.id, upgraded.recipeDao().getRecipeById(recipe.id)!!.id)
            assertEquals(1, upgraded.recipeDao().getRecipeItemsSync(recipe.id).size)
            assertEquals(1, upgraded.saleDao().getSalesCount())
            assertEquals(shift.id, upgraded.shiftDao().getShiftById(shift.id)!!.id)
            assertEquals(1, upgraded.debtTransactionDao().getTransactionsForCustomer(customer.id).first().size)
            assertEquals(1, upgraded.inventoryTransactionDao().getTransactionsByReference(sale.id).size)
            assertEquals(0, upgraded.gatewayQueueDao().getAllQueueItems().first().size)
            val walletRows = upgraded.walletDao().getAllTransactionsSync()
            assertEquals(2, walletRows.size)
            assertEquals(1, walletRows.count { it.originalSmsId == "migration-event-id" })
            assertNotNull(walletRows.firstOrNull { it.id == "migration-wallet-tx-duplicate" })
            assertEquals(null, walletRows.first { it.id == "migration-wallet-tx-duplicate" }.originalSmsId)
            assertNotNull(upgraded.settingsDao())
        } finally {
            upgraded.close()
            context.deleteDatabase(dbName)
        }
    }
}
