package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.IngestResult
import com.example.data.engine.WalletMatchingEngine
import com.example.data.engine.ShiftEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WalletTransferStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WalletShiftIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun user() = UserEntity(
        name = "Test Cashier",
        username = "test",
        role = UserRole.CASHIER
    )

    @Test
    fun incomingSmsUsesReceivedAtShiftAndNotProcessingTime() = runBlocking {
        val u = user()
        val shift = ShiftEntity(
            userId = u.id,
            userName = u.name,
            startTime = 1_000L,
            endTime = 10_000L,
            status = ShiftStatus.CLOSED
        )
        db.shiftDao().insertShift(shift)

        val engine = WalletMatchingEngine(db)
        val result = engine.ingestSms(
            sender = "Jaib",
            body = "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225",
            receivedAt = 5_000L,
            sourceSmsId = "gateway-event-received-at-1"
        )

        assertTrue(result is IngestResult.Created)
        val tx = (result as IngestResult.Created).tx
        assertEquals(shift.id, tx.shiftId)
        assertEquals(5_000L, tx.receivedAt)
        assertEquals(WalletTransferStatus.MATCHED, tx.status)
    }

    @Test
    fun sameGatewayEventIsIdempotentWithoutTransactionReference() = runBlocking {
        val u = user()
        val shift = ShiftEntity(
            userId = u.id,
            userName = u.name,
            startTime = 1_000L,
            endTime = null,
            status = ShiftStatus.OPEN
        )
        db.shiftDao().insertShift(shift)

        val engine = WalletMatchingEngine(db)
        val body = "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 800.00 ر.ي"

        val first = engine.ingestSms("Floosak", body, 5_000L, "event-800")
        val second = engine.ingestSms("Floosak", body, 5_000L, "event-800")

        assertTrue(first is IngestResult.Created)
        assertTrue(second is IngestResult.Duplicate)
        assertEquals(1, db.walletDao().getAllTransactionsSync().size)
        assertEquals(1, db.walletDao().getAllSmsSync().size)
    }

    @Test
    fun outgoingJaibSmsNeverCreatesIncomingTransfer() = runBlocking {
        val engine = WalletMatchingEngine(db)
        val result = engine.ingestSms(
            sender = "Jaib",
            body = "خصم 2290 ر.ي التحويل الى Wenet Pay رص:53118.47ر.ي الى 22208 فلوسك",
            receivedAt = 5_000L,
            sourceSmsId = "outgoing-1"
        )
        assertTrue(result is IngestResult.Unmatched)
        assertEquals(0, db.walletDao().getAllTransactionsSync().size)
    }

    @Test
    fun noShiftAtReceivedAtMarksTransferOutOfShift() = runBlocking {
        val u = user()
        db.shiftDao().insertShift(
            ShiftEntity(
                userId = u.id,
                userName = u.name,
                startTime = 10_000L,
                endTime = 20_000L,
                status = ShiftStatus.CLOSED
            )
        )

        val engine = WalletMatchingEngine(db)
        val result = engine.ingestSms(
            "Jawali",
            "لقد استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد",
            5_000L,
            "outside-shift-1"
        )

        assertTrue(result is IngestResult.Created)
        assertTrue((result as IngestResult.Created).isOutOfShift)
        assertEquals(WalletTransferStatus.OUT_OF_SHIFT, result.tx.status)
        assertNull(result.tx.shiftId)
    }

    @Test
    fun shiftEngineKeepsOpeningAndExpectedCashAtomic() = runBlocking {
        val engine = ShiftEngine(db)
        val u = user()

        val opened = engine.startShift(u, 10000.0)
        assertTrue(opened is com.example.data.engine.ShiftResult.Success)
        val shift = (opened as com.example.data.engine.ShiftResult.Success).shift
        assertEquals(10000.0, shift.openingCash, 0.001)
        assertEquals(10000.0, shift.expectedCash, 0.001)

        val closed = engine.closeShift(
            shift.id,
            actualCash = 9500.0,
            handedOverCash = 9000.0,
            leftForNextShift = 500.0,
            user = u
        )
        assertTrue(closed is com.example.data.engine.ShiftResult.Success)
        val finalShift = (closed as com.example.data.engine.ShiftResult.Success).shift
        assertEquals(-500.0, finalShift.discrepancyAmount, 0.001)
        assertEquals(ShiftStatus.CLOSED, finalShift.status)
    }
}
