package com.example.gateway

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CafeteriaSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Debug-build-only fixture used by the cross-app emulator integration test.
 * It is excluded from release variants and protected by a signature permission.
 */
class DebugGatewayIntegrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stop = intent.getBooleanExtra(EXTRA_STOP, false)
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)

        Thread {
            val configured = runCatching {
                if (!stop) {
                    require(token.length >= GatewaySecretStore.MIN_TOKEN_LENGTH) {
                        "Integration test token is too short"
                    }
                    require(deviceId.isNotBlank()) { "Integration test device ID is missing" }
                    require(port in 1024..65535) { "Integration test port is invalid" }

                    runBlocking(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(applicationContext)
                        db.settingsDao().setSetting(
                            CafeteriaSettingEntity(
                                key = "gateway_device_id",
                                value = deviceId
                            )
                        )
                        check(GatewaySecretStore(applicationContext).saveToken(token)) {
                            "Unable to store test token"
                        }
                    }
                }
            }.isSuccess

            Handler(Looper.getMainLooper()).post {
                if (configured) {
                    if (stop) {
                        GatewayService.stopGateway(applicationContext)
                    } else {
                        GatewayService.startGateway(applicationContext, port)
                    }
                    setResult(RESULT_OK)
                } else {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_TOKEN = "debug_gateway_integration_token"
        const val EXTRA_DEVICE_ID = "debug_gateway_integration_device_id"
        const val EXTRA_PORT = "debug_gateway_integration_port"
        const val EXTRA_STOP = "debug_gateway_integration_stop"
        const val PERMISSION = "com.aistudio.powerfeulpos.wzkmqy.permission.DEBUG_GATEWAY_INTEGRATION"
        const val DEFAULT_PORT = 18080
    }
}
