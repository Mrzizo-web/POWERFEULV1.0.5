package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSeederOwnerRecoveryAndroidTest {
    @Test
    fun reactivatesOnlyKnownOwnerWhenNoActiveUsersRemainAndPreservesCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val owner = UserEntity(
                id = "user-owner-ziad",
                name = "Local Owner",
                username = "owner",
                pinHash = "existing-hash",
                pinSalt = "existing-salt",
                role = UserRole.OWNER,
                isActive = false
            )
            db.userDao().insertUser(owner)
            db.userDao().insertUser(
                UserEntity(
                    id = "inactive-cashier",
                    name = "Inactive Cashier",
                    username = "cashier",
                    role = UserRole.CASHIER,
                    isActive = false
                )
            )

            DatabaseSeeder.seedIfNeeded(db)

            val recoveredOwner = db.userDao().getUserById(owner.id)
            assertNotNull(recoveredOwner)
            assertTrue(recoveredOwner!!.isActive)
            assertEquals(owner.pinHash, recoveredOwner.pinHash)
            assertEquals(owner.pinSalt, recoveredOwner.pinSalt)
            assertFalse(db.userDao().getUserById("inactive-cashier")!!.isActive)
        } finally {
            db.close()
        }
    }

    @Test
    fun doesNotReactivateOwnerWhenAnotherActiveUserExists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val owner = UserEntity(
                id = "user-owner-ziad",
                name = "Local Owner",
                username = "owner",
                pinHash = "existing-hash",
                pinSalt = "existing-salt",
                role = UserRole.OWNER,
                isActive = false
            )
            db.userDao().insertUser(owner)
            db.userDao().insertUser(
                UserEntity(
                    id = "active-cashier",
                    name = "Active Cashier",
                    username = "active-cashier",
                    role = UserRole.CASHIER,
                    isActive = true
                )
            )

            DatabaseSeeder.seedIfNeeded(db)

            assertTrue(!db.userDao().getUserById(owner.id)!!.isActive)
        } finally {
            db.close()
        }
    }
}
