package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.PowerAiEngine
import com.example.data.engine.PowerAiSecretStore
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CafeteriaSettingEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerAiSecretStoreAndroidTest {
    @Test
    fun apiKeyIsEncryptedAtRestAndLegacyPlaintextMigratesOnRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val legacyKey = "AIzaSyDUMMY_TEST_KEY_DO_NOT_USE_0123456789"
            db.settingsDao().setSetting(CafeteriaSettingEntity("gemini_api_key", legacyKey))
            val engine = PowerAiEngine(db)

            assertEquals(legacyKey, engine.getGeminiApiKey())
            val migrated = db.settingsDao().getSetting("gemini_api_key") ?: ""
            assertTrue(migrated.startsWith(PowerAiSecretStore.ENCRYPTED_PREFIX))
            assertFalse(migrated.contains(legacyKey))
            assertEquals(legacyKey, PowerAiSecretStore.decrypt(migrated))

            val updatedKey = "new-test-key-0123456789-abcdefghijklmnopqrstuvwxyz"
            engine.saveGeminiApiKey(updatedKey)
            val saved = db.settingsDao().getSetting("gemini_api_key") ?: ""
            assertTrue(saved.startsWith(PowerAiSecretStore.ENCRYPTED_PREFIX))
            assertFalse(saved.contains(updatedKey))
            assertEquals(updatedKey, engine.getGeminiApiKey())
        } finally {
            db.close()
        }
    }

    @Test
    fun responseParserHandlesEscapedTextAndMultipleParts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val engine = PowerAiEngine(db)
            val partOne = org.json.JSONObject().put("text", "السطر الأول\nعلامة \"اقتباس\"")
            val partTwo = org.json.JSONObject().put("text", "الجزء الثاني")
            val response = org.json.JSONObject()
                .put(
                    "candidates",
                    org.json.JSONArray().put(
                        org.json.JSONObject().put(
                            "content",
                            org.json.JSONObject().put(
                                "parts",
                                org.json.JSONArray().put(partOne).put(partTwo)
                            )
                        )
                    )
                )
                .toString()

            assertEquals(
                "السطر الأول\nعلامة \"اقتباس\"\nالجزء الثاني",
                engine.parseGeminiResponse(response)
            )
        } finally {
            db.close()
        }
    }
}
