package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.DebtEngine
import com.example.data.engine.InventoryAdjustmentEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.CustomerStatus
import com.example.domain.model.InventoryTxType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WasteReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinancialIntegrityAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var owner: UserEntity
    private lateinit var cashier: UserEntity
    private lateinit var shift: ShiftEntity

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        owner = UserEntity(
            id = "integrity-owner",
            name = "مالك اختبار",
            username = "integrity_owner",
            role = UserRole.OWNER
        )
        cashier = UserEntity(
            id = "integrity-cashier",
            name = "كاشير اختبار",
            username = "integrity_cashier",
            role = UserRole.CASHIER
        )
        db.userDao().insertUser(owner)
        db.userDao().insertUser(cashier)
        shift = ShiftEntity(
            id = "integrity-shift",
            userId = owner.id,
            userName = owner.name,
            openingCash = 1000.0,
            expectedCash = 1000.0,
            status = ShiftStatus.OPEN
        )
        db.shiftDao().insertShift(shift)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cashDebtCollectionUpdatesBalanceLedgerAndDrawerAtomically() = runBlocking {
        val customer = CustomerEntity(
            id = "debt-customer",
            name = "عميل دين",
            currentDebt = 300.0,
            creditLimit = 1000.0,
            status = CustomerStatus.ACTIVE
        )
        db.customerDao().insertCustomer(customer)

        val tx = DebtEngine(db).payDebt(
            customerId = customer.id,
            amount = 125.0,
            paymentMethod = PaymentMethod.CASH,
            shiftId = shift.id,
            user = owner,
            notes = "سداد جزئي"
        )

        val updatedCustomer = db.customerDao().getCustomerById(customer.id)!!
        val updatedShift = db.shiftDao().getShiftById(shift.id)!!
        assertEquals(300.0, tx.balanceBefore, 0.001)
        assertEquals(175.0, tx.balanceAfter, 0.001)
        assertEquals(shift.id, tx.shiftId)
        assertEquals(PaymentMethod.CASH, tx.paymentMethod)
        assertEquals(175.0, updatedCustomer.currentDebt, 0.001)
        assertEquals(CustomerStatus.ACTIVE, updatedCustomer.status)
        assertEquals(1125.0, updatedShift.expectedCash, 0.001)
        assertEquals(125.0, updatedShift.totalCashDebtPayments, 0.001)
        assertEquals(0.0, updatedShift.totalDebtSales, 0.001)
        assertEquals(1, db.debtTransactionDao().getTransactionsForCustomer(customer.id).first().size)
        assertEquals("DEBT_COLLECTION", db.shiftDao().getCashMovements(shift.id).first().single().type)
    }

    @Test
    fun blockedCustomerStaysBlockedAndOverpaymentWritesNothing() = runBlocking {
        val customer = CustomerEntity(
            id = "blocked-debt-customer",
            name = "عميل محظور",
            currentDebt = 80.0,
            creditLimit = 1000.0,
            status = CustomerStatus.BLOCKED
        )
        db.customerDao().insertCustomer(customer)

        val overpay = runCatching {
            DebtEngine(db).payDebt(
                customerId = customer.id,
                amount = 90.0,
                paymentMethod = PaymentMethod.CASH,
                shiftId = shift.id,
                user = owner
            )
        }
        assertTrue(overpay.isFailure)
        assertEquals(80.0, db.customerDao().getCustomerById(customer.id)!!.currentDebt, 0.001)
        assertTrue(db.debtTransactionDao().getTransactionsForCustomer(customer.id).first().isEmpty())
        assertEquals(1000.0, db.shiftDao().getShiftById(shift.id)!!.expectedCash, 0.001)

        DebtEngine(db).payDebt(
            customerId = customer.id,
            amount = 80.0,
            paymentMethod = PaymentMethod.CASH,
            shiftId = shift.id,
            user = owner
        )
        val settled = db.customerDao().getCustomerById(customer.id)!!
        assertEquals(0.0, settled.currentDebt, 0.001)
        assertEquals(CustomerStatus.BLOCKED, settled.status)
    }

    @Test
    fun walletDebtCollectionHasSeparateShiftTotalAndDoesNotIncreaseSales() = runBlocking {
        val customer = CustomerEntity(
            id = "wallet-debt-customer",
            name = "عميل سداد محفظة",
            currentDebt = 200.0,
            creditLimit = 500.0
        )
        db.customerDao().insertCustomer(customer)

        val tx = DebtEngine(db).payDebt(
            customerId = customer.id,
            amount = 75.0,
            paymentMethod = PaymentMethod.E_WALLET,
            shiftId = shift.id,
            user = owner
        )
        val updatedShift = db.shiftDao().getShiftById(shift.id)!!
        assertEquals(PaymentMethod.E_WALLET, tx.paymentMethod)
        assertEquals(75.0, updatedShift.totalWalletDebtPayments, 0.001)
        assertEquals(0.0, updatedShift.totalWalletSales, 0.001)
        assertEquals(1000.0, updatedShift.expectedCash, 0.001)
    }

    @Test
    fun wasteDeductsStockAndCreatesWasteAndInventoryLedgerInOneTransaction() = runBlocking {
        val material = RawMaterialEntity(
            id = "waste-material",
            name = "شوفان",
            baseUnit = "g",
            currentStock = 1000.0,
            avgCostPerUnit = 0.5
        )
        db.rawMaterialDao().insertRawMaterial(material)

        val waste = InventoryAdjustmentEngine(db).recordWaste(
            materialId = material.id,
            quantity = 200.0,
            unit = "g",
            reason = WasteReason.DAMAGED,
            notes = "تلف",
            user = owner
        )

        assertEquals(100.0, waste.estimatedCost, 0.001)
        assertEquals(800.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
        assertEquals(1, db.wasteDao().getAllWaste().first().size)
        val transactions = db.inventoryTransactionDao().getTransactionsForMaterial(material.id).first()
        assertEquals(1, transactions.size)
        assertEquals(InventoryTxType.WASTE, transactions.single().type)
        assertEquals(-200.0, transactions.single().quantityChange, 0.001)
        assertEquals(1000.0, transactions.single().previousQuantity, 0.001)
        assertEquals(800.0, transactions.single().newQuantity, 0.001)
    }

    @Test
    fun insufficientStockOrUnauthorizedWasteLeavesAllRowsUnchanged() = runBlocking {
        val material = RawMaterialEntity(
            id = "waste-short-material",
            name = "حليب",
            baseUnit = "ml",
            currentStock = 100.0,
            avgCostPerUnit = 1.0
        )
        db.rawMaterialDao().insertRawMaterial(material)

        val insufficient = runCatching {
            InventoryAdjustmentEngine(db).recordWaste(
                material.id, 200.0, "ml", WasteReason.OTHER, "كمية غير متوفرة", owner
            )
        }
        assertTrue(insufficient.isFailure)
        assertEquals(100.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
        assertTrue(db.wasteDao().getAllWaste().first().isEmpty())
        assertTrue(db.inventoryTransactionDao().getTransactionsForMaterial(material.id).first().isEmpty())

        val unauthorized = runCatching {
            InventoryAdjustmentEngine(db).recordWaste(
                material.id, 10.0, "ml", WasteReason.OTHER, "دون صلاحية", cashier
            )
        }
        assertTrue(unauthorized.isFailure)
        assertEquals(100.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
        assertTrue(db.wasteDao().getAllWaste().first().isEmpty())
    }

    @Test
    fun stockAdjustmentUpdatesBalanceAndLedgerAndRejectsInvalidStock() = runBlocking {
        val material = RawMaterialEntity(
            id = "adjust-material",
            name = "موز",
            baseUnit = "g",
            currentStock = 500.0,
            avgCostPerUnit = 0.25
        )
        db.rawMaterialDao().insertRawMaterial(material)

        val adjustment = InventoryAdjustmentEngine(db)
            .applyStockAdjustment(material.id, 320.0, "جرد فعلي", owner)
        assertEquals(500.0, adjustment.systemStock, 0.001)
        assertEquals(320.0, adjustment.actualStock, 0.001)
        assertEquals(-180.0, adjustment.discrepancy, 0.001)
        assertEquals(320.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
        val tx = db.inventoryTransactionDao().getTransactionsForMaterial(material.id).first().single()
        assertEquals(InventoryTxType.ADJUSTMENT, tx.type)
        assertEquals(-180.0, tx.quantityChange, 0.001)
        assertEquals(500.0, tx.previousQuantity, 0.001)
        assertEquals(320.0, tx.newQuantity, 0.001)

        val invalid = runCatching {
            InventoryAdjustmentEngine(db).applyStockAdjustment(material.id, Double.NaN, "قيمة غير صالحة", owner)
        }
        assertTrue(invalid.isFailure)
        assertEquals(320.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)
    }

    @Test
    fun cashierCannotAdjustInventoryOrCloseAnotherUsersShift() = runBlocking {
        val material = RawMaterialEntity(
            id = "permission-material",
            name = "تمر",
            baseUnit = "g",
            currentStock = 200.0
        )
        db.rawMaterialDao().insertRawMaterial(material)
        val adjustment = runCatching {
            InventoryAdjustmentEngine(db).applyStockAdjustment(material.id, 100.0, "غير مصرح", cashier)
        }
        assertTrue(adjustment.isFailure)
        assertEquals(200.0, db.rawMaterialDao().getRawMaterialById(material.id)!!.currentStock, 0.001)

        val result = com.example.data.engine.ShiftEngine(db).closeShift(
            shiftId = shift.id,
            actualCash = 1000.0,
            handedOverCash = 1000.0,
            leftForNextShift = 0.0,
            user = cashier
        )
        assertTrue(result is com.example.data.engine.ShiftResult.Error)
        assertEquals(ShiftStatus.OPEN, db.shiftDao().getShiftById(shift.id)!!.status)
    }
}
