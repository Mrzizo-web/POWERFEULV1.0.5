package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DatabaseSeederAndroidTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshDatabaseSeedsAnOwnerVisibleToLoginFlow() = runBlocking {
        DatabaseSeeder.seedIfNeeded(db)
        val activeUsers = db.userDao().getAllActiveUsers().first()
        val owner = activeUsers.firstOrNull { it.id == "user-owner-ziad" }
        assertTrue("Owner should be visible after seeding", owner != null)
        assertTrue("Fresh owner account must not contain a default PIN hash", owner!!.pinHash.isBlank())
        assertTrue("Fresh owner account must not contain a default PIN salt", owner.pinSalt.isBlank())
    }

    @Test
    fun seedingDoesNotReactivateDisabledOwnerOrOverwriteCredential() = runBlocking {
        val customCredential = "preserve-existing-credential"
        val hash = PasswordHasher.DEFAULT.hash(customCredential)
        db.userDao().insertUser(
            UserEntity(
                id = "user-owner-ziad",
                name = "مالك اختبار",
                username = "owner_test",
                pinHash = hash.hashHex,
                pinSalt = hash.saltHex,
                role = UserRole.OWNER,
                isActive = false
            )
        )

        DatabaseSeeder.seedIfNeeded(db)

        val owner = db.userDao().getUserById("user-owner-ziad")!!
        assertFalse("Seeding must not re-enable a deliberately disabled owner", owner.isActive)
        assertEquals(hash.hashHex, owner.pinHash)
        assertEquals(hash.saltHex, owner.pinSalt)
        assertEquals(1, db.userDao().countUsers())
        assertTrue(PasswordHasher.DEFAULT.verify(customCredential, owner.pinSalt, owner.pinHash))
    }
}
