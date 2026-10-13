package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.ShiftEngine
import com.example.data.engine.ShiftResult
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShiftEngineAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: ShiftEngine
    private lateinit var owner: UserEntity
    private lateinit var cashier: UserEntity

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = ShiftEngine(db)
        owner = UserEntity(
            id = "shift-owner",
            name = "مالك اختبار",
            username = "shift_owner",
            role = UserRole.OWNER
        )
        cashier = UserEntity(
            id = "shift-cashier",
            name = "كاشير اختبار",
            username = "shift_cashier",
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(owner)
        db.userDao().insertUser(cashier)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun closedShiftCannotBeClosedTwiceOrWithInvalidAmounts() = runBlocking {
        val started = engine.startShift(owner, 5000.0) as ShiftResult.Success
        val invalid = engine.closeShift(
            started.shift.id,
            actualCash = Double.NaN,
            handedOverCash = 0.0,
            leftForNextShift = 0.0,
            user = owner
        )
        assertTrue(invalid is ShiftResult.Error)
        assertEquals(ShiftStatus.OPEN, db.shiftDao().getShiftById(started.shift.id)!!.status)

        val closed = engine.closeShift(
            started.shift.id,
            actualCash = 4800.0,
            handedOverCash = 3000.0,
            leftForNextShift = 1800.0,
            user = owner
        ) as ShiftResult.Success
        assertEquals(ShiftStatus.CLOSED, closed.shift.status)

        val secondClose = engine.closeShift(
            started.shift.id,
            actualCash = 4800.0,
            handedOverCash = 3000.0,
            leftForNextShift = 1800.0,
            user = owner
        )
        assertTrue(secondClose is ShiftResult.Error)
        assertEquals(closed.shift.endTime, db.shiftDao().getShiftById(started.shift.id)!!.endTime)
        assertEquals(1, db.auditLogDao().getRecentLogs().first().count { it.action == "SHIFT_CLOSED" })
    }

    @Test
    fun handoverRequiresClosedSourceAndNeverCreatesOrphanRecord() = runBlocking {
        val source = engine.startShift(owner, 5000.0) as ShiftResult.Success
        val beforeClose = engine.acceptHandover(source.shift.id, cashier, 4500.0)
        assertTrue(beforeClose is ShiftResult.Error)
        assertTrue(db.shiftDao().getRecentHandovers().first().isEmpty())
        assertEquals(1, db.shiftDao().getAllShifts().first().size)

        engine.closeShift(
            source.shift.id,
            actualCash = 4500.0,
            handedOverCash = 0.0,
            leftForNextShift = 4500.0,
            user = owner
        )
        val accepted = engine.acceptHandover(source.shift.id, cashier, 4400.0) as ShiftResult.Success
        val handovers = db.shiftDao().getRecentHandovers().first()
        assertEquals(1, handovers.size)
        assertEquals(source.shift.id, handovers.single().fromShiftId)
        assertEquals(accepted.shift.id, handovers.single().toShiftId)
        assertEquals(cashier.id, handovers.single().toUserId)
        assertEquals(4500.0, handovers.single().expectedLeftAmount, 0.001)
        assertEquals(4400.0, handovers.single().actualReceivedAmount, 0.001)

        val duplicate = engine.acceptHandover(source.shift.id, cashier, 4400.0)
        assertTrue(duplicate is ShiftResult.Error)
        assertEquals(1, db.shiftDao().getRecentHandovers().first().size)
        assertEquals(2, db.shiftDao().getAllShifts().first().size)
    }

    @Test
    fun startingShiftWithNegativeOpeningCashIsRejected() = runBlocking {
        val result = engine.startShift(owner, -1.0)
        assertTrue(result is ShiftResult.Error)
        assertTrue(db.shiftDao().getAllShifts().first().isEmpty())
    }
}
