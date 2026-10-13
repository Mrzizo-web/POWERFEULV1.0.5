package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.data.engine.NewPurchaseItem
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

private data class PurchaseLineDraft(
    val materialId: String,
    val quantityText: String = "",
    val unitPriceText: String = ""
)

@Composable
fun AdminPurchasesTab(
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>,
    rawMaterials: List<RawMaterialEntity>,
    onCreatePurchase: (supplierId: String, supplierName: String, invoiceNumber: String, items: List<NewPurchaseItem>, notes: String) -> Unit,
    onAddSupplier: (name: String, phone: String, address: String, notes: String) -> Unit
) {
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var supplierMenuExpanded by remember { mutableStateOf(false) }
    var invoiceNumber by remember { mutableStateOf("") }
    var purchaseNotes by remember { mutableStateOf("") }
    var selectedSupplierId by remember { mutableStateOf("") }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    var purchaseLines by remember { mutableStateOf(listOf<PurchaseLineDraft>()) }

    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var supplierAddress by remember { mutableStateOf("") }
    var supplierNotes by remember { mutableStateOf("") }
    var supplierError by remember { mutableStateOf<String?>(null) }

    val activeSuppliers = suppliers.filter { it.isActive }
    val selectedSupplier = activeSuppliers.firstOrNull { it.id == selectedSupplierId }
    val totalPreview = purchaseLines.sumOf { line ->
        val quantity = line.quantityText.toDoubleOrNull() ?: 0.0
        val unitPrice = line.unitPriceText.toDoubleOrNull() ?: 0.0
        (quantity * unitPrice).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("المشتريات والتوريد (${purchases.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("تسجيل فاتورة شراء يحدّث المخزون ومتوسط التكلفة داخل معاملة واحدة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                supplierName = ""
                supplierPhone = ""
                supplierAddress = ""
                supplierNotes = ""
                supplierError = null
                showSupplierDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة مورد")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = {
                    invoiceNumber = ""
                    purchaseNotes = ""
                    purchaseError = null
                    selectedSupplierId = activeSuppliers.firstOrNull()?.id.orEmpty()
                    purchaseLines = listOf(PurchaseLineDraft(rawMaterials.firstOrNull()?.id.orEmpty()))
                    showPurchaseDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("فاتورة شراء")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (activeSuppliers.isEmpty()) {
            Text(
                "أضف بيانات المورد أولًا، ثم أنشئ فاتورة شراء.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(purchases, key = { it.id }) { purchase ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "فاتورة: ${purchase.invoiceNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text("المورد: ${purchase.supplierName}", fontSize = 12.sp)
                        Text("الإجمالي: ${purchase.totalAmount} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        if (purchase.notes.isNotBlank()) {
                            Text(purchase.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showPurchaseDialog) {
        AlertDialog(
            onDismissRequest = { showPurchaseDialog = false },
            title = { Text("تسجيل فاتورة شراء متعددة المواد") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("المورد", fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { supplierMenuExpanded = true },
                            enabled = activeSuppliers.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedSupplier?.name ?: "اختر المورد")
                        }
                        DropdownMenu(
                            expanded = supplierMenuExpanded,
                            onDismissRequest = { supplierMenuExpanded = false }
                        ) {
                            activeSuppliers.forEach { supplier ->
                                DropdownMenuItem(
                                    text = { Text(supplier.name) },
                                    onClick = {
                                        selectedSupplierId = supplier.id
                                        supplierMenuExpanded = false
                                        purchaseError = null
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it; purchaseError = null },
                        label = { Text("رقم فاتورة المورد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider()
                    Text("مواد الفاتورة", fontWeight = FontWeight.SemiBold)
                    purchaseLines.forEachIndexed { index, line ->
                        var materialMenuExpanded by remember(index) { mutableStateOf(false) }
                        val selectedMaterial = rawMaterials.firstOrNull { it.id == line.materialId }
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { materialMenuExpanded = true },
                                            enabled = rawMaterials.isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(selectedMaterial?.name ?: "اختر المادة الخام")
                                        }
                                        DropdownMenu(
                                            expanded = materialMenuExpanded,
                                            onDismissRequest = { materialMenuExpanded = false }
                                        ) {
                                            rawMaterials.forEach { material ->
                                                DropdownMenuItem(
                                                    text = { Text("${material.name} (${material.baseUnit})") },
                                                    onClick = {
                                                        purchaseLines = purchaseLines.mapIndexed { i, old ->
                                                            if (i == index) old.copy(materialId = material.id) else old
                                                        }
                                                        materialMenuExpanded = false
                                                        purchaseError = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        enabled = purchaseLines.size > 1,
                                        onClick = {
                                            purchaseLines = purchaseLines.filterIndexed { i, _ -> i != index }
                                            purchaseError = null
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف السطر")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = line.quantityText,
                                        onValueChange = { value ->
                                            purchaseLines = purchaseLines.mapIndexed { i, old ->
                                                if (i == index) old.copy(quantityText = value) else old
                                            }
                                            purchaseError = null
                                        },
                                        label = { Text("الكمية (${selectedMaterial?.baseUnit ?: "الوحدة"})") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = line.unitPriceText,
                                        onValueChange = { value ->
                                            purchaseLines = purchaseLines.mapIndexed { i, old ->
                                                if (i == index) old.copy(unitPriceText = value) else old
                                            }
                                            purchaseError = null
                                        },
                                        label = { Text("سعر الوحدة") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                val qty = line.quantityText.toDoubleOrNull() ?: 0.0
                                val price = line.unitPriceText.toDoubleOrNull() ?: 0.0
                                Text(
                                    "إجمالي السطر: " + ((qty * price).takeIf { it.isFinite() } ?: 0.0) + " ريال",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        enabled = rawMaterials.isNotEmpty(),
                        onClick = {
                            purchaseLines = purchaseLines + PurchaseLineDraft(rawMaterials.first().id)
                            purchaseError = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة مادة أخرى")
                    }
                    OutlinedTextField(
                        value = purchaseNotes,
                        onValueChange = { purchaseNotes = it },
                        label = { Text("ملاحظات الفاتورة (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("إجمالي الفاتورة: $totalPreview ريال", fontWeight = FontWeight.Bold)
                    if (purchaseError != null) {
                        Text(purchaseError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val supplier = activeSuppliers.firstOrNull { it.id == selectedSupplierId }
                    val lines = purchaseLines.mapNotNull { line ->
                        val material = rawMaterials.firstOrNull { it.id == line.materialId }
                        val quantity = line.quantityText.toDoubleOrNull()
                        val unitPrice = line.unitPriceText.toDoubleOrNull()
                        if (material == null || quantity == null || unitPrice == null ||
                            !quantity.isFinite() || quantity <= 0.0 ||
                            !unitPrice.isFinite() || unitPrice < 0.0
                        ) null else NewPurchaseItem(
                            rawMaterialId = material.id,
                            rawMaterialName = material.name,
                            quantity = quantity,
                            unit = material.baseUnit,
                            unitPrice = unitPrice
                        )
                    }
                    when {
                        supplier == null -> purchaseError = "اختر موردًا نشطًا"
                        invoiceNumber.isBlank() -> purchaseError = "رقم فاتورة المورد مطلوب"
                        rawMaterials.isEmpty() -> purchaseError = "أضف المواد الخام أولًا"
                        purchaseLines.isEmpty() -> purchaseError = "أضف مادة واحدة على الأقل"
                        lines.size != purchaseLines.size -> purchaseError = "راجع الكميات وأسعار الوحدات؛ الكمية أكبر من صفر والسعر غير سالب"
                        else -> {
                            onCreatePurchase(supplier.id, supplier.name, invoiceNumber.trim(), lines, purchaseNotes.trim())
                            showPurchaseDialog = false
                        }
                    }
                }) { Text("حفظ الفاتورة وتحديث المخزون") }
            },
            dismissButton = {
                TextButton(onClick = { showPurchaseDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showSupplierDialog) {
        AlertDialog(
            onDismissRequest = { showSupplierDialog = false },
            title = { Text("إضافة مورد") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it; supplierError = null },
                        label = { Text("اسم المورد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierPhone,
                        onValueChange = { supplierPhone = it },
                        label = { Text("رقم الهاتف (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierAddress,
                        onValueChange = { supplierAddress = it },
                        label = { Text("العنوان (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierNotes,
                        onValueChange = { supplierNotes = it },
                        label = { Text("ملاحظات (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (supplierError != null) {
                        Text(supplierError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (supplierName.isBlank()) {
                        supplierError = "اسم المورد مطلوب"
                    } else if (suppliers.any { it.name.trim().equals(supplierName.trim(), ignoreCase = true) }) {
                        supplierError = "يوجد مورد بهذا الاسم بالفعل"
                    } else {
                        onAddSupplier(supplierName.trim(), supplierPhone.trim(), supplierAddress.trim(), supplierNotes.trim())
                        showSupplierDialog = false
                        supplierName = ""
                        supplierPhone = ""
                        supplierAddress = ""
                        supplierNotes = ""
                    }
                }) { Text("حفظ المورد") }
            },
            dismissButton = {
                TextButton(onClick = { showSupplierDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminDebtsTab(
    customers: List<CustomerEntity>,
    hasOpenShift: Boolean,
    onAddCustomer: (name: String, phone: String, creditLimit: Double, allowDebt: Boolean) -> Unit = { _, _, _, _ -> },
    onPayDebt: (customerId: String, amount: Double, isCash: Boolean, notes: String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("1000") }
    var allowDebt by remember { mutableStateOf(true) }

    var payingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentNotes by remember { mutableStateOf("") }
    var payCash by remember { mutableStateOf(true) }
    var paymentError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("العملاء والديون (\${customers.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة عميل")
            }
        }
        if (!hasOpenShift) {
            Text(
                "لا يوجد شفت مفتوح: تحصيل الدين نقدًا غير متاح، ويمكن تسجيل السداد بالمحفظة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(customers, key = { it.id }) { customer ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "هاتف: \${customer.phone} • الدين: \${customer.currentDebt} ريال • السقف: \${customer.creditLimit} ريال",
                            fontSize = 12.sp
                        )
                        if (customer.currentDebt > 0.0) {
                            Button(
                                onClick = {
                                    payingCustomer = customer
                                    paymentAmountText = customer.currentDebt.toString()
                                    paymentNotes = ""
                                    payCash = hasOpenShift
                                    paymentError = null
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحصيل سداد دين")
                            }
                        } else {
                            Text("لا يوجد دين مستحق", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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
                Button(onClick = {
                    val limit = limitText.toDoubleOrNull()
                    when {
                        name.isBlank() -> Unit
                        limit == null || !limit.isFinite() || limit < 0.0 -> Unit
                        else -> {
                            onAddCustomer(name.trim(), phone.trim(), limit, allowDebt)
                            showDialog = false
                            name = ""
                            phone = ""
                        }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }

    payingCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = { payingCustomer = null },
            title = { Text("سداد دين العميل: \${customer.name}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("الرصيد المستحق: \${customer.currentDebt} ريال")
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it; paymentError = null },
                        label = { Text("مبلغ السداد (ريال)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = payCash,
                            onClick = { payCash = true; paymentError = null },
                            enabled = hasOpenShift
                        )
                        Text("نقدي", color = if (hasOpenShift) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = !payCash,
                            onClick = { payCash = false; paymentError = null }
                        )
                        Text("محفظة إلكترونية")
                    }
                    if (payCash && !hasOpenShift) {
                        Text("يجب فتح شفت لتسجيل سداد نقدي.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("ملاحظات (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (paymentError != null) {
                        Text(paymentError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = paymentAmountText.toDoubleOrNull()
                    when {
                        amount == null || !amount.isFinite() || amount <= 0.0 ->
                            paymentError = "أدخل مبلغ سداد موجبًا وصالحًا"
                        amount > customer.currentDebt ->
                            paymentError = "مبلغ السداد لا يمكن أن يتجاوز الدين المستحق"
                        payCash && !hasOpenShift ->
                            paymentError = "افتح شفتًا قبل تحصيل الدين نقدًا"
                        else -> {
                            onPayDebt(customer.id, amount, payCash, paymentNotes.trim())
                            payingCustomer = null
                        }
                    }
                }) { Text("تأكيد السداد") }
            },
            dismissButton = {
                TextButton(onClick = { payingCustomer = null }) { Text("إلغاء") }
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
    canManageUsers: Boolean = true,
    allowedRoles: List<UserRole> = UserRole.values().filter { it != UserRole.OWNER }
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
                            allowedRoles.forEach { role ->
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
