package com.example.gateway

import com.example.data.engine.IngestResult
import com.example.data.engine.GatewaySyncEngine
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class GatewayServer(
    private val db: AppDatabase,
    private val walletMatchingEngine: WalletMatchingEngine,
    val port: Int = 8080,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val gatewaySyncEngine: GatewaySyncEngine? = null
) {
    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set

    fun start(): Boolean {
        if (isRunning) return true
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true
            coroutineScope.launch {
                listen()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            false
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private suspend fun listen() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                launch {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                if (!isRunning) break
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                    if (key == "content-length") contentLength = value.toIntOrNull() ?: 0
                }
            }

            // Read body if POST
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val charBuf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(charBuf, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                bodyBuilder.append(charBuf, 0, readTotal)
            }
            val body = bodyBuilder.toString()

            val (statusCode, responseJson) = processRequest(method, path, body, headers)

            out.write("HTTP/1.1 $statusCode OK\r\n")
            out.write("Content-Type: application/json; charset=UTF-8\r\n")
            out.write("Access-Control-Allow-Origin: *\r\n")
            out.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            out.write("Access-Control-Allow-Headers: Content-Type, X-Gateway-Token, X-Gateway-Device-Id, X-Gateway-Event-Id, Idempotency-Key\r\n")
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            out.write("Content-Length: ${bytes.size}\r\n")
            out.write("Connection: close\r\n\r\n")
            out.write(responseJson)
            out.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private suspend fun processRequest(method: String, path: String, body: String, headers: Map<String, String>): Pair<Int, String> {
        if (method == "OPTIONS") {
            return Pair(200, "{}")
        }

        return when {
            path == "/api/status" -> {
                val openShift = db.shiftDao().getCurrentOpenShiftSync()
                val pendingQueueCount = db.gatewayQueueDao().getPendingQueueItemsSync().size
                val json = """
                    {
                        "status": "ONLINE",
                        "server": "POWER FEUL POS GATEWAY",
                        "version": "1.0",
                        "hasOpenShift": ${openShift != null},
                        "shiftNumber": "${openShift?.shiftNumber ?: 0}",
                        "cashierName": "${openShift?.userName ?: "لا يوجد"}",
                        "pendingQueue": $pendingQueueCount,
                        "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent()
                Pair(200, json)
            }

            path == "/api/wallets" -> {
                // Gateway exposes only the three officially supported wallets.
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val wallets = db.walletDao().getAllWalletsSync()
                    .filter { it.code in officialCodes && it.enabled }
                val items = wallets.joinToString(",") { w ->
                    """{"code":"${w.code}","name":"${w.name}","enabled":${w.enabled}}"""
                }
                Pair(200, """{"wallets":[$items]}""")
            }

            path == "/api/transactions" -> {
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val txs = db.walletDao().getAllTransactionsSync()
                    .filter { it.walletCode in officialCodes }
                    .take(50)
                val items = txs.joinToString(",") { tx ->
                    """{
                        "id":"${tx.id}",
                        "walletCode":"${tx.walletCode}",
                        "amount":${tx.amount},
                        "transactionId":"${tx.transactionId}",
                        "sender":"${tx.sender}",
                        "status":"${tx.status.name}",
                        "cashierName":"${tx.cashierName ?: ""}",
                        "receivedAt":${tx.receivedAt}
                    }""".trimIndent().replace("\n", "")
                }
                Pair(200, """{"transactions":[$items]}""")
            }

            path == "/api/sms/ingest" && method == "POST" -> {
                val configuredToken = db.settingsDao().getSetting("gateway_token")?.trim().orEmpty()
                val configuredDeviceId = db.settingsDao().getSetting("gateway_device_id")?.trim().orEmpty()
                val suppliedToken = headers["x-gateway-token"].orEmpty()
                val suppliedDeviceId = headers[GatewayRequestSigner.DEVICE_ID_HEADER].orEmpty().ifBlank { headers["x-gateway-device-id"].orEmpty() }
                val eventId = headers[GatewayRequestSigner.EVENT_ID_HEADER].orEmpty().ifBlank { headers["idempotency-key"].orEmpty() }
                val timestamp = headers[GatewayRequestSigner.TIMESTAMP_HEADER].orEmpty()
                val signature = headers[GatewayRequestSigner.SIGNATURE_HEADER].orEmpty()

                val isHmacAuth = signature.isNotBlank() && timestamp.isNotBlank()
                val isTokenAuth = suppliedToken.isNotBlank() && suppliedToken == configuredToken

                if (configuredToken.isBlank() || configuredDeviceId.isBlank()) {
                    Pair(503, """{"error":"Gateway security is not configured"}""")
                } else if (eventId.isBlank()) {
                    Pair(400, """{"error":"X-Gateway-Event-Id or Idempotency-Key is required"}""")
                } else if (suppliedDeviceId != configuredDeviceId) {
                    Pair(401, """{"error":"Invalid gateway device ID"}""")
                } else if (!isHmacAuth && !isTokenAuth) {
                    Pair(401, """{"error":"Invalid gateway credentials or missing signature"}""")
                } else if (isHmacAuth && !GatewayRequestSigner.verify(configuredToken, timestamp, eventId, suppliedDeviceId, body, signature)) {
                    Pair(401, """{"error":"Invalid HMAC signature or expired timestamp"}""")
                } else {
                    val sender = extractJsonField(body, "sender")
                    val smsBody = extractJsonField(body, "body")
                    val receivedAt = extractJsonLong(body, "receivedAt") ?: System.currentTimeMillis()

                    if (sender.isBlank() || smsBody.isBlank()) {
                        Pair(400, """{"error":"sender and body required"}""")
                    } else {
                        val result = walletMatchingEngine.ingestSms(
                            sender = sender,
                            body = smsBody,
                            receivedAt = receivedAt,
                            sourceSmsId = eventId
                        )
                        val resultJson = when (result) {
                            is IngestResult.Created -> {
                                """{"success":true,"result":"CREATED","txId":"${result.tx.id}","amount":${result.tx.amount},"wallet":"${result.tx.walletCode}","receivedAt":${result.tx.receivedAt},"outOfShift":${result.isOutOfShift}}"""
                            }
                            is IngestResult.Duplicate -> {
                                """{"success":true,"result":"DUPLICATE","reason":"${result.reason}","txId":"${result.tx.id}"}"""
                            }
                            is IngestResult.Unmatched -> {
                                """{"success":false,"result":"UNMATCHED","smsId":"${result.sms.id}"}"""
                            }
                        }
                        Pair(200, resultJson)
                    }
                }
            }

            path == "/api/queue/retry" && method == "POST" -> {
                gatewaySyncEngine?.retryAllNow()
                val pending = db.gatewayQueueDao().getPendingQueueItemsSync().size
                Pair(200, """{"success":true,"retried":true,"pendingCount":$pending}""")
            }

            else -> Pair(404, """{"error":"Not Found"}""")
        }
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
}
