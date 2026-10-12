package com.example.gateway

import android.content.Context
import com.example.data.engine.GatewaySyncEngine
import com.example.data.engine.IngestResult
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class GatewayServer(
    private val db: AppDatabase,
    private val walletMatchingEngine: WalletMatchingEngine,
    val port: Int = 8080,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val gatewaySyncEngine: GatewaySyncEngine? = null,
    private val context: Context? = null
) {
    private var serverSocket: ServerSocket? = null
    @Volatile
    var isRunning: Boolean = false
        private set

    // Replay protection for read/retry operations during this server lifetime.
    // SMS ingestion also has durable idempotency in WalletMatchingEngine's Room records.
    private val seenRequestIds = ConcurrentHashMap<String, Long>()

    fun start(): Boolean {
        if (isRunning) return true
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(java.net.InetSocketAddress(port))
            serverSocket = socket
            isRunning = true
            coroutineScope.launch { listen() }
            true
        } catch (_: Exception) {
            isRunning = false
            false
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private suspend fun listen() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val client = serverSocket?.accept() ?: break
                launch { handleClient(client) }
            } catch (_: Exception) {
                if (!isRunning) break
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val input = socket.getInputStream()
            val headerBlock = readHeaderBlock(input)
            if (headerBlock == null) {
                writeResponse(socket, 400, JSONObject().put("error", "Malformed HTTP headers").toString())
                return@withContext
            }

            val headerLines = String(headerBlock, Charsets.ISO_8859_1).split("\r\n")
            val requestParts = headerLines.firstOrNull().orEmpty().split(" ", limit = 3)
            if (requestParts.size < 2) {
                writeResponse(socket, 400, JSONObject().put("error", "Malformed request line").toString())
                return@withContext
            }

            val method = requestParts[0].uppercase()
            val path = requestParts[1].substringBefore("?")
            val headers = mutableMapOf<String, String>()
            for (line in headerLines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            if (headers.containsKey("transfer-encoding")) {
                writeResponse(socket, 400, JSONObject().put("error", "Transfer-Encoding is not supported").toString())
                return@withContext
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                writeResponse(socket, 413, JSONObject().put("error", "Request body too large").toString())
                return@withContext
            }

            val bodyBytes = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val count = input.read(bodyBytes, offset, contentLength - offset)
                if (count < 0) {
                    writeResponse(socket, 400, JSONObject().put("error", "Incomplete request body").toString())
                    return@withContext
                }
                offset += count
            }
            val body = String(bodyBytes, Charsets.UTF_8)
            val (status, payload) = processRequest(method, path, body, headers)
            writeResponse(socket, status, payload)
        } catch (_: Exception) {
            try {
                writeResponse(socket, 500, JSONObject().put("error", "Gateway request failed").toString())
            } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readHeaderBlock(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val marker = intArrayOf(13, 10, 13, 10)
        var matched = 0
        while (output.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return null
            output.write(next)
            if (next == marker[matched]) {
                matched++
                if (matched == marker.size) {
                    val bytes = output.toByteArray()
                    return bytes.copyOf(bytes.size - marker.size)
                }
            } else {
                matched = if (next == marker[0]) 1 else 0
            }
        }
        return null
    }

    private fun writeResponse(socket: Socket, status: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val output = socket.getOutputStream()
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private suspend fun processRequest(
        method: String,
        path: String,
        body: String,
        headers: Map<String, String>
    ): Pair<Int, String> {
        val configuredToken = getConfiguredToken()
        val configuredDeviceId = db.settingsDao().getSetting("gateway_device_id")?.trim().orEmpty()
        val authFailure = GatewayRequestAuthenticator.authenticate(
            method = method,
            path = path,
            headers = headers,
            body = body,
            configuredToken = configuredToken,
            configuredDeviceId = configuredDeviceId
        )
        if (authFailure != null) {
            return authFailure.statusCode to JSONObject().put("error", authFailure.message).toString()
        }

        val deviceId = headers[GatewayRequestSigner.DEVICE_ID_HEADER].orEmpty()
            .ifBlank { headers["x-gateway-device-id"].orEmpty() }
        val eventId = headers[GatewayRequestSigner.EVENT_ID_HEADER].orEmpty()
            .ifBlank { headers["idempotency-key"].orEmpty() }
        if (path != "/api/sms/ingest" && !claimRequestId(deviceId, eventId)) {
            return 409 to JSONObject().put("error", "Request replay detected").toString()
        }

        return when (path) {
            "/api/status" -> {
                val openShift = db.shiftDao().getCurrentOpenShiftSync()
                val pending = db.gatewayQueueDao().getPendingQueueItemsSync().size
                val json = JSONObject()
                    .put("status", "ONLINE")
                    .put("server", "POWER FEUL POS GATEWAY")
                    .put("version", "1.0")
                    .put("hasOpenShift", openShift != null)
                    .put("shiftNumber", openShift?.shiftNumber ?: 0)
                    .put("cashierName", openShift?.userName ?: "لا يوجد")
                    .put("pendingQueue", pending)
                    .put("timestamp", System.currentTimeMillis())
                200 to json.toString()
            }

            "/api/wallets" -> {
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val wallets = db.walletDao().getAllWalletsSync()
                    .filter { it.code in officialCodes && it.enabled }
                val array = JSONArray()
                wallets.forEach { wallet ->
                    array.put(JSONObject().put("code", wallet.code).put("name", wallet.name).put("enabled", wallet.enabled))
                }
                200 to JSONObject().put("wallets", array).toString()
            }

            "/api/transactions" -> {
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val transactions = db.walletDao().getAllTransactionsSync()
                    .filter { it.walletCode in officialCodes }
                    .take(50)
                val array = JSONArray()
                transactions.forEach { tx ->
                    array.put(
                        JSONObject()
                            .put("id", tx.id)
                            .put("walletCode", tx.walletCode)
                            .put("amount", tx.amount)
                            .put("transactionId", tx.transactionId)
                            .put("sender", tx.sender)
                            .put("status", tx.status.name)
                            .put("cashierName", tx.cashierName ?: "")
                            .put("receivedAt", tx.receivedAt)
                    )
                }
                200 to JSONObject().put("transactions", array).toString()
            }

            "/api/sms/ingest" -> {
                val sender = extractJsonField(body, "sender")
                val smsBody = extractJsonField(body, "body")
                val receivedAt = extractJsonLong(body, "receivedAt") ?: System.currentTimeMillis()
                if (sender.isBlank() || smsBody.isBlank()) {
                    400 to JSONObject().put("error", "sender and body are required").toString()
                } else {
                    when (val result = walletMatchingEngine.ingestSms(
                        sender = sender,
                        body = smsBody,
                        receivedAt = receivedAt,
                        sourceSmsId = eventId
                    )) {
                        is IngestResult.Created -> 200 to JSONObject()
                            .put("success", true)
                            .put("result", "CREATED")
                            .put("txId", result.tx.id)
                            .put("amount", result.tx.amount)
                            .put("wallet", result.tx.walletCode)
                            .put("receivedAt", result.tx.receivedAt)
                            .put("outOfShift", result.isOutOfShift)
                            .toString()
                        is IngestResult.Duplicate -> 200 to JSONObject()
                            .put("success", true)
                            .put("result", "DUPLICATE")
                            .put("reason", result.reason)
                            .put("txId", result.tx.id)
                            .toString()
                        is IngestResult.Unmatched -> 200 to JSONObject()
                            .put("success", false)
                            .put("result", "UNMATCHED")
                            .put("smsId", result.sms.id)
                            .toString()
                    }
                }
            }

            "/api/queue/retry" -> {
                val syncEngine = gatewaySyncEngine
                    ?: return 503 to JSONObject().put("error", "Gateway retry engine is not attached").toString()
                syncEngine.retryAllNow()
                val pending = db.gatewayQueueDao().getPendingQueueItemsSync().size
                200 to JSONObject()
                    .put("success", true)
                    .put("retried", true)
                    .put("pendingCount", pending)
                    .toString()
            }

            else -> 404 to JSONObject().put("error", "Not Found").toString()
        }
    }

    private suspend fun getConfiguredToken(): String {
        val appContext = context ?: return ""
        val store = GatewaySecretStore(appContext)
        val secured = store.getToken()
        if (!secured.isNullOrBlank()) return secured
        // Fail closed if an encrypted token exists but cannot be decrypted.
        if (store.hasTokenRecord()) return ""

        // One-time migration of an old plaintext token into Android Keystore storage.
        val legacy = db.settingsDao().getSetting("gateway_token")?.trim().orEmpty()
        if (legacy.length >= GatewaySecretStore.MIN_TOKEN_LENGTH && store.saveToken(legacy)) {
            db.settingsDao().deleteSetting("gateway_token")
            return legacy
        }
        return ""
    }

    private fun claimRequestId(deviceId: String, eventId: String): Boolean {
        val now = System.currentTimeMillis()
        for ((key, timestamp) in seenRequestIds.entries) {
            if (now - timestamp > GatewayRequestSigner.MAX_CLOCK_SKEW_MS * 2) {
                seenRequestIds.remove(key, timestamp)
            }
        }
        if (seenRequestIds.size > MAX_CACHED_REQUESTS) seenRequestIds.clear()
        return seenRequestIds.putIfAbsent("$deviceId:$eventId", now) == null
    }

    private fun extractJsonField(json: String, key: String): String {
        val pattern = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = pattern.find(json) ?: return ""
        return match.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = Regex(""""$key"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    companion object {
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_BODY_BYTES = 1024 * 1024
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val MAX_CACHED_REQUESTS = 4096
    }
}
