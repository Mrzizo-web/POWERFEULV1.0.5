package com.example

import com.example.data.engine.ReportCalculator
import com.example.data.engine.ReportPeriod
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReportCalculatorTest {
    private val now = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00")).apply {
        set(2026, Calendar.OCTOBER, 13, 15, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val activeSale = SaleEntity(
        id = "sale-active",
        invoiceNumber = "INV-1",
        shiftId = "shift-1",
        userId = "user-1",
        userName = "كاشير",
        totalAmount = 120.0,
        discountAmount = 20.0,
        netAmount = 100.0,
        paymentMethod = PaymentMethod.CASH,
        totalCost = 40.0,
        createdAt = now - 1_000L
    )
    private val voidedSale = SaleEntity(
        id = "sale-voided",
        invoiceNumber = "INV-2",
        shiftId = "shift-1",
        userId = "user-1",
        userName = "كاشير",
        totalAmount = 999.0,
        netAmount = 999.0,
        paymentMethod = PaymentMethod.CASH,
        totalCost = 10.0,
        status = "VOIDED",
        createdAt = now - 2_000L
    )
    private val oldSale = SaleEntity(
        id = "sale-old",
        invoiceNumber = "INV-3",
        shiftId = "shift-2",
        userId = "user-2",
        userName = "كاشير آخر",
        totalAmount = 50.0,
        netAmount = 50.0,
        paymentMethod = PaymentMethod.E_WALLET,
        totalCost = 20.0,
        createdAt = now - 10L * 24L * 60L * 60L * 1000L
    )
    private val expenses = listOf(
        ExpenseEntity(
            id = "expense-today",
            title = "تنظيف",
            category = "CLEANING",
            amount = 10.0,
            userId = "user-1",
            userName = "كاشير",
            createdAt = now - 500L
        ),
        ExpenseEntity(
            id = "expense-old",
            title = "صيانة",
            category = "MAINTENANCE",
            amount = 5.0,
            userId = "user-2",
            userName = "كاشير آخر",
            createdAt = now - 10L * 24L * 60L * 60L * 1000L
        )
    )
    private val items = listOf(
        SaleItemEntity(
            id = "item-active",
            saleId = "sale-active",
            productId = "power-full",
            productName = "Power Full",
            quantity = 2,
            unitPrice = 60.0,
            totalPrice = 120.0,
            unitCost = 20.0,
            totalCost = 40.0
        ),
        SaleItemEntity(
            id = "item-voided",
            saleId = "sale-voided",
            productId = "discarded-product",
            productName = "منتج ملغى",
            quantity = 1,
            unitPrice = 999.0,
            totalPrice = 999.0,
            totalCost = 10.0
        ),
        SaleItemEntity(
            id = "item-old",
            saleId = "sale-old",
            productId = "old-product",
            productName = "منتج قديم",
            quantity = 1,
            unitPrice = 50.0,
            totalPrice = 50.0,
            unitCost = 20.0,
            totalCost = 20.0
        )
    )

    @Test
    fun allTimeReportExcludesVoidedSalesAndUsesCostOfGoods() {
        val result = ReportCalculator.calculate(
            sales = listOf(activeSale, voidedSale, oldSale),
            expenses = expenses,
            saleItems = items,
            period = ReportPeriod.ALL,
            nowMillis = now
        )
        assertEquals(2, result.saleCount)
        assertEquals(150.0, result.totalSales, 0.001)
        assertEquals(60.0, result.costOfGoods, 0.001)
        assertEquals(90.0, result.grossProfit, 0.001)
        assertEquals(15.0, result.totalExpenses, 0.001)
        assertEquals(75.0, result.netProfit, 0.001)
        assertFalse(result.products.any { it.productId == "discarded-product" })
        val powerFull = result.products.single { it.productId == "power-full" }
        assertEquals(2, powerFull.quantity)
        assertEquals(100.0, powerFull.netSales, 0.001)
        assertEquals(40.0, powerFull.costOfGoods, 0.001)
        assertEquals(60.0, powerFull.profit, 0.001)
    }

    @Test
    fun sevenDayReportUsesPeriodAndKeepsPaymentBreakdown() {
        val result = ReportCalculator.calculate(
            sales = listOf(activeSale, voidedSale, oldSale),
            expenses = expenses,
            saleItems = items,
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now
        )
        assertEquals(1, result.saleCount)
        assertEquals(100.0, result.totalSales, 0.001)
        assertEquals(40.0, result.costOfGoods, 0.001)
        assertEquals(10.0, result.totalExpenses, 0.001)
        assertEquals(50.0, result.netProfit, 0.001)
        assertEquals(100.0, result.salesByPaymentMethod[PaymentMethod.CASH] ?: 0.0, 0.001)
        assertEquals(0.0, result.salesByPaymentMethod[PaymentMethod.E_WALLET] ?: 0.0, 0.001)
    }

    @Test
    fun malformedFinancialRowsAreCountedAndExcludedRatherThanCorruptingTotals() {
        val malformed = activeSale.copy(id = "sale-invalid", netAmount = Double.NaN)
        val result = ReportCalculator.calculate(
            sales = listOf(activeSale, malformed),
            expenses = listOf(expenses.first(), expenses.first().copy(id = "expense-invalid", amount = Double.NaN)),
            saleItems = items,
            period = ReportPeriod.ALL,
            nowMillis = now
        )
        assertEquals(2, result.invalidRecordCount)
        assertEquals(1, result.saleCount)
        assertEquals(100.0, result.totalSales, 0.001)
        assertEquals(10.0, result.totalExpenses, 0.001)
    }
}
