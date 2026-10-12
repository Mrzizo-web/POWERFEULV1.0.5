package com.example

import com.example.gateway.GatewayRequestSigner
import org.junit.Assert.*
import org.junit.Test

class GatewayRequestSignerTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun signatureVerifiesForExactRequest() {
        val method = "POST"
        val path = "/api/sms/ingest"
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"sender":"JEEB","rawMessage":"اضيف 5000 ر.ي"}"""
        val signature = GatewayRequestSigner.sign(token, method, path, timestamp, eventId, deviceId, body)

        assertTrue(
            GatewayRequestSigner.verify(
                token, method, path, timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong()
            )
        )
    }

    @Test
    fun signatureRejectsChangedBodyEndpointMethodAndExpiredTimestamp() {
        val method = "POST"
        val path = "/api/sms/ingest"
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"amount":5000}"""
        val signature = GatewayRequestSigner.sign(token, method, path, timestamp, eventId, deviceId, body)

        assertFalse(
            GatewayRequestSigner.verify(
                token, method, path, timestamp, eventId, deviceId, """{"amount":5001}""", signature,
                nowMillis = timestamp.toLong()
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, "GET", "/api/status", timestamp, eventId, deviceId, "", signature,
                nowMillis = timestamp.toLong()
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, method, "/api/wallets", timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong()
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, method, path, timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong() + GatewayRequestSigner.MAX_CLOCK_SKEW_MS + 1
            )
        )
    }

    @Test
    fun timestampExtremesAreRejectedWithoutOverflow() {
        val method = "POST"
        val path = "/api/sms/ingest"
        val eventId = "event-extreme"
        val deviceId = "GATEWAY_DEV_01"
        val body = "{}"
        val signature = GatewayRequestSigner.sign(token, method, path, "0", eventId, deviceId, body)
        assertFalse(
            GatewayRequestSigner.verify(
                token, method, path, Long.MIN_VALUE.toString(), eventId, deviceId, body, signature, nowMillis = 1000L
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, method, path, Long.MAX_VALUE.toString(), eventId, deviceId, body, signature, nowMillis = 1000L
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, method, path, "-1", eventId, deviceId, body, signature, nowMillis = 1000L
            )
        )
    }
}
