package com.example.data.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreSecretPolicyTest {
    @Test
    fun excludesAllDeviceBoundCredentialsFromPortableBackups() {
        listOf(
            "gateway_token",
            "gateway_shared_secret",
            "gateway_hmac_key",
            "gemini_api_key",
            "GEMINI_API_KEY"
        ).forEach { key ->
            assertTrue("$key is device-bound", BackupRestoreEngine.isDeviceBoundSecretKey(key))
        }
    }

    @Test
    fun doesNotClassifyBusinessSettingsAsDeviceBoundSecrets() {
        listOf("auto_lock_minutes", "gateway_device_id", "theme", "store_name").forEach { key ->
            assertFalse("$key is not a device-bound credential", BackupRestoreEngine.isDeviceBoundSecretKey(key))
        }
    }
}
