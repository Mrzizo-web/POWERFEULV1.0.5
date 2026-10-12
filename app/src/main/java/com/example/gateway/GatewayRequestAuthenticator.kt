package com.example.gateway

data class GatewayAuthFailure(val statusCode: Int, val message: String)

/**
 * Shared policy for every HTTP endpoint exposed by the POS gateway.
 * A static token is never accepted as a substitute for a request signature.
 */
object GatewayRequestAuthenticator {
    fun authenticate(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        configuredToken: String,
        configuredDeviceId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): GatewayAuthFailure? {
        val expectedMethod = when (path) {
            "/api/status", "/api/wallets", "/api/transactions" -> "GET"
            "/api/sms/ingest", "/api/queue/retry" -> "POST"
            else -> return GatewayAuthFailure(404, "Not Found")
        }
        if (!method.equals(expectedMethod, ignoreCase = true)) {
            return GatewayAuthFailure(405, "Method Not Allowed")
        }
        if (configuredToken.isBlank() || configuredDeviceId.isBlank()) {
            return GatewayAuthFailure(503, "Gateway security is not configured")
        }

        val eventId = headers[GatewayRequestSigner.EVENT_ID_HEADER].orEmpty()
            .ifBlank { headers["idempotency-key"].orEmpty() }
        val timestamp = headers[GatewayRequestSigner.TIMESTAMP_HEADER].orEmpty()
        val signature = headers[GatewayRequestSigner.SIGNATURE_HEADER].orEmpty()
        val deviceId = headers[GatewayRequestSigner.DEVICE_ID_HEADER].orEmpty()
            .ifBlank { headers["x-gateway-device-id"].orEmpty() }

        if (eventId.isBlank() || timestamp.isBlank() || signature.isBlank() || deviceId.isBlank()) {
            return GatewayAuthFailure(401, "Missing mandatory HMAC authentication headers")
        }
        if (deviceId != configuredDeviceId) {
            return GatewayAuthFailure(401, "Invalid gateway device ID")
        }
        if (!GatewayRequestSigner.verify(
                token = configuredToken,
                method = method,
                path = path,
                timestamp = timestamp,
                eventId = eventId,
                deviceId = deviceId,
                body = body,
                signature = signature,
                nowMillis = nowMillis
            )
        ) {
            return GatewayAuthFailure(401, "Invalid HMAC signature or expired timestamp")
        }
        return null
    }
}
