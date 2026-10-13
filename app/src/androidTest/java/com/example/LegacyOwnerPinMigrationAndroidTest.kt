package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.migration.MIGRATION_1_2
import com.example.data.local.migration.MIGRATION_2_3
import com.example.data.local.migration.MIGRATION_3_4
import com.example.data.local.migration.MIGRATION_4_5
import com.example.security.OwnerPinSetupManager
import com.example.security.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LegacyOwnerPinMigrationAndroidTest {
    @Test
    fun unsupportedLegacyOwnerPinCanBeResetAndValidCashierPinIsPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "legacy-pin-migration-" + UUID.randomUUID().toString() + ".db"

        val bootstrap = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        // Mutate the same open SQLite database connection Room created. This avoids
        // path/connection discrepancies between Context database APIs in instrumentation.
        val raw = bootstrap.openHelper.writableDatabase
        raw.execSQL("DROP TABLE users")
        raw.execSQL(
            """
            CREATE TABLE users (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                username TEXT NOT NULL,
                pin TEXT,
                role TEXT NOT NULL,
                phone TEXT,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        raw.execSQL(
            "INSERT INTO users (id, name, username, pin, role, phone, isActive, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("user-owner-ziad", "مالك قديم", "ziad", "918273645", "OWNER", "", 1, 1000L)
        )
        raw.execSQL(
            "INSERT INTO users (id, name, username, pin, role, phone, isActive, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-employee", "موظف قديم", "cashier-old", "2468", "CASHIER", "", 1, 1001L)
        )
        raw.execSQL("PRAGMA user_version = 1")
        bootstrap.close()

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        try {
            val owner = kotlinx.coroutines.runBlocking {
                upgraded.userDao().getUserById("user-owner-ziad")
            }!!
            assertTrue("Nine-digit legacy owner PIN must be resettable", owner.pinHash.isBlank())
            assertTrue(owner.pinSalt.isBlank())

            val cashier = kotlinx.coroutines.runBlocking {
                upgraded.userDao().getUserById("legacy-employee")
            }!!
            assertTrue(cashier.pinHash.isNotBlank())
            assertTrue(cashier.pinSalt.isNotBlank())
            assertTrue(PasswordHasher.DEFAULT.verify("2468", cashier.pinSalt, cashier.pinHash))
            assertTrue("Database must finish on the current schema version", upgraded.openHelper.readableDatabase.version == 5)

            val configuredOwner = kotlinx.coroutines.runBlocking {
                OwnerPinSetupManager(upgraded).setInitialPin(owner.id, "583149")
            }
            assertTrue(PasswordHasher.DEFAULT.verify("583149", configuredOwner.pinSalt, configuredOwner.pinHash))
            assertFalse(PasswordHasher.DEFAULT.verify("918273645", configuredOwner.pinSalt, configuredOwner.pinHash))
        } finally {
            upgraded.close()
            context.deleteDatabase(dbName)
        }
    }
}
