package com.example.gateway

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Request authentication for the local Wi-Fi gateway.
 * The shared token is used as an HMAC key and is never sent in an HTTP header.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "x-gateway-timestamp"
    const val SIGNATURE_HEADER = "x-gateway-signature"
    const val EVENT_ID_HEADER = "x-gateway-event-id"
    const val DEVICE_ID_HEADER = "x-gateway-device-id"
    const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L

    fun sign(token: String, timestamp: String, eventId: String, deviceId: String, body: String): String {
        val canonical = "$timestamp\n$eventId\n$deviceId\n$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(
        token: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (token.length < 32 || timestamp.isBlank() || eventId.isBlank() || deviceId.isBlank()) return false
        val timestampMillis = timestamp.toLongOrNull() ?: return false
        if (kotlin.math.abs(nowMillis - timestampMillis) > MAX_CLOCK_SKEW_MS) return false
        if (!signature.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = sign(token, timestamp, eventId, deviceId, body)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}
