package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.GatewaySyncEngine
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CafeteriaSettingEntity
import com.example.data.local.entity.ShiftEntity
import com.example.domain.model.ShiftStatus
import com.example.gateway.GatewayRequestSigner
import com.example.gateway.GatewaySecretStore
import com.example.gateway.GatewayServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Exercises the production POS HTTP server over a real loopback TCP socket using
 * the wire fields emitted by the separate POWER FEUL Gateway app.
 */
class GatewayServerWalletIngestionAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var server: GatewayServer
    private lateinit var token: String
    private lateinit var deviceId: String
    private lateinit var serverScope: CoroutineScope
    private lateinit var syncScope: CoroutineScope
    private var testPort = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        token = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        deviceId = "INTEGRATION_TEST_GATEWAY"
        db.settingsDao().setSetting(CafeteriaSettingEntity("gateway_device_id", deviceId))
        assertTrue(GatewaySecretStore(context).saveToken(token))

        testPort = ServerSocket(0).use { it.localPort }
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        server = GatewayServer(
            db = db,
            walletMatchingEngine = WalletMatchingEngine(db),
            port = testPort,
            coroutineScope = serverScope,
            gatewaySyncEngine = GatewaySyncEngine(db, syncScope, autoStartRetry = false),
            context = context
        )
        assertTrue("POS HTTP server must start", server.start())
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
        if (::serverScope.isInitialized) serverScope.cancel()
        if (::syncScope.isInitialized) syncScope.cancel()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun signedGatewayHttpMessagesForAllThreeWalletsCreateTransactionsAndRetryIdempotently() = runBlocking {
        val receivedAtBase = System.currentTimeMillis()
        val shift = ShiftEntity(
            userId = "integration-cashier",
            userName = "Integration Cashier",
            startTime = receivedAtBase - 10_000L,
            endTime = null,
            status = ShiftStatus.OPEN
        )
        db.shiftDao().insertShift(shift)

        data class Sample(
            val wallet: String,
            val amount: Double,
            val sender: String,
            val body: String,
            val transactionReference: String? = null
        )
        val samples = listOf(
            Sample(
                wallet = "JEEB",
                amount = 5000.0,
                sender = "عبدالله الجميلي",
                body = "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225"
            ),
            Sample(
                wallet = "FLOOSAK",
                amount = 800.0,
                sender = "حاشد بزدان",
                body = "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي"
            ),
            Sample(
                wallet = "JAWALI",
                amount = 16000.0,
                sender = "عميد محمد احمد احمد",
                body = "استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد",
                transactionReference = "468486397181"
            )
        )

        val eventIds = mutableListOf<String>()
        samples.forEachIndexed { index, sample ->
            val receivedAt = receivedAtBase + index
            val eventId = "integration-${sample.wallet.lowercase()}-${UUID.randomUUID()}"
            eventIds += eventId
            val response = sendGatewayStyleIngest(
                eventId = eventId,
                wallet = sample.wallet,
                amount = sample.amount,
                sender = sample.sender,
                rawSms = sample.body,
                receivedAt = receivedAt,
                transactionReference = sample.transactionReference
            )
            assertTrue("HTTP ingest failed for ${sample.wallet}: $response", response.startsWith("HTTP/1.1 200"))
            val result = JSONObject(response.substringAfter("\r\n\r\n"))
            assertEquals("CREATED", result.optString("result"))
            assertEquals(sample.wallet, result.optString("wallet"))
            assertEquals(sample.amount, result.getDouble("amount"), 0.001)
            assertEquals(receivedAt, result.getLong("receivedAt"))
            assertEquals(false, result.getBoolean("outOfShift"))
        }

        val transactions = db.walletDao().getAllTransactionsSync()
        assertEquals("Exactly one transaction per distinct incoming SMS", 3, transactions.size)
        samples.forEach { sample ->
            val tx = transactions.single { it.walletCode == sample.wallet }
            assertEquals(sample.amount, tx.amount, 0.001)
            assertEquals(shift.id, tx.shiftId)
            assertEquals(shift.userName, tx.cashierName)
            assertEquals(shift.userId, tx.cashierId)
            assertTrue(tx.receivedAt >= shift.startTime)
        }

        // A retry uses the same stable event ID but a fresh signed request timestamp.
        val first = samples.first()
        val duplicateResponse = sendGatewayStyleIngest(
            eventId = eventIds.first(),
            wallet = first.wallet,
            amount = first.amount,
            sender = first.sender,
            rawSms = first.body,
            receivedAt = receivedAtBase,
            transactionReference = first.transactionReference
        )
        assertTrue("Retry should be acknowledged, not fail: $duplicateResponse",
            duplicateResponse.startsWith("HTTP/1.1 200"))
        assertEquals("DUPLICATE", JSONObject(duplicateResponse.substringAfter("\r\n\r\n")).optString("result"))
        assertEquals("Retry must not create a second financial transaction", 3,
            db.walletDao().getAllTransactionsSync().size)
    }

    private fun sendGatewayStyleIngest(
        eventId: String,
        wallet: String,
        amount: Double,
        sender: String,
        rawSms: String,
        receivedAt: Long,
        transactionReference: String?
    ): String {
        // Preserve the same payload fields/order and signing inputs used by Gateway HttpPosGateway.
        val body = JSONObject().apply {
            put("sender", sender)
            put("body", rawSms)
            put("eventId", eventId)
            put("paymentId", "payment-$eventId")
            put("wallet", wallet)
            put("amount", amount)
            put("currency", "YER")
            put("transactionId", transactionReference ?: JSONObject.NULL)
            put("senderAccount", JSONObject.NULL)
            put("receivedAt", receivedAt)
            put("gatewayDeviceId", deviceId)
            put("sourceSmsId", eventId)
            put("rawMessage", rawSms)
            put("parserVersion", "integration-test")
            put("createdAt", receivedAt)
            put("messageCount", 1)
            put("confidence", 1.0)
            put("status", "QUEUED")
        }.toString()
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token = token,
            method = "POST",
            path = "/api/sms/ingest",
            timestamp = timestamp,
            eventId = eventId,
            deviceId = deviceId,
            body = body
        )
        return sendRequest(
            method = "POST",
            path = "/api/sms/ingest",
            headers = mapOf(
                GatewayRequestSigner.DEVICE_ID_HEADER to deviceId,
                GatewayRequestSigner.EVENT_ID_HEADER to eventId,
                GatewayRequestSigner.TIMESTAMP_HEADER to timestamp,
                GatewayRequestSigner.SIGNATURE_HEADER to signature
            ),
            body = body
        )
    }

    private fun sendRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String
    ): String {
        Socket("127.0.0.1", testPort).use { socket ->
            socket.soTimeout = 5_000
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Connection: close\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                headers.forEach { (key, value) -> append("$key: $value\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().write(header.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().write(bodyBytes)
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
