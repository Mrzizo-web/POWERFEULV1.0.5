package com.example.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.security.PasswordHasher

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new schema table for users (users_v2) matching version 2 exactly
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users_v2 (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                username TEXT NOT NULL,
                pinHash TEXT NOT NULL,
                pinSalt TEXT NOT NULL,
                role TEXT NOT NULL,
                phone TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                failedAttempts INTEGER NOT NULL,
                lockedUntil INTEGER,
                lastLoginAt INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. Read existing users, hash their plaintext pin if present, and insert into users_v2
        val cursor = db.query("SELECT id, name, username, pin, role, phone, isActive, createdAt FROM users")
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val username = cursor.getString(2)
                val rawPin = cursor.getString(3)
                val role = cursor.getString(4)
                val phone = cursor.getString(5) ?: ""
                val isActive = cursor.getInt(6)
                val createdAt = cursor.getLong(7)

                // Never create a shared PIN during migration. Valid legacy PINs
                // are immediately salted and hashed; empty legacy credentials stay
                // unconfigured and must be reset through an authorized flow.
                val credentials = if (rawPin.isNullOrBlank() || !rawPin.matches(Regex("[0-9]{4,6}"))) {
                    // Legacy credentials outside the supported 4-6 digit PIN policy
                    // must be reset; never migrate an unusable PIN and lock out the owner.
                    "" to ""
                } else {
                    val hashResult = PasswordHasher.DEFAULT.hash(rawPin)
                    hashResult.hashHex to hashResult.saltHex
                }

                db.execSQL(
                    """
                    INSERT INTO users_v2 (
                        id, name, username, pinHash, pinSalt, role, phone, isActive, 
                        failedAttempts, lockedUntil, lastLoginAt, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        id, name, username, credentials.first, credentials.second,
                        role, phone, isActive, createdAt, System.currentTimeMillis()
                    )
                )
            }
        } finally {
            cursor.close()
        }

        // 3. Drop the old table and rename the new table to users
        db.execSQL("DROP TABLE users")
        db.execSQL("ALTER TABLE users_v2 RENAME TO users")
    }
}


/**
 * Version 3 currently contains no schema changes relative to version 2.
 * Keep this explicit migration so existing version-2 databases can upgrade
 * without destructive migration.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ensures gateway_offline_queue exists for any v2 installation upgrading to v3
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS gateway_offline_queue (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                targetEndpoint TEXT NOT NULL,
                status TEXT NOT NULL,
                retryCount INTEGER NOT NULL,
                maxRetries INTEGER NOT NULL,
                lastAttemptAt INTEGER NOT NULL,
                nextAttemptAt INTEGER NOT NULL,
                errorMessage TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}


/**
 * Version 4 enforces durable SMS event idempotency. If a prior version already
 * recorded duplicate originalSmsId values, retain every financial row but clear
 * the idempotency link on all but one row before creating the unique index.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE wallet_transactions
            SET originalSmsId = NULL
            WHERE originalSmsId IS NOT NULL
              AND id NOT IN (
                  SELECT MIN(id)
                  FROM wallet_transactions
                  WHERE originalSmsId IS NOT NULL
                  GROUP BY originalSmsId
              )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_wallet_transactions_originalSmsId " +
                "ON wallet_transactions(originalSmsId)"
        )
    }
}
