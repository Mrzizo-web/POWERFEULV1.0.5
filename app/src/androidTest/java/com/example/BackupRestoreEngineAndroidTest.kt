package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.BackupRestoreEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.UserEntity
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
}
