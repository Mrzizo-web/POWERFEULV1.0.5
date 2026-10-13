package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*
import com.example.domain.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminInventoryTab(
    rawMaterials: List<RawMaterialEntity>,
    onAddStock: (materialId: String, amount: Double) -> Unit,
    onAddRawMaterial: (name: String, unit: String, initialStock: Double, minStock: Double, cost: Double) -> Unit = { _, _, _, _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showStockDialog by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf("جرام") }
    var newStockText by remember { mutableStateOf("") }
    var newMinStockText by remember { mutableStateOf("10") }
    var newCostText by remember { mutableStateOf("0.0") }
    var addAmountText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إدارة المخزون والمواد الخام (${rawMaterials.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة صنف مخزون")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(rawMaterials) { m ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("الرصيد الحالي: ${m.currentStock} ${m.baseUnit} • حد التنبيه: ${m.minStock} • التكلفة: ${m.avgCostPerUnit} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { showStockDialog = m },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("توريد / إضافة كمية", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة مادة خام جديدة للمخزون") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("اسم الصنف / المادة الخام") }, singleLine = true)
                    OutlinedTextField(value = newUnit, onValueChange = { newUnit = it }, label = { Text("وحدة القياس (مثال: جرام، مل، حبة)") }, singleLine = true)
                    OutlinedTextField(value = newStockText, onValueChange = { newStockText = it }, label = { Text("الكمية الافتتاحية") }, singleLine = true)
                    OutlinedTextField(value = newMinStockText, onValueChange = { newMinStockText = it }, label = { Text("حد التنبيه الأدنى") }, singleLine = true)
                    OutlinedTextField(value = newCostText, onValueChange = { newCostText = it }, label = { Text("سعر تكلفة الوحدة (ريال)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stock = newStockText.toDoubleOrNull() ?: 0.0
                        val minStock = newMinStockText.toDoubleOrNull() ?: 5.0
                        val cost = newCostText.toDoubleOrNull() ?: 0.0
                        if (newName.isNotBlank()) {
                            onAddRawMaterial(newName, newUnit, stock, minStock, cost)
                            showAddDialog = false
                            newName = ""
                            newStockText = ""
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showStockDialog != null) {
        val targetMat = showStockDialog!!
        AlertDialog(
            onDismissRequest = { showStockDialog = null },
            title = { Text("إضافة كمية إلى ${targetMat.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الرصيد الحالي: ${targetMat.currentStock} ${targetMat.baseUnit}", fontSize = 13.sp)
                    OutlinedTextField(
                        value = addAmountText,
                        onValueChange = { addAmountText = it },
                        label = { Text("الكمية المضافة (${targetMat.baseUnit})") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = addAmountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            onAddStock(targetMat.id, amount)
                            showStockDialog = null
                            addAmountText = ""
                        }
                    }
                ) {
                    Text("تأكيد الإضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStockDialog = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminPurchasesTab(
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("المشتريات والتوريد (${purchases.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purchases) { p ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("فاتورة شراء: ${p.invoiceNumber} • المورد: ${p.supplierName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("الإجمالي: ${p.totalAmount} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDebtsTab(
    customers: List<CustomerEntity>,
    onAddCustomer: (name: String, phone: String, creditLimit: Double, allowDebt: Boolean) -> Unit = { _, _, _, _ -> }
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("1000") }
    var allowDebt by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("العملاء والديون (${customers.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة عميل")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(customers) { c ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("هاتف: ${c.phone} • دين: ${c.currentDebt} ريال • سقف: ${c.creditLimit} ريال", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة عميل جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم العميل") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true)
                    OutlinedTextField(value = limitText, onValueChange = { limitText = it }, label = { Text("سقف الدين المسموح (ريال)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitText.toDoubleOrNull() ?: 1000.0
                        if (name.isNotBlank()) {
                            onAddCustomer(name, phone, limit, allowDebt)
                            showDialog = false
                            name = ""
                            phone = ""
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminExpensesTab(
    expenses: List<ExpenseEntity>,
    onAddExpense: (title: String, amount: Double, category: String, paidTo: String, notes: String) -> Unit = { _, _, _, _, _ -> }
) {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("تشغيلي") }
    var paidTo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سجل المصروفات (${expenses.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("تسجيل مصروف")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(expenses) { e ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("المبلغ: ${e.amount} ريال • التصنيف: ${e.category} • المدفوع لـ: ${e.paidTo}", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("تسجيل مصروف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("بيان المصروف") }, singleLine = true)
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("المبلغ (ريال)") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("التصنيف (تشغيلي، صيانة، نظافة)") }, singleLine = true)
                    OutlinedTextField(value = paidTo, onValueChange = { paidTo = it }, label = { Text("المدفوع له") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amount > 0) {
                            onAddExpense(title, amount, category, paidTo, notes)
                            showDialog = false
                            title = ""
                            amountText = ""
                        }
                    }
                ) {
                    Text("حفظ المصروف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminShiftsTab(
    shifts: List<ShiftEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل الورديات (${shifts.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shifts) { s ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(s.startTime))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("شفت #${s.shiftNumber} - ${s.userName} (${s.status.titleAr})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("البداية: $dateStr • افتتاحي: ${s.openingCash} ريال • نقدي: ${s.totalCashSales} ريال", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEmployeesTab(
    users: List<UserEntity>,
    onAddEmployee: (name: String, username: String, pin: String, role: UserRole, phone: String) -> Unit,
    onToggleActive: (userId: String, active: Boolean) -> Unit,
    onRemoveUser: (userId: String) -> Unit = {},
    canManageUsers: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    var userPendingRemoval by remember { mutableStateOf<UserEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.CASHIER) }
    var showRoleMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الموظفون والمستخدمون (${users.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (canManageUsers) {
                Button(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة موظف")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users) { u ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("اسم المستخدم: @${u.username} • الدور: ${u.role.titleAr} • PIN: محمي ومشفّر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = u.isActive,
                                enabled = canManageUsers && u.role != UserRole.OWNER,
                                onCheckedChange = { onToggleActive(u.id, it) }
                            )
                            if (canManageUsers && u.role != UserRole.OWNER) {
                                IconButton(onClick = { userPendingRemoval = u }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "إزالة المستخدم",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingRemoval = userPendingRemoval
    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = { userPendingRemoval = null },
            title = { Text("تأكيد إزالة المستخدم") },
            text = { Text("هل تريد إزالة المستخدم ${pendingRemoval.name}؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveUser(pendingRemoval.id)
                        userPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("إزالة المستخدم") }
            },
            dismissButton = {
                TextButton(onClick = { userPendingRemoval = null }) { Text("إلغاء") }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة موظف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("رمز PIN") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true)
                    Box {
                        TextButton(onClick = { showRoleMenu = true }) {
                            Text("الصلاحية: ${selectedRole.titleAr}")
                        }
                        DropdownMenu(
                            expanded = showRoleMenu,
                            onDismissRequest = { showRoleMenu = false }
                        ) {
                            UserRole.values().filter { it != UserRole.OWNER }.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.titleAr) },
                                    onClick = {
                                        selectedRole = role
                                        showRoleMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEmployee(name, username, pin, selectedRole, phone)
                        showDialog = false
                    }
                ) {
                    Text("حفظ الموظف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminReportsTab(
    sales: List<SaleEntity>,
    expenses: List<ExpenseEntity>
) {
    val totalSales = sales.sumOf { it.netAmount }
    val totalExpenses = expenses.sumOf { it.amount }
    val netProfit = totalSales - totalExpenses

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("التقارير المالية والأرباح", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("إجمالي المبيعات", fontSize = 12.sp)
                    Text("$totalSales ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("إجمالي المصروفات", fontSize = 12.sp)
                    Text("$totalExpenses ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("صافي الربح", fontSize = 12.sp)
                    Text("$netProfit ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogTab(
    logs: List<AuditLogEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل التدقيق والأمان (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { log ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(log.timestamp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${log.userName} (${log.userRole}): ${log.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (log.notes.isNotEmpty()) {
                            Text(log.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(
    users: List<UserEntity>,
    onSaveSetting: (key: String, value: String) -> Unit = { _, _ -> },
    initialAutoLockMinutes: String = "5"
) {
    var gymName by remember { mutableStateOf("نادي Power Home Gym") }
    var gatewayPort by remember { mutableStateOf("8080") }
    var autoLockMinutes by remember(initialAutoLockMinutes) { mutableStateOf(initialAutoLockMinutes) }
    var autoStartGateway by remember { mutableStateOf(true) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("إعدادات النظام وبوابة الرسائل والأمان", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بيانات المنشأة وبوابة نقاط البيع:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = gymName,
                        onValueChange = { gymName = it },
                        label = { Text("اسم المنشأة / النادي") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gatewayPort,
                        onValueChange = { gatewayPort = it },
                        label = { Text("منفذ بوابة الـ Wi-Fi المحلية (Port)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = autoLockMinutes,
                        onValueChange = { autoLockMinutes = it },
                        label = { Text("مدة قفل الجلسة عند عدم النشاط (1–60 دقيقة؛ الافتراضي 5)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الاسترداد التلقائي بعد إعادة التشغيل (Restart Recovery):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("تشغيل خادم بوابة الرسائل فور إقلاع الجهاز لضمان عدم ضياع أي حوالة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoStartGateway, onCheckedChange = { autoStartGateway = it })
                    }
                    Button(
                        onClick = {
                            onSaveSetting("gym_name", gymName)
                            onSaveSetting("gateway_port", gatewayPort)
                            onSaveSetting("auto_lock_minutes", autoLockMinutes)
                            onSaveSetting("auto_start_gateway", autoStartGateway.toString())
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معلومات النظام والأمان:", fontWeight = FontWeight.Bold)
                    Text("نظام التشغيل: POWER FEUL POS & SMS GATEWAY 1.0", fontSize = 13.sp)
                    Text("قاعدة البيانات: SQLite / Room (Local-First Zero-Cloud)", fontSize = 13.sp)
                    Text("المحافظ المدعومة: جيب (JEEB) • فلوسك (FLOOSAK) • جوالي (JAWALI)", fontSize = 13.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("المستخدمون المصرح لهم بالدخول (${users.size}):", fontWeight = FontWeight.Bold)
                    users.forEach { u ->
                        Text("• ${u.name} — الدور: ${u.role.titleAr} (${if (u.isActive) "نشط" else "معطل"})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
