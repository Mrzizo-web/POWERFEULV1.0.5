package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.GatewaySyncEngine
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CafeteriaSettingEntity
import com.example.gateway.GatewayRequestSigner
import com.example.gateway.GatewaySecretStore
import com.example.gateway.GatewayServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import org.json.JSONObject
import java.util.UUID

class GatewayServerSecurityAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var server: GatewayServer
    private lateinit var token: String
    private lateinit var deviceId: String
    private var serverScope: CoroutineScope? = null
    private var syncScope: CoroutineScope? = null
    private var testPort: Int = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        deviceId = "AUTHORIZED_TEST_DEVICE"
        db.settingsDao().setSetting(CafeteriaSettingEntity("gateway_device_id", deviceId))
        assertTrue(GatewaySecretStore(context).saveToken(token))

        testPort = ServerSocket(0).use { it.localPort }
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val syncEngine = GatewaySyncEngine(db, syncScope!!)
        server = GatewayServer(
            db = db,
            walletMatchingEngine = WalletMatchingEngine(db),
            port = testPort,
            coroutineScope = serverScope!!,
            gatewaySyncEngine = syncEngine,
            context = context
        )
        assertTrue("Gateway server must start for integration test", server.start())
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
        serverScope?.cancel()
        syncScope?.cancel()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun httpServerRejectsUnsignedAccessToEveryProtectedEndpoint() {
        val routes = listOf(
            "GET" to "/api/status",
            "GET" to "/api/wallets",
            "GET" to "/api/transactions",
            "POST" to "/api/sms/ingest",
            "POST" to "/api/queue/retry"
        )
        for ((method, path) in routes) {
            val response = sendRequest(
                method = method,
                path = path,
                headers = mapOf(
                    "X-Gateway-Token" to token,
                    "X-Gateway-Device-Id" to deviceId
                ),
                body = if (method == "POST") "{}" else ""
            )
            assertTrue("$method $path should reject a Token-only request: $response", response.startsWith("HTTP/1.1 401"))
        }
    }

    @Test
    fun httpServerAcceptsValidHmacAndRejectsReplay() {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(token, "GET", "/api/status", timestamp, eventId, deviceId, "")
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        val accepted = sendRequest("GET", "/api/status", headers, "")
        assertTrue("Signed status request should succeed: $accepted", accepted.startsWith("HTTP/1.1 200"))
        val copiedToWallets = sendRequest("GET", "/api/wallets", headers, "")
        assertTrue("A status signature must not authorize /api/wallets: $copiedToWallets", copiedToWallets.startsWith("HTTP/1.1 401"))
        val replay = sendRequest("GET", "/api/status", headers, "")
        assertTrue("Replaying a signed request ID should be rejected: " + replay, replay.startsWith("HTTP/1.1 409"))

        // Restart with empty in-memory state. The same valid signed request is still
        // within its timestamp window but must be rejected by the persistent nonce store.
        server.stop()
        serverScope?.cancel()
        syncScope?.cancel()
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        server = GatewayServer(
            db = db,
            walletMatchingEngine = WalletMatchingEngine(db),
            port = testPort,
            coroutineScope = serverScope!!,
            gatewaySyncEngine = GatewaySyncEngine(db, syncScope!!, autoStartRetry = false),
            context = InstrumentationRegistry.getInstrumentation().targetContext
        )
        // Closing an accepted TCP connection can leave a short OS-level port-reuse
        // window on some emulator images. Retry the same port briefly and report the
        // actual bind exception if the listener cannot recover.
        val restartDeadline = System.currentTimeMillis() + 3_000L
        var restarted = false
        while (!restarted && System.currentTimeMillis() < restartDeadline) {
            restarted = server.start()
            if (!restarted) Thread.sleep(50L)
        }
        assertTrue("Gateway server should restart on the same port: ${server.lastStartError}", restarted)
        val afterRestart = sendRequest("GET", "/api/status", headers, "")
        assertTrue("Persisted replay nonce must survive server restart: " + afterRestart, afterRestart.startsWith("HTTP/1.1 409"))
    }

    @Test
    fun signedSmsIngestPreservesEscapedSmsTextExactly() {
        val eventId = UUID.randomUUID().toString()
        val rawMessage = "رسالة فيها \"علامة\" وشرطة مائلة \\\nوالسطر الثاني"
        val payload = JSONObject()
            .put("sender", "FLOOSAK")
            .put("body", rawMessage)
            .put("receivedAt", System.currentTimeMillis())
            .toString()
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, payload
        )
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        val response = sendRequest("POST", "/api/sms/ingest", headers, payload)
        assertTrue("Valid JSON SMS payload should be acknowledged: $response", response.startsWith("HTTP/1.1 200"))
        val savedSms = runBlocking { db.walletDao().getSmsById(eventId) }
        assertNotNull("SMS should be stored in Room", savedSms)
        assertEquals(rawMessage, savedSms!!.body)
    }

    @Test
    fun authenticatedMalformedJsonIsRejected() {
        val eventId = UUID.randomUUID().toString()
        val invalidJson = "{not-json"
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, invalidJson
        )
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        val response = sendRequest("POST", "/api/sms/ingest", headers, invalidJson)
        assertTrue("Invalid JSON should produce HTTP 400: $response", response.startsWith("HTTP/1.1 400"))
    }

    private fun sendRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String
    ): String {
        Socket("127.0.0.1", testPort).use { socket ->
            socket.soTimeout = 5000
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("${method} ${path} HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Connection: close\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                headers.forEach { (key, value) -> append("${key}: ${value}\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().write(bodyBytes)
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
    @Test
    fun authenticatedIngestRejectsMissingReceivedAtInsteadOfUsingProcessingTime() {
        val eventId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("sender", "FLOOSAK")
            .put("body", "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي")
            .toString()
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, payload
        )
        val headers = mapOf(
            GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
            GatewayRequestSigner.EVENT_ID_HEADER to eventId,
            GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
            GatewayRequestSigner.SIGNATURE_HEADER to signature
        )

        val response = sendRequest("POST", "/api/sms/ingest", headers, payload)
        assertTrue("Missing original SMS timestamp must be rejected: $response", response.startsWith("HTTP/1.1 400"))
        assertEquals(null, runBlocking { db.walletDao().getSmsById(eventId) })
        assertEquals(0, runBlocking { db.walletDao().getAllTransactionsSync().size })
    }

}
