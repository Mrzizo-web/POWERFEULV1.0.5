package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayQueueAtomicRetryAndroidTest {
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
        if (::db.isInitialized) db.close()
    }

    @Test
    fun manualRetryCannotOverwriteAnInProgressRow() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "in-progress-retry-test",
            type = "OUTGOING_SYNC",
            payload = "{}",
            status = "IN_PROGRESS",
            retryCount = 3,
            nextAttemptAt = 123L,
            errorMessage = "request is running"
        )
        dao.insertItem(item)

        assertEquals(0, dao.prepareManualRetry(item.id))
        val unchanged = dao.getQueueItemById(item.id)
        assertNotNull(unchanged)
        assertEquals("IN_PROGRESS", unchanged!!.status)
        assertEquals(3, unchanged.retryCount)
        assertEquals("request is running", unchanged.errorMessage)
    }

    @Test
    fun competingWorkersCanClaimOnlyOnce() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "concurrent-claim-test",
            type = "OUTGOING_SYNC",
            payload = "{}",
            status = "RETRYING",
            retryCount = 4,
            errorMessage = "previous failure"
        )
        dao.insertItem(item)

        val results = listOf(
            async(Dispatchers.IO) { dao.claimItem(item.id, 100L) },
            async(Dispatchers.IO) { dao.claimItem(item.id, 101L) }
        ).awaitAll()

        assertEquals(1, results.sum())
        assertEquals("IN_PROGRESS", dao.getQueueItemById(item.id)!!.status)
    }

    @Test
    fun manualRetryAtomicallyResetsEligibleFailedRows() = runBlocking {
        val dao = db.gatewayQueueDao()
        val item = GatewayQueueEntity(
            id = "failed-retry-test",
            type = "OUTGOING_SYNC",
            payload = "{}",
            status = "FAILED",
            retryCount = 5,
            nextAttemptAt = 999L,
            errorMessage = "endpoint unavailable"
        )
        dao.insertItem(item)

        assertEquals(1, dao.prepareManualRetry(item.id))
        val prepared = dao.getQueueItemById(item.id)
        assertNotNull(prepared)
        assertEquals("RETRYING", prepared!!.status)
        assertEquals(0, prepared.retryCount)
        assertEquals(0L, prepared.nextAttemptAt)
        assertTrue(prepared.errorMessage.isEmpty())
    }
}
