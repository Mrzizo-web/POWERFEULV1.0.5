package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.ExpenseEngine
import com.example.data.engine.ExpenseValidationException
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ExpenseEngineAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: ExpenseEngine
    private val user = UserEntity(
        id = "expense-owner",
        name = "مالك اختبار",
        username = "expense_owner",
        role = UserRole.OWNER
    )
    private val shift = ShiftEntity(
        id = "expense-shift",
        shiftNumber = 1,
        userId = "expense-owner",
        userName = "مالك اختبار",
        openingCash = 5000.0,
        expectedCash = 9500.0,
        status = ShiftStatus.OPEN
    )

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = ExpenseEngine(db)
        db.userDao().insertUser(user)
        db.shiftDao().insertShift(shift)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cashExpenseAtomicallyUpdatesExpenseCashMovementAndExpectedCash() = runBlocking {
        val recorded = engine.recordExpense(
            title = "ماء وتنظيف",
            amount = 600.0,
            category = "مرافق",
            paidTo = "مورد",
            notes = "",
            paymentMethod = PaymentMethod.CASH,
            shiftId = shift.id,
            user = user
        )

        assertEquals(shift.id, recorded.shiftId)
        assertEquals(PaymentMethod.CASH, recorded.paymentMethod)
        assertEquals(600.0, db.shiftDao().getShiftById(shift.id)!!.totalExpensesCash, 0.001)
        assertEquals(8900.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(1, db.expenseDao().getAllExpenses().first().size)
        val movement = db.shiftDao().getCashMovements(shift.id).first().single()
        assertEquals("EXPENSE", movement.type)
        assertEquals(recorded.id, movement.referenceId)
        assertEquals(600.0, movement.amount, 0.001)
    }

    @Test
    fun insufficientCashAndInvalidAmountsLeaveNoPartialRecords() = runBlocking {
        assertThrows(ExpenseValidationException::class.java) {
            runBlocking {
                engine.recordExpense(
                    title = "مصروف أكبر من النقدية",
                    amount = 10000.0,
                    category = "مرافق",
                    paidTo = "",
                    notes = "",
                    paymentMethod = PaymentMethod.CASH,
                    shiftId = shift.id,
                    user = user
                )
            }
        }
        assertThrows(ExpenseValidationException::class.java) {
            runBlocking {
                engine.recordExpense(
                    title = "مصروف غير صالح",
                    amount = Double.NaN,
                    category = "مرافق",
                    paidTo = "",
                    notes = "",
                    paymentMethod = PaymentMethod.CASH,
                    shiftId = shift.id,
                    user = user
                )
            }
        }
        assertEquals(0, db.expenseDao().getAllExpenses().first().size)
        assertEquals(0, db.shiftDao().getCashMovements(shift.id).first().size)
        assertEquals(9500.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(0.0, db.shiftDao().getShiftById(shift.id)!!.totalExpensesCash, 0.001)
    }

    @Test
    fun walletExpenseDoesNotChangeDrawerCash() = runBlocking {
        val recorded = engine.recordExpense(
            title = "خدمة إلكترونية",
            amount = 300.0,
            category = "مرافق",
            paidTo = "مزود خدمة",
            notes = "",
            paymentMethod = PaymentMethod.E_WALLET,
            shiftId = shift.id,
            user = user
        )
        assertEquals(PaymentMethod.E_WALLET, recorded.paymentMethod)
        assertEquals(9500.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(0.0, db.shiftDao().getShiftById(shift.id)!!.totalExpensesCash, 0.001)
        assertEquals(0, db.shiftDao().getCashMovements(shift.id).first().size)
        assertEquals(1, db.expenseDao().getAllExpenses().first().size)
    }
}
