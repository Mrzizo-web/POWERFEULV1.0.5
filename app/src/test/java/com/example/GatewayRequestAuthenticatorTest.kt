package com.example

import com.example.gateway.GatewayRequestAuthenticator
import com.example.gateway.GatewayRequestSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class GatewayRequestAuthenticatorTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val deviceId = "AUTHORIZED_GATEWAY_01"

    @Test
    fun everySensitiveEndpointRejectsTokenOnlyAndMissingHmac() {
        val routes = listOf(
            "GET" to "/api/status",
            "GET" to "/api/wallets",
            "GET" to "/api/transactions",
            "POST" to "/api/sms/ingest",
            "POST" to "/api/queue/retry"
        )
        for ((method, path) in routes) {
            val tokenOnly = mapOf(
                "x-gateway-token" to token,
                "x-gateway-device-id" to deviceId
            )
            val failure = GatewayRequestAuthenticator.authenticate(
                method, path, tokenOnly, "", token, deviceId
            )
            assertNotNull("$method $path accepted Token without HMAC", failure)
            assertEquals(401, failure?.statusCode)
        }
    }

    @Test
    fun signedRequestsAreAcceptedOnEverySupportedEndpoint() {
        val routes = listOf(
            "GET" to "/api/status",
            "GET" to "/api/wallets",
            "GET" to "/api/transactions",
            "POST" to "/api/sms/ingest",
            "POST" to "/api/queue/retry"
        )
        for ((method, path) in routes) {
            val body = if (method == "POST") "{}" else ""
            val timestamp = System.currentTimeMillis().toString()
            val eventId = UUID.randomUUID().toString()
            val signature = GatewayRequestSigner.sign(token, method, path, timestamp, eventId, deviceId, body)
            val headers = mapOf(
                GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
                GatewayRequestSigner.EVENT_ID_HEADER to eventId,
                GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
                GatewayRequestSigner.SIGNATURE_HEADER to signature
            )
            val failure = GatewayRequestAuthenticator.authenticate(
                method, path, headers, body, token, deviceId
            )
            assertNull("$method $path rejected a valid HMAC request: $failure", failure)
        }
    }

    @Test
    fun copiedSignatureCannotAuthorizeAnotherPathOrMethod() {
        val timestamp = System.currentTimeMillis().toString()
        val eventId = UUID.randomUUID().toString()
        val body = """{"sender":"gateway","body":"test"}"""
        val signature = GatewayRequestSigner.sign(
            token, "GET", "/api/status", timestamp, eventId, deviceId, ""
        )
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        assertEquals(
            401,
            GatewayRequestAuthenticator.authenticate(
                "GET", "/api/wallets", headers, "", token, deviceId
            )?.statusCode
        )
        assertEquals(
            401,
            GatewayRequestAuthenticator.authenticate(
                "POST", "/api/status", headers, body, token, deviceId
            )?.statusCode
        )
        assertEquals(
            401,
            GatewayRequestAuthenticator.authenticate(
                "POST", "/api/sms/ingest", headers, body, token, deviceId
            )?.statusCode
        )
    }

    @Test
    fun invalidDeviceBodyAndMethodAreRejected() {
        val timestamp = System.currentTimeMillis().toString()
        val eventId = UUID.randomUUID().toString()
        val body = """{"sender":"gateway","body":"test"}"""
        val signature = GatewayRequestSigner.sign(
            token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body
        )
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        assertEquals(
            401,
            GatewayRequestAuthenticator.authenticate(
                "POST", "/api/sms/ingest", headers, """{"sender":"attacker"}""", token, deviceId
            )?.statusCode
        )
        assertEquals(
            401,
            GatewayRequestAuthenticator.authenticate(
                "POST", "/api/sms/ingest", headers, body, token, "OTHER_DEVICE"
            )?.statusCode
        )
        assertEquals(
            405,
            GatewayRequestAuthenticator.authenticate(
                "POST", "/api/status", headers, body, token, deviceId
            )?.statusCode
        )
    }
}
