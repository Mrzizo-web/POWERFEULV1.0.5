package com.example.data.engine

import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.domain.model.PaymentMethod
import java.util.Calendar
import java.util.TimeZone

enum class ReportPeriod(val label: String) {
    TODAY("اليوم"),
    LAST_7_DAYS("آخر 7 أيام"),
    THIS_MONTH("هذا الشهر"),
    ALL("كل الفترات")
}

data class ProductSalesLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val netSales: Double,
    val costOfGoods: Double,
    val profit: Double
)

data class FinancialReportSummary(
    val period: ReportPeriod,
    val saleCount: Int,
    val totalSales: Double,
    val costOfGoods: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val salesByPaymentMethod: Map<PaymentMethod, Double>,
    val salesByEmployee: Map<String, Double>,
    val products: List<ProductSalesLine>,
    val invalidRecordCount: Int
)

object ReportCalculator {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private val businessTimeZone: TimeZone = TimeZone.getTimeZone("GMT+03:00")

    fun calculate(
        sales: List<SaleEntity>,
        expenses: List<ExpenseEntity>,
        saleItems: List<SaleItemEntity>,
        period: ReportPeriod,
        nowMillis: Long = System.currentTimeMillis()
    ): FinancialReportSummary {
        require(nowMillis >= 0L) { "وقت التقرير غير صالح" }
        val start = periodStart(period, nowMillis)
        val periodSales = sales.filter {
            !it.status.equals("VOIDED", ignoreCase = true) &&
                it.createdAt >= start && it.createdAt <= nowMillis
        }
        val validSales = periodSales.filter {
            it.totalAmount.isFinite() && it.totalAmount >= 0.0 &&
                it.netAmount.isFinite() && it.netAmount >= 0.0 &&
                it.totalCost.isFinite() && it.totalCost >= 0.0
        }
        val periodExpenses = expenses.filter {
            it.createdAt >= start && it.createdAt <= nowMillis
        }
        val validExpenses = periodExpenses.filter { it.amount.isFinite() && it.amount >= 0.0 }
        val invalidCount = (periodSales.size - validSales.size) + (periodExpenses.size - validExpenses.size)

        val totalSales = validSales.sumOf { it.netAmount }
        val costOfGoods = validSales.sumOf { it.totalCost }
        val totalExpenses = validExpenses.sumOf { it.amount }
        val grossProfit = totalSales - costOfGoods
        val netProfit = grossProfit - totalExpenses
        val activeSalesById = validSales.associateBy { it.id }
        val salesByPaymentMethod = validSales
            .groupBy { it.paymentMethod }
            .mapValues { (_, methodSales) -> methodSales.sumOf { it.netAmount } }
        val salesByEmployee = validSales
            .groupBy { it.userName.ifBlank { it.userId } }
            .mapValues { (_, employeeSales) -> employeeSales.sumOf { it.netAmount } }
            .toList().sortedByDescending { it.second }.toMap()

        val validItems = saleItems.filter { item ->
            activeSalesById.containsKey(item.saleId) &&
                item.quantity > 0 &&
                item.totalPrice.isFinite() && item.totalPrice >= 0.0 &&
                item.totalCost.isFinite() && item.totalCost >= 0.0
        }
        val itemTotalsBySale = validItems.groupBy { it.saleId }
            .mapValues { (_, items) -> items.sumOf { it.totalPrice } }

        val productGroups = validItems.groupBy { it.productId to it.productName }
        val products = productGroups.map { (key, items) ->
            var netRevenue = 0.0
            for (item in items) {
                val sale = activeSalesById.getValue(item.saleId)
                val invoiceItemsTotal = itemTotalsBySale[item.saleId] ?: 0.0
                val allocatedNet = if (invoiceItemsTotal > 0.0) {
                    item.totalPrice * (sale.netAmount / invoiceItemsTotal)
                } else {
                    0.0
                }
                netRevenue += allocatedNet
            }
            val itemCost = items.sumOf { it.totalCost }
            ProductSalesLine(
                productId = key.first,
                productName = key.second,
                quantity = items.sumOf { it.quantity },
                netSales = netRevenue,
                costOfGoods = itemCost,
                profit = netRevenue - itemCost
            )
        }.sortedByDescending { it.netSales }

        return FinancialReportSummary(
            period = period,
            saleCount = validSales.size,
            totalSales = totalSales,
            costOfGoods = costOfGoods,
            grossProfit = grossProfit,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            salesByPaymentMethod = salesByPaymentMethod,
            salesByEmployee = salesByEmployee,
            products = products,
            invalidRecordCount = invalidCount
        )
    }

    private fun periodStart(period: ReportPeriod, nowMillis: Long): Long = when (period) {
        ReportPeriod.ALL -> 0L
        ReportPeriod.LAST_7_DAYS -> (nowMillis - 7L * DAY_MILLIS).coerceAtLeast(0L)
        ReportPeriod.TODAY -> Calendar.getInstance(businessTimeZone).run {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
        ReportPeriod.THIS_MONTH -> Calendar.getInstance(businessTimeZone).run {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
}
