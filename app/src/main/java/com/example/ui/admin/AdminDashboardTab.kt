package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.ReportCalculator
import com.example.data.engine.ReportPeriod
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShiftEntity
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import java.util.Locale

@Composable
fun AdminDashboardTab(
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity>,
    expenses: List<ExpenseEntity>,
    rawMaterials: List<RawMaterialEntity>,
    customers: List<CustomerEntity>,
    shifts: List<ShiftEntity>
) {
    var selectedShiftId by remember { mutableStateOf("") }
    var shiftMenuExpanded by remember { mutableStateOf(false) }
    val selectedShift = shifts.firstOrNull { it.id == selectedShiftId }
        ?: shifts.filter { it.status == ShiftStatus.OPEN }.maxByOrNull { it.startTime }
        ?: shifts.maxByOrNull { it.startTime }

    LaunchedEffect(selectedShift?.id) {
        if (selectedShift != null && selectedShiftId.isBlank()) selectedShiftId = selectedShift.id
    }

    val todayReport = remember(sales, expenses, saleItems) {
        ReportCalculator.calculate(
            sales = sales,
            expenses = expenses,
            saleItems = saleItems,
            period = ReportPeriod.TODAY,
            nowMillis = System.currentTimeMillis()
        )
    }
    val shiftSales = sales.filter {
        selectedShift != null && it.shiftId == selectedShift.id &&
            !it.status.equals("VOIDED", ignoreCase = true)
    }.filter {
        it.netAmount.isFinite() && it.netAmount >= 0.0 && it.totalCost.isFinite() && it.totalCost >= 0.0
    }
    val shiftRevenue = shiftSales.sumOf { it.netAmount }
    val shiftCost = shiftSales.sumOf { it.totalCost }
    val shiftExpenses = expenses.filter {
        selectedShift != null && it.shiftId == selectedShift.id && it.amount.isFinite() && it.amount >= 0.0
    }.sumOf { it.amount }
    val shiftNetProfit = shiftRevenue - shiftCost - shiftExpenses
    val lowStock = rawMaterials.filter {
        it.currentStock.isFinite() && it.minStock.isFinite() &&
            it.currentStock > 0.0 && it.currentStock <= it.minStock
    }
    val outOfStock = rawMaterials.filter { !it.currentStock.isFinite() || it.currentStock <= 0.0 }
    val debts = customers.filter { it.currentDebt.isFinite() && it.currentDebt > 0.0 }
    val totalDebt = debts.sumOf { it.currentDebt }
    val openShifts = shifts.filter { it.status == ShiftStatus.OPEN }
    val discrepancies = shifts.filter {
        it.status == ShiftStatus.CLOSED && it.discrepancyAmount.isFinite() &&
            kotlin.math.abs(it.discrepancyAmount) > 0.01
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("لوحة التحكم", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            "ملخص من قاعدة البيانات المحلية • اليوم بتوقيت عدن/صنعاء (UTC+3)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Text("ملخص اليوم", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardMetricCard("المبيعات اليوم", todayReport.totalSales, Color(0xFF2E7D32), Modifier.weight(1f))
            DashboardMetricCard("تكلفة المواد", todayReport.costOfGoods, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardMetricCard("الربح الإجمالي", todayReport.grossProfit, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            DashboardMetricCard(
                "صافي الربح",
                todayReport.netProfit,
                if (todayReport.netProfit >= 0.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                Modifier.weight(1f)
            )
        }

        Text("تفاصيل الشفت", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { shiftMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AccessTime, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedShift == null) "لا يوجد شفت" else
                        "الشفت #" + selectedShift.shiftNumber + " • " + selectedShift.userName
                )
            }
            DropdownMenu(expanded = shiftMenuExpanded, onDismissRequest = { shiftMenuExpanded = false }) {
                shifts.sortedByDescending { it.startTime }.forEach { shift ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "#" + shift.shiftNumber + " • " + shift.userName + " • " +
                                    if (shift.status == ShiftStatus.OPEN) "مفتوح" else "مغلق"
                            )
                        },
                        onClick = {
                            selectedShiftId = shift.id
                            shiftMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (selectedShift == null) {
            Text("لم يبدأ أي شفت بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            Text(
                "الحالة: " + (if (selectedShift.status == ShiftStatus.OPEN) "مفتوح" else "مغلق") +
                    " • النقدية الافتتاحية: " + money(selectedShift.openingCash) + " ريال",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardMetricCard("مبيعات الشفت", shiftRevenue, Color(0xFF2E7D32), Modifier.weight(1f))
                DashboardMetricCard("تكلفة الشفت", shiftCost, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardMetricCard("المصروفات المرتبطة", shiftExpenses, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                DashboardMetricCard(
                    "صافي ربح الشفت",
                    shiftNetProfit,
                    if (shiftNetProfit >= 0.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    Modifier.weight(1f)
                )
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تفصيل طرق الدفع", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    DashboardValueRow("النقدية", selectedShift.totalCashSales)
                    DashboardValueRow("المحافظ", selectedShift.totalWalletSales)
                    DashboardValueRow("الآجل", selectedShift.totalDebtSales)
                    DashboardValueRow("تحصيل دين نقدي", selectedShift.totalCashDebtPayments)
                    DashboardValueRow("تحصيل دين بالمحفظة", selectedShift.totalWalletDebtPayments)
                    DashboardValueRow("النقدية المتوقعة", selectedShift.expectedCash)
                    if (selectedShift.status == ShiftStatus.CLOSED) {
                        DashboardValueRow("النقدية الفعلية", selectedShift.actualCash)
                        DashboardValueRow("الفرق عند الإغلاق", selectedShift.discrepancyAmount)
                    }
                }
            }
        }

        Text("تنبيهات التشغيل", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardCountCard("مواد منخفضة", lowStock.size, Icons.Default.Inventory, Modifier.weight(1f))
            DashboardCountCard("مواد نافدة", outOfStock.size, Icons.Default.Inventory, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardCountCard("شفتات مفتوحة", openShifts.size, Icons.Default.AccessTime, Modifier.weight(1f))
            DashboardCountCard("فروقات إغلاق", discrepancies.size, Icons.Default.Assessment, Modifier.weight(1f))
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي الديون المستحقة", fontWeight = FontWeight.SemiBold)
                    Text(money(totalDebt) + " ريال", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "عدد العملاء ذوي الأرصدة المستحقة: " + debts.size,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (lowStock.isNotEmpty() || outOfStock.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المواد التي تتطلب متابعة", fontWeight = FontWeight.Bold)
                    (outOfStock + lowStock).distinctBy { it.id }.take(10).forEach { material ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(material.name, fontSize = 12.sp)
                            Text(
                                money(material.currentStock) + " " + material.baseUnit,
                                fontSize = 12.sp,
                                color = if (material.currentStock <= 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (outOfStock.size + lowStock.size > 10) {
                        Text("يُعرض أول 10 أصناف تحتاج إلى متابعة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مبيعات اليوم حسب طريقة الدفع", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                DashboardValueRow("نقدي", todayReport.salesByPaymentMethod[PaymentMethod.CASH] ?: 0.0)
                DashboardValueRow("محفظة", todayReport.salesByPaymentMethod[PaymentMethod.E_WALLET] ?: 0.0)
                DashboardValueRow("دين", todayReport.salesByPaymentMethod[PaymentMethod.DEBT] ?: 0.0)
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: Double,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(value) + " ريال", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun DashboardCountCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun DashboardValueRow(label: String, value: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp)
        Text(money(value) + " ريال", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun money(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
