package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        assertEquals("IN_PROGRESS", dao.getAllQueueItems().let { flow ->
            kotlinx.coroutines.flow.first(flow).single().status
        })

        assertEquals(1, dao.recoverInProgressItems(2000L))
        assertEquals(1, dao.claimItem(item.id, 2001L))
    }
}
