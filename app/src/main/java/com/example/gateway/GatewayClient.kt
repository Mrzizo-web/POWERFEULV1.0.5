package com.example.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object GatewayClient {

    /**
     * Deliberately fail closed for legacy unsigned callers.
     * Use the overload that supplies the shared HMAC key and authorized device ID.
     */
    suspend fun checkGatewayStatus(host: String, port: Int = 8080): Result<String> =
        Result.failure(IllegalStateException("Gateway HMAC token and device ID are required"))

    suspend fun checkGatewayStatus(
        host: String,
        token: String,
        deviceId: String,
        port: Int = 8080
    ): Result<String> = withContext(Dispatchers.IO) {
        signedRequest(
            url = "http://$host:$port/api/status",
            method = "GET",
            token = token,
            deviceId = deviceId,
            eventId = UUID.randomUUID().toString(),
            body = ""
        )
    }

    /**
     * Unsigned SMS sends are not allowed. Keep the old signature source-compatible
     * while making it fail closed so callers must move to the authenticated overload.
     */
    suspend fun sendRemoteSms(
        host: String,
        port: Int = 8080,
        sender: String,
        body: String
    ): Result<String> =
        Result.failure(IllegalStateException("Gateway HMAC token, device ID, and stable event ID are required"))

    suspend fun sendRemoteSms(
        host: String,
        sender: String,
        body: String,
        token: String,
        deviceId: String,
        eventId: String,
        receivedAt: Long = System.currentTimeMillis(),
        port: Int = 8080
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("sender", sender)
            .put("body", body)
            .put("receivedAt", receivedAt)
            .toString()
        signedRequest(
            url = "http://$host:$port/api/sms/ingest",
            method = "POST",
            token = token,
            deviceId = deviceId,
            eventId = eventId,
            body = payload
        )
    }

    private fun signedRequest(
        url: String,
        method: String,
        token: String,
        deviceId: String,
        eventId: String,
        body: String
    ): Result<String> {
        if (token.length < GatewaySecretStore.MIN_TOKEN_LENGTH) {
            return Result.failure(IllegalArgumentException("Gateway HMAC token must be at least 32 characters"))
        }
        if (deviceId.isBlank() || eventId.isBlank()) {
            return Result.failure(IllegalArgumentException("Gateway device ID and event ID are required"))
        }

        var connection: HttpURLConnection? = null
        return try {
            val timestamp = System.currentTimeMillis().toString()
            val requestPath = URL(url).path
            val signature = GatewayRequestSigner.sign(
                token = token,
                method = method,
                path = requestPath,
                timestamp = timestamp,
                eventId = eventId,
                deviceId = deviceId,
                body = body
            )
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("X-Gateway-Device-Id", deviceId)
                setRequestProperty("X-Gateway-Event-Id", eventId)
                setRequestProperty("X-Gateway-Timestamp", timestamp)
                setRequestProperty("X-Gateway-Signature", signature)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (method == "POST") doOutput = true
            }
            if (method == "POST") {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) Result.success(response)
            else Result.failure(IllegalStateException("Gateway returned HTTP $status: $response"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
