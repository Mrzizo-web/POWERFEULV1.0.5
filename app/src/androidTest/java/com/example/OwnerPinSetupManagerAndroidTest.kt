package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole as DomainUserRole
import com.example.security.OwnerPinSetupManager
import com.example.security.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OwnerPinSetupManagerAndroidTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.userDao().insertUser(
                UserEntity(
                    id = OwnerPinSetupManager.INITIAL_OWNER_ID,
                    name = "مالك اختبار",
                    username = "owner_setup_test",
                    role = DomainUserRole.OWNER,
                    pinHash = "",
                    pinSalt = ""
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun initialPinIsHashedSaltedAndAudited() = runBlocking {
        val manager = OwnerPinSetupManager(db)
        val configured = manager.setInitialPin(OwnerPinSetupManager.INITIAL_OWNER_ID, "4826")

        assertNotEquals("4826", configured.pinHash)
        assertTrue(configured.pinSalt.isNotBlank())
        assertTrue(PasswordHasher.DEFAULT.verify("4826", configured.pinSalt, configured.pinHash))
        assertFalse(PasswordHasher.DEFAULT.verify("4827", configured.pinSalt, configured.pinHash))
        assertTrue(db.auditLogDao().getRecentLogs().first().any { it.action == "INITIAL_OWNER_PIN_SET" })
    }

    @Test
    fun refusesToReplaceAnExistingOwnerPin() = runBlocking {
        val manager = OwnerPinSetupManager(db)
        val first = manager.setInitialPin(OwnerPinSetupManager.INITIAL_OWNER_ID, "4826")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.setInitialPin(OwnerPinSetupManager.INITIAL_OWNER_ID, "5937") }
        }
        val stored = db.userDao().getUserById(OwnerPinSetupManager.INITIAL_OWNER_ID)!!
        assertTrue(PasswordHasher.DEFAULT.verify("4826", stored.pinSalt, stored.pinHash))
        assertFalse(PasswordHasher.DEFAULT.verify("5937", stored.pinSalt, stored.pinHash))
        assertTrue(first.pinHash == stored.pinHash)
    }

    @Test
    fun rejectsInvalidPinAndNonOwnerIds() = runBlocking {
        val manager = OwnerPinSetupManager(db)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.setInitialPin(OwnerPinSetupManager.INITIAL_OWNER_ID, "12ab") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.setInitialPin("some-other-user", "4826") }
        }
        val owner = db.userDao().getUserById(OwnerPinSetupManager.INITIAL_OWNER_ID)!!
        assertTrue(owner.pinHash.isBlank())
        assertTrue(owner.pinSalt.isBlank())
    }
}
