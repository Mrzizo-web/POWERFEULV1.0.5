package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayQueueEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

class GatewayQueueClaimAndroidTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun oneWorkerClaimsAnItemAndRecoveryReleasesAbandonedClaim() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "claim-test-item",
            type = "OUTGOING_SYNC",
            payload = "{}",
            targetEndpoint = "http://127.0.0.1/unused"
        )
        dao.insertItem(item)

        assertEquals(1, dao.claimItem(item.id, 1000L))
        assertEquals(0, dao.claimItem(item.id, 1001L))
        assertEquals("IN_PROGRESS", dao.getAllQueueItems().first().single().status)

        assertEquals(1, dao.recoverInProgressItems(2000L))
        assertEquals(1, dao.claimItem(item.id, 2001L))
    }
    @Test
    fun itemWithoutEndpointIsNotMarkedCompleted() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "missing-endpoint",
            type = "OUTGOING_SYNC",
            payload = "{}",
            targetEndpoint = "",
            nextAttemptAt = 0L
        )
        dao.insertItem(item)

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val engine = com.example.data.engine.GatewaySyncEngine(
            db,
            scope,
            autoStartRetry = false
        )
        try {
            engine.retryAllNow()
            val stored = dao.getAllQueueItems().first().single()
            assertFalse("Missing endpoint must never be reported as completed", stored.status == "COMPLETED")
            assertEquals("RETRYING", stored.status)
            assertEquals(1, stored.retryCount)
            assertTrue(stored.errorMessage.contains("not configured"))
        } finally {
            engine.stopPeriodicRetryLoop()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun failedQueueItemCanBeManuallyRetriedAfterEndpointIsFixed() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "manual-retry-after-failure",
            type = "OUTGOING_SYNC",
            payload = "{}",
            targetEndpoint = "",
            retryCount = 0,
            maxRetries = 1,
            nextAttemptAt = 0L
        )
        dao.insertItem(item)

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val engine = com.example.data.engine.GatewaySyncEngine(
            db,
            scope,
            autoStartRetry = false
        )

        try {
            engine.retryAllNow()
            val failed = dao.getAllQueueItems().first().single()
            assertEquals("FAILED", failed.status)
            assertEquals(1, failed.retryCount)

            val localServer = ServerSocket(0)
            val serverJob = scope.launch {
                localServer.use { server ->
                    server.accept().use { client ->
                        client.soTimeout = 5000
                        val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(response.toByteArray(Charsets.ISO_8859_1))
                            flush()
                        }
                    }
                }
            }

            dao.updateItem(
                failed.copy(
                    targetEndpoint = "http://127.0.0.1:" + localServer.localPort + "/retry"
                )
            )
            engine.retryNow(failed.id)
            serverJob.join()

            val completed = dao.getAllQueueItems().first().single()
            assertEquals("COMPLETED", completed.status)
            assertEquals(0, completed.retryCount)
            assertTrue(completed.errorMessage.isBlank())
        } finally {
            engine.stopPeriodicRetryLoop()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun publicCleartextEndpointIsRejectedBeforeNetworkTransmission() = runBlocking {
        val dao = db.gatewayQueueDao()
        dao.insertItem(
            GatewayQueueEntity(
                id = "public-cleartext-host",
                type = "OUTGOING_SYNC",
                payload = "{}",
                targetEndpoint = "http://203.0.113.10:8080/api/sms/ingest",
                maxRetries = 1,
                nextAttemptAt = 0L
            )
        )
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val engine = com.example.data.engine.GatewaySyncEngine(
            db,
            scope,
            autoStartRetry = false
        )
        try {
            engine.retryAllNow()
            val stored = dao.getAllQueueItems().first().single()
            assertEquals("FAILED", stored.status)
            assertTrue(stored.errorMessage.contains("local/private network host"))
        } finally {
            engine.stopPeriodicRetryLoop()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

}
