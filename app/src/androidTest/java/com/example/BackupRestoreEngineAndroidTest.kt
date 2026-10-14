package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.BackupRestoreEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.UserEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.DebtTransactionEntity
import com.example.data.local.entity.CafeteriaSettingEntity
import com.example.domain.model.PaymentMethod
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRestoreEngineAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: BackupRestoreEngine

    private val ownerId = "backup-test-owner"
    private val backupPassword = "PowerBackup#2026"

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = BackupRestoreEngine(context, db)

        val credentials = PasswordHasher.DEFAULT.hash("4826")
        db.userDao().insertUser(
            UserEntity(
                id = ownerId,
                name = "مالك النسخة التجريبية",
                username = "backup_test_owner",
                pinHash = credentials.hashHex,
                pinSalt = credentials.saltHex,
                role = UserRole.OWNER,
                isActive = true
            )
        )
        db.productDao().insertProduct(
            ProductEntity(
                id = "product-in-backup",
                name = "منتج داخل النسخة",
                categoryId = "category-test",
                price = 250.0,
                costPrice = 100.0
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun encryptedBackupRejectsWrongPasswordWithoutChangingDataAndRestoresCorrectPassword() = runBlocking {
        val backup = engine.createFullBackupBytes(ownerId, "مالك النسخة التجريبية", backupPassword)
        assertTrue("Encrypted payload should not be empty", backup.isNotEmpty())
        assertFalse(
            "Backup bytes should not expose a product name in plaintext",
            String(backup, Charsets.UTF_8).contains("منتج داخل النسخة")
        )

        db.productDao().insertProduct(
            ProductEntity(
                id = "product-added-after-backup",
                name = "منتج أُضيف بعد النسخ",
                categoryId = "category-test",
                price = 500.0
            )
        )
        assertEquals(2, db.productDao().getAllProductsSync().size)

        val wrongPasswordResult = runCatching {
            engine.restoreFullBackup(backup, "WrongBackupPassword#2026", ownerId)
        }
        assertTrue("Wrong password must reject the backup", wrongPasswordResult.isFailure)
        assertEquals(
            "Rejected restore must leave current data untouched",
            2,
            db.productDao().getAllProductsSync().size
        )

        val restoredTableCount = engine.restoreFullBackup(backup, backupPassword, ownerId)
        assertTrue("Restore should report restored tables", restoredTableCount > 0)
        val products = db.productDao().getAllProductsSync()
        assertEquals(1, products.size)
        assertEquals("product-in-backup", products.single().id)
        assertEquals(250.0, products.single().price, 0.001)
        assertTrue("Owner account must remain available", db.userDao().getUserById(ownerId) != null)
    }
    @Test
    fun portableBackupDoesNotRestoreDeviceBoundGeminiKey() = runBlocking {
        val originalCiphertext = "power-ai-gcm:v1:SYNTHETIC_DEVICE_BOUND_CIPHERTEXT"
        db.settingsDao().setSetting(CafeteriaSettingEntity("gemini_api_key", originalCiphertext))

        val backup = engine.createFullBackupBytes(ownerId, "مالك النسخة التجريبية", backupPassword)

        // Simulate a local value on the destination install; the portable backup must
        // replace it with an empty value rather than copy ciphertext bound to another device.
        db.settingsDao().setSetting(CafeteriaSettingEntity("gemini_api_key", "different-local-value"))
        engine.restoreFullBackup(backup, backupPassword, ownerId)

        assertEquals("", db.settingsDao().getSetting("gemini_api_key") ?: "")
    }

    @Test
    fun csvExportsIncludeLiveCustomerExpenseAndDebtRecordsAndExcelFriendlyUtf8() = runBlocking {
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = "csv-customer",
                name = "عميل, \"اختبار\"",
                phone = "777000111",
                creditLimit = 500.0,
                currentDebt = 125.0,
                notes = "ملاحظات, فيها فاصلة"
            )
        )
        db.expenseDao().insertExpense(
            ExpenseEntity(
                id = "csv-expense",
                title = "شراء, مواد خام",
                category = "تشغيلي",
                amount = 42.5,
                paidTo = "المورد",
                paymentMethod = PaymentMethod.CASH,
                notes = "مصروف اختبار",
                userId = ownerId,
                userName = "مالك اختبار"
            )
        )
        db.debtTransactionDao().insertDebtTransaction(
            DebtTransactionEntity(
                id = "csv-debt-tx",
                customerId = "csv-customer",
                customerName = "عميل, \"اختبار\"",
                type = "PAYMENT",
                amount = 25.0,
                balanceBefore = 150.0,
                balanceAfter = 125.0,
                paymentMethod = PaymentMethod.CASH,
                referenceId = "receipt-42",
                notes = "سداد جزئي",
                userId = ownerId,
                userName = "مالك اختبار"
            )
        )

        val customerCsv = java.io.File(engine.exportCustomersCsv()).readText(Charsets.UTF_8)
        val expenseCsv = java.io.File(engine.exportExpensesCsv()).readText(Charsets.UTF_8)
        val debtCsv = java.io.File(engine.exportDebtTransactionsCsv()).readText(Charsets.UTF_8)

        assertTrue("Customer CSV should have a UTF-8 BOM", customerCsv.startsWith("\uFEFF"))
        assertTrue(customerCsv.contains("csv-customer"))
        assertTrue(customerCsv.contains("125.0"))
        assertTrue(customerCsv.contains("\"\"اختبار\"\""))
        assertTrue(customerCsv.contains("ملاحظات, فيها فاصلة"))

        assertTrue("Expense CSV should have a UTF-8 BOM", expenseCsv.startsWith("\uFEFF"))
        assertTrue(expenseCsv.contains("csv-expense"))
        assertTrue(expenseCsv.contains("42.5"))
        assertTrue(expenseCsv.contains("شراء, مواد خام"))

        assertTrue("Debt CSV should have a UTF-8 BOM", debtCsv.startsWith("\uFEFF"))
        assertTrue(debtCsv.contains("csv-debt-tx"))
        assertTrue(debtCsv.contains("receipt-42"))
        assertTrue(debtCsv.contains("سداد جزئي"))
    }

}
