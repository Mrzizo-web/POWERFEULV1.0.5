package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.DebtEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebtEngineAndroidTest {
    private lateinit var db: AppDatabase
    private val owner = UserEntity(
        id = "debt-test-owner",
        name = "مالك اختبار الديون",
        username = "debt_test_owner",
        role = UserRole.OWNER
    )
    private val customer = CustomerEntity(
        id = "debt-test-customer",
        name = "عميل مدين",
        currentDebt = 1000.0,
        creditLimit = 1500.0,
        allowDebt = true,
        status = CustomerStatus.ACTIVE
    )
    private val shift = ShiftEntity(
        id = "debt-test-shift",
        userId = "debt-test-owner",
        userName = "مالك اختبار الديون",
        openingCash = 5000.0,
        expectedCash = 5000.0,
        status = ShiftStatus.OPEN
    )

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.userDao().insertUser(owner)
        db.customerDao().insertCustomer(customer)
        db.shiftDao().insertShift(shift)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cashDebtPaymentAtomicallyUpdatesBalanceLedgerShiftAndCashMovement() = runBlocking {
        val payment = DebtEngine(db).payDebt(
            customerId = customer.id,
            amount = 250.0,
            paymentMethod = PaymentMethod.CASH,
            shiftId = shift.id,
            user = owner,
            notes = "تحصيل نقدي اختباري"
        )

        assertEquals(750.0, db.customerDao().getCustomerById(customer.id)!!.currentDebt, 0.001)
        assertEquals(5250.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(250.0, db.shiftDao().getShiftById(shift.id)!!.totalCashDebtPayments, 0.001)
        assertEquals("PAYMENT", payment.type)
        assertEquals(1000.0, payment.balanceBefore, 0.001)
        assertEquals(750.0, payment.balanceAfter, 0.001)
        val ledger = db.debtTransactionDao().getTransactionsForCustomer(customer.id).first()
        assertEquals(1, ledger.size)
        assertEquals(1, db.shiftDao().getCashMovements(shift.id).first().size)
        assertTrue(db.auditLogDao().getRecentLogs().first().any { it.action == "DEBT_PAYMENT_RECORDED" })
    }

    @Test
    fun walletDebtPaymentCanBeRecordedWithoutOpenShiftAndDoesNotChangeCash() = runBlocking {
        val payment = DebtEngine(db).payDebt(
            customerId = customer.id,
            amount = 400.0,
            paymentMethod = PaymentMethod.E_WALLET,
            shiftId = "",
            user = owner
        )

        assertEquals(600.0, db.customerDao().getCustomerById(customer.id)!!.currentDebt, 0.001)
        assertEquals(PaymentMethod.E_WALLET, payment.paymentMethod)
        assertEquals("", payment.shiftId)
        assertEquals(5000.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(0.0, db.shiftDao().getShiftById(shift.id)!!.totalCashDebtPayments, 0.001)
        assertEquals(1, db.debtTransactionDao().getTransactionsForCustomer(customer.id).first().size)
    }

    @Test
    fun overpaymentIsRejectedWithoutChangingBalanceOrShift() = runBlocking {
        val result = runCatching {
            DebtEngine(db).payDebt(
                customerId = customer.id,
                amount = 1000.01,
                paymentMethod = PaymentMethod.CASH,
                shiftId = shift.id,
                user = owner
            )
        }

        assertTrue("Overpayment must be rejected", result.isFailure)
        assertEquals(1000.0, db.customerDao().getCustomerById(customer.id)!!.currentDebt, 0.001)
        assertEquals(5000.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)
        assertEquals(0, db.debtTransactionDao().getTransactionsForCustomer(customer.id).first().size)
        assertEquals(0, db.shiftDao().getCashMovements(shift.id).first().size)
    }
}
