package com.example.gateway

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC authentication for every request reaching the local Wi-Fi gateway.
 * Method and path are part of the canonical bytes to prevent reusing a
 * valid signature against a different endpoint.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "x-gateway-timestamp"
    const val SIGNATURE_HEADER = "x-gateway-signature"
    const val EVENT_ID_HEADER = "x-gateway-event-id"
    const val DEVICE_ID_HEADER = "x-gateway-device-id"
    const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L

    fun sign(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String
    ): String {
        require(token.length >= 32) { "Gateway HMAC key must contain at least 32 characters" }
        require(method.isNotBlank() && path.startsWith("/")) { "HTTP method and absolute path are required" }
        val canonical = listOf(
            method.uppercase(),
            path,
            timestamp,
            eventId,
            deviceId,
            body
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (token.length < 32 || timestamp.isBlank() || eventId.isBlank() || deviceId.isBlank()) return false
        if (method.isBlank() || !path.startsWith("/")) return false
        val timestampMillis = timestamp.toLongOrNull() ?: return false
        if (timestampMillis < 0L || nowMillis < 0L) return false
        val delta = if (timestampMillis >= nowMillis) {
            timestampMillis - nowMillis
        } else {
            nowMillis - timestampMillis
        }
        if (delta < 0L || delta > MAX_CLOCK_SKEW_MS) return false
        if (!signature.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = sign(token, method, path, timestamp, eventId, deviceId, body)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}
