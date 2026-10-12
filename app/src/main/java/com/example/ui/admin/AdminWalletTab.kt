package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GatewayQueueEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.WalletEntity
import com.example.data.local.entity.WalletSmsEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.domain.model.WalletTransferStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminWalletTab(
    wallets: List<WalletEntity>,
    transactions: List<WalletTransactionEntity>,
    smsInbox: List<WalletSmsEntity>,
    queueItems: List<GatewayQueueEntity> = emptyList(),
    isGatewayRunning: Boolean = false,
    gatewayIp: String = "127.0.0.1",
    gatewayPort: Int = 8080,
    shifts: List<ShiftEntity> = emptyList(),
    onToggleGateway: () -> Unit = {},
    onRetryQueueItem: (String) -> Unit = {},
    onRetryAllQueue: () -> Unit = {},
    onClearCompletedQueue: () -> Unit = {},
    onIngestTestSms: (sender: String, body: String) -> Unit = { _, _ -> },
    onUpdateStatus: (txId: String, newStatus: WalletTransferStatus, reason: String) -> Unit,
    onReassignShift: (txId: String, newShiftId: String, cashierId: String, cashierName: String, reason: String) -> Unit = { _, _, _, _, _ -> },
    onSaveSetting: (key: String, value: String) -> Unit = { _, _ -> }
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Transactions, 1: SMS Inbox, 2: Gateway & Wi-Fi, 3: Wallets
    var searchQuery by remember { mutableStateOf("") }
    var showTestSmsDialog by remember { mutableStateOf(false) }
    var viewingTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }
    var reassigningTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("إدارة الحوالات وبوابة الرسائل (POWER FEUL SMS GATEWAY)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = if (isGatewayRunning) "الخادم نشط على: http://$gatewayIp:$gatewayPort" else "خادم البوابة متوقف",
                    fontSize = 11.sp,
                    color = if (isGatewayRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showTestSmsDialog = true }) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("محاكاة SMS", fontSize = 12.sp)
                }
            }
        }

        TabRow(selectedTabIndex = selectedSection) {
            Tab(selected = selectedSection == 0, onClick = { selectedSection = 0 }, text = { Text("الحوالات (${transactions.size})") })
            Tab(selected = selectedSection == 1, onClick = { selectedSection = 1 }, text = { Text("صندوق الرسائل (${smsInbox.size})") })
            Tab(selected = selectedSection == 2, onClick = { selectedSection = 2 }, text = { Text("بوابة Wi-Fi والطابور (${queueItems.count { it.status != "COMPLETED" }})") })
            Tab(selected = selectedSection == 3, onClick = { selectedSection = 3 }, text = { Text("المحافظ (${wallets.size})") })
        }

        when (selectedSection) {
            0 -> {
                // Transactions section
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث برقم العملية أو المرسل أو الشفت...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val filtered = transactions.filter {
                    searchQuery.isBlank() ||
                            it.transactionId.contains(searchQuery, ignoreCase = true) ||
                            it.sender.contains(searchQuery, ignoreCase = true) ||
                            (it.cashierName ?: "").contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filtered) { tx ->
                        val linkedSmsCount = smsInbox.count { it.matchedTransactionId == tx.id }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingTx = tx }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${tx.walletCode}: ${tx.amount} ريال", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        if (linkedSmsCount > 1) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(" $linkedSmsCount رسائل ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("رقم العملية: ${tx.transactionId} • المرسل: ${tx.sender}", fontSize = 12.sp)
                                    Text("الشفت: ${tx.cashierName ?: "خارج الدوام"} • الحالة: ${tx.status.titleAr}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (tx.status != WalletTransferStatus.CONFIRMED) {
                                        IconButton(onClick = { onUpdateStatus(tx.id, WalletTransferStatus.CONFIRMED, "تأكيد إداري") }) {
                                            Icon(Icons.Default.Check, contentDescription = "تأكيد", tint = Color(0xFF2E7D32))
                                        }
                                    }
                                    if (tx.status != WalletTransferStatus.REJECTED) {
                                        IconButton(onClick = { onUpdateStatus(tx.id, WalletTransferStatus.REJECTED, "رفض إداري") }) {
                                            Icon(Icons.Default.Close, contentDescription = "رفض", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    IconButton(onClick = { viewingTx = tx }) {
                                        Icon(Icons.Default.Info, contentDescription = "تفاصيل", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Raw SMS Inbox
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(smsInbox) { sms ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(sms.receivedAt))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المرسل: ${sms.sender} [${sms.walletCode}]", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (sms.processingStatus.name == "PROCESSED") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                    ) {
                                        Text(
                                            text = " ${sms.processingStatus.titleAr} ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sms.processingStatus.name == "PROCESSED") Color(0xFF2E7D32) else Color(0xFFE65100)
                                        )
                                    }
                                }
                                Text(sms.body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الوقت: $dateStr", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    if (!sms.matchedTransactionId.isNullOrBlank()) {
                                        Text("معرف الحوالة: ${sms.matchedTransactionId?.take(8)}...", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Gateway & Local Wi-Fi + Offline Queue Section
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("خادم البوابة والاتصال بالشبكة المحلية (Wi-Fi)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    Switch(
                                        checked = isGatewayRunning,
                                        onCheckedChange = { onToggleGateway() }
                                    )
                                }

                                var gatewayToken by remember { mutableStateOf("") }
                                var gatewayDeviceId by remember { mutableStateOf("") }

                                Text("أمان بوابة SMS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = gatewayDeviceId,
                                    onValueChange = { gatewayDeviceId = it },
                                    label = { Text("Gateway Device ID المسموح") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = gatewayToken,
                                    onValueChange = { gatewayToken = it },
                                    label = { Text("Gateway Token المشترك") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        onSaveSetting("gateway_device_id", gatewayDeviceId.trim())
                                        onSaveSetting("gateway_token", gatewayToken.trim())
                                    },
                                    enabled = gatewayDeviceId.isNotBlank() && gatewayToken.isNotBlank()
                                ) {
                                    Text("حفظ بيانات أمان البوابة")
                                }

                                Text(
                                    text = "عند تفعيل الخادم، يمكن لأجهزة الكاشير ونقاط البيع (POS) الأخرى الاتصال بهذا الهاتف عبر شبكة الـ Wi-Fi المحلية لجلب الحوالات ومطابقة المبيعات لحظياً.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("عنوان البوابة المحلي (IP:Port):", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            Text("http://$gatewayIp:$gatewayPort", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isGatewayRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                        ) {
                                            Text(
                                                text = if (isGatewayRunning) "يعمل (LISTENING)" else "متوقف (OFFLINE)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGatewayRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Queue controls
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("طابور المزامنة وإعادة المحاولة التلقائية (Offline Queue):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = onClearCompletedQueue) {
                                    Text("تنظيف المكتمل", fontSize = 11.sp)
                                }
                                Button(onClick = onRetryAllQueue) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إعادة المحاولة الآن", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (queueItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("طابور المزامنة فارغ حالياً — جميع البيانات متزامنة محلياً", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        items(queueItems) { q ->
                            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(q.createdAt))
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${q.type} • $dateStr", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (q.status) {
                                                    "COMPLETED" -> Color(0xFFE8F5E9)
                                                    "FAILED" -> Color(0xFFFFEBEE)
                                                    else -> Color(0xFFFFF3E0)
                                                }
                                            ) {
                                                Text(
                                                    text = " ${q.status} (محاولة ${q.retryCount}/${q.maxRetries}) ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (q.status) {
                                                        "COMPLETED" -> Color(0xFF2E7D32)
                                                        "FAILED" -> Color(0xFFC62828)
                                                        else -> Color(0xFFE65100)
                                                    }
                                                )
                                            }
                                        }
                                        if (q.targetEndpoint.isNotBlank()) {
                                            Text("الهدف: ${q.targetEndpoint}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (q.errorMessage.isNotBlank()) {
                                            Text("الخطأ: ${q.errorMessage}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    if (q.status != "COMPLETED") {
                                        IconButton(onClick = { onRetryQueueItem(q.id) }) {
                                            Icon(Icons.Default.Replay, contentDescription = "إعادة المحاولة", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Wallets List
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(wallets) { w ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(w.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("الرمز: ${w.code}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(checked = w.enabled, onCheckedChange = {})
                            }
                        }
                    }
                }
            }
        }
    }

    // TRANSACTION DETAILS DIALOG WITH MULTI-SMS VIEWER
    val txToView = viewingTx
    if (txToView != null) {
        val linkedSmsList = smsInbox.filter { it.matchedTransactionId == txToView.id }
        AlertDialog(
            onDismissRequest = { viewingTx = null },
            title = { Text("تفاصيل الحوالة والرسائل المرتبطة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("المحفظة: ${txToView.walletCode} — المبلغ: ${txToView.amount} ريال", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("رقم العملية: ${txToView.transactionId}")
                    Text("المرسل: ${txToView.sender}")
                    Text("الشفت: ${txToView.cashierName ?: "خارج الدوام"}")
                    Text("الحالة: ${txToView.status.titleAr}")
                    if (txToView.notes.isNotBlank()) {
                        Text("ملاحظات: ${txToView.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("رسائل SMS المرتبطة بالعملية (${linkedSmsList.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (linkedSmsList.isEmpty()) {
                        Text("لا توجد رسائل مسجلة مرتبطة", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        linkedSmsList.forEach { sms ->
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(sms.receivedAt))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("من: ${sms.sender}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(timeStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(sms.body, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (txToView.status != WalletTransferStatus.CONFIRMED) {
                        Button(onClick = {
                            onUpdateStatus(txToView.id, WalletTransferStatus.CONFIRMED, "تأكيد إداري")
                            viewingTx = null
                        }) {
                            Text("تأكيد الحوالة")
                        }
                    }
                    if (txToView.status == WalletTransferStatus.OUT_OF_SHIFT && shifts.any { it.status.name == "OPEN" }) {
                        FilledTonalButton(onClick = {
                            reassigningTx = txToView
                            viewingTx = null
                        }) {
                            Text("ربط بشفت")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingTx = null }) { Text("إغلاق") }
            }
        )
    }

    // REASSIGN SHIFT DIALOG
    val txToReassign = reassigningTx
    if (txToReassign != null) {
        val openShifts = shifts.filter { it.status.name == "OPEN" }
        var selectedShift by remember { mutableStateOf(openShifts.firstOrNull()) }
        var reassignReason by remember { mutableStateOf("تم استلام الحوالة وتحديد شفت الكاشير المستلم") }

        AlertDialog(
            onDismissRequest = { reassigningTx = null },
            title = { Text("إعادة إسناد الحوالة إلى شفت مفتوح", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الحوالة: ${txToReassign.amount} ريال (${txToReassign.walletCode} - ${txToReassign.transactionId})")
                    Text("اختر الشفت المفتوح:", fontWeight = FontWeight.Bold)
                    openShifts.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedShift = s }
                        ) {
                            RadioButton(selected = selectedShift?.id == s.id, onClick = { selectedShift = s })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شفت #${s.shiftNumber} — الكاشير: ${s.userName}")
                        }
                    }
                    OutlinedTextField(
                        value = reassignReason,
                        onValueChange = { reassignReason = it },
                        label = { Text("سبب إعادة الإسناد") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = selectedShift
                        if (s != null) {
                            onReassignShift(txToReassign.id, s.id, s.userId, s.userName, reassignReason)
                            reassigningTx = null
                        }
                    },
                    enabled = selectedShift != null
                ) {
                    Text("إسناد إلى الشفت")
                }
            },
            dismissButton = {
                TextButton(onClick = { reassigningTx = null }) { Text("إلغاء") }
            }
        )
    }

    // SIMULATED TEST SMS DIALOG
    if (showTestSmsDialog) {
        var selectedWalletCode by remember { mutableStateOf("JEEB") }
        var amountText by remember { mutableStateOf("5000") }
        var senderText by remember { mutableStateOf("عبدالله الجميلي") }
        var txIdText by remember { mutableStateOf("468486397181") }

        AlertDialog(
            onDismissRequest = { showTestSmsDialog = false },
            title = { Text("محاكاة استقبال SMS المحفظة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختر المحفظة:", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("JEEB" to "جيب", "FLOOSAK" to "فلوسك", "JAWALI" to "جوالي").forEach { (code, name) ->
                            FilterChip(
                                selected = selectedWalletCode == code,
                                onClick = { selectedWalletCode = code },
                                label = { Text(name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ (ريال)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = txIdText,
                        onValueChange = { txIdText = it },
                        label = { Text("رقم العملية (TxID)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = senderText,
                        onValueChange = { senderText = it },
                        label = { Text("رقم/اسم المرسل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val body = when (selectedWalletCode) {
                            "JEEB" -> "اضيف $amountText ر.ي تحويل مشترك رص:72758.47ر.ي من $senderText-77366225"
                            "FLOOSAK" -> "استلمت حوالة من $senderText بمبلغ $amountText.00 ر.ي رصيدك 800.00 ر.ي"
                            else -> "لقد استلمت YER $amountText كقيمة مشتريات بمرجع $txIdText من $senderText"
                        }
                        onIngestTestSms(selectedWalletCode, body)
                        showTestSmsDialog = false
                    }
                ) {
                    Text("إرسال إلى النظام")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestSmsDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
