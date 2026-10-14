package com.example.ui.wallet

import androidx.compose.foundation.clickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.data.local.entity.WalletEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.domain.model.WalletTransferStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    currentUser: UserEntity,
    currentShift: ShiftEntity?,
    wallets: List<WalletEntity>,
    transactions: List<WalletTransactionEntity>,
    onBack: () -> Unit,
    onIngestTestSms: (sender: String, body: String) -> Unit,
    onUpdateStatus: (txId: String, newStatus: WalletTransferStatus, reason: String) -> Unit,
    onReassignShift: (txId: String, newShiftId: String, cashierId: String, cashierName: String, reason: String) -> Unit
) {
    val canViewAll = PermissionChecker.hasPermission(currentUser.role, AppPermission.VIEW_ALL_TRANSFERS)
    val canReassign = PermissionChecker.hasPermission(currentUser.role, AppPermission.REASSIGN_TRANSFER)


    var selectedWalletFilter by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<WalletTransferStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showTestSmsDialog by remember { mutableStateOf(false) }

    // Dialog state for reject
    var rejectingTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    // Dialog state for details
    var viewingTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }

    // Scope data before applying search/status filters. A cashier with no open
    // shift must see no wallet history (including rows with a null shiftId).
    // Cashiers are limited to today's transactions in their current shift.
    val scopedTransactions = remember(transactions, currentShift?.id, canViewAll) {
        visibleWalletTransactionsForUser(
            transactions = transactions,
            canViewAll = canViewAll,
            currentShiftId = currentShift?.id,
            nowMillis = System.currentTimeMillis()
        )
    }
    val visibleTransactions = remember(scopedTransactions, selectedWalletFilter, selectedStatusFilter, searchQuery) {
        scopedTransactions.filter { tx ->
            val matchesWallet = selectedWalletFilter == null || tx.walletCode == selectedWalletFilter
            val matchesStatus = selectedStatusFilter == null || tx.status == selectedStatusFilter
            val matchesSearch = searchQuery.isBlank() ||
                    tx.transactionId.contains(searchQuery, ignoreCase = true) ||
                    tx.sender.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)
            matchesWallet && matchesStatus && matchesSearch
        }
    }

    // Totals obey the same shift/day restriction as the visible list.
    val jeebTxs = scopedTransactions.filter { it.walletCode == "JEEB" }
    val floosakTxs = scopedTransactions.filter { it.walletCode == "FLOOSAK" }
    val jawaliTxs = scopedTransactions.filter { it.walletCode == "JAWALI" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("إدارة الحوالات والمحافظ المالية (SMS)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val scopeText = if (canViewAll) "عرض شامل (المدير / المشرف)" else "حوالات الشفت الحالي فقط (#${currentShift?.shiftNumber ?: "لا يوجد"})"
                        Text(scopeText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // SMS Gateway Ingestion Test Dialog Button
                    FilledTonalButton(
                        onClick = { showTestSmsDialog = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استقبال SMS تجريبي", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 1. CARDS PER WALLET: [ جيب ] [ فلوسك ] [ جوالي ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // JEEB CARD
                WalletSummaryCard(
                    title = "جيـب",
                    count = jeebTxs.size,
                    total = jeebTxs.sumOf { it.amount },
                    color = Color(0xFFE65100),
                    isSelected = selectedWalletFilter == "JEEB",
                    onClick = {
                        selectedWalletFilter = if (selectedWalletFilter == "JEEB") null else "JEEB"
                    },
                    modifier = Modifier.weight(1f)
                )

                // FLOOSAK CARD
                WalletSummaryCard(
                    title = "فلوسك",
                    count = floosakTxs.size,
                    total = floosakTxs.sumOf { it.amount },
                    color = Color(0xFF1565C0),
                    isSelected = selectedWalletFilter == "FLOOSAK",
                    onClick = {
                        selectedWalletFilter = if (selectedWalletFilter == "FLOOSAK") null else "FLOOSAK"
                    },
                    modifier = Modifier.weight(1f)
                )

                // JAWALI CARD
                WalletSummaryCard(
                    title = "جوالي",
                    count = jawaliTxs.size,
                    total = jawaliTxs.sumOf { it.amount },
                    color = Color(0xFF2E7D32),
                    isSelected = selectedWalletFilter == "JAWALI",
                    onClick = {
                        selectedWalletFilter = if (selectedWalletFilter == "JAWALI") null else "JAWALI"
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. SEARCH & STATUS FILTERS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث برقم العملية أو المرسل أو المبلغ...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                // Status chip dropdown
                var statusExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { statusExpanded = true }) {
                        Text(selectedStatusFilter?.titleAr ?: "جميع الحالات")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("الكل") },
                            onClick = { selectedStatusFilter = null; statusExpanded = false }
                        )
                        WalletTransferStatus.values().forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.titleAr) },
                                onClick = { selectedStatusFilter = st; statusExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. TRANSACTIONS LIST
            if (visibleTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!canViewAll) "لا توجد أي حوالات مرتبطة بالشفت الحالي" else "لا توجد حوالات تطابق معايير البحث",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTransactions) { tx ->
                        WalletTransactionItem(
                            tx = tx,
                            canManage = canReassign,
                            onClick = { viewingTx = tx },
                            onConfirm = { onUpdateStatus(tx.id, WalletTransferStatus.CONFIRMED, "تأكيد يدوي من المشرف") },
                            onReject = { rejectingTx = tx },
                            onMarkDuplicate = { onUpdateStatus(tx.id, WalletTransferStatus.DUPLICATE, "تحديد كمكررة") }
                        )
                    }
                }
            }
        }
    }

    // Test SMS Ingestion Dialog
    if (showTestSmsDialog) {
        TestSmsDialog(
            onDismiss = { showTestSmsDialog = false },
            onSend = { sender, body ->
                onIngestTestSms(sender, body)
                showTestSmsDialog = false
            }
        )
    }

    // Reject Reason Dialog
    if (rejectingTx != null) {
        AlertDialog(
            onDismissRequest = { rejectingTx = null },
            title = { Text("رفض الحوالة") },
            text = {
                Column {
                    Text("يرجى كتابة سبب رفض الحوالة رقم ${rejectingTx!!.transactionId}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("سبب الرفض...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStatus(rejectingTx!!.id, WalletTransferStatus.REJECTED, rejectReason)
                        rejectingTx = null
                        rejectReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الرفض")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingTx = null }) { Text("إلغاء") }
            }
        )
    }

    // View Details Dialog
    if (viewingTx != null) {
        val tx = viewingTx!!
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(tx.receivedAt))
        AlertDialog(
            onDismissRequest = { viewingTx = null },
            title = { Text("تفاصيل الحوالة [${tx.walletCode}]", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المبلغ: ${tx.amount} ريال", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("رقم العملية: ${tx.transactionId}")
                    Text("المرسل: ${tx.sender}")
                    Text("وقت الاستلام الحقيقي للرسالة: $dateStr")
                    Text("الشفت: ${tx.shiftId ?: "خارج الدوام (OUT_OF_SHIFT)"}")
                    Text("المحاسب: ${tx.cashierName ?: "غير محدد"}")
                    Text("الحالة: ${tx.status.titleAr}")
                    if (tx.rejectionReason.isNotEmpty()) {
                        Text("سبب الرفض: ${tx.rejectionReason}", color = MaterialTheme.colorScheme.error)
                    }
                    if (tx.notes.isNotEmpty()) {
                        Text("ملاحظات: ${tx.notes}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingTx = null }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
fun WalletSummaryCard(
    title: String,
    count: Int,
    total: Double,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Spacer(modifier = Modifier.height(6.dp))
            Text("عدد الحوالات: $count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("الإجمالي: $total ريال", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun WalletTransactionItem(
    tx: WalletTransactionEntity,
    canManage: Boolean,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onMarkDuplicate: () -> Unit
) {
    val dateStr = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.ENGLISH).format(Date(tx.receivedAt))
    val statusColor = when (tx.status) {
        WalletTransferStatus.CONFIRMED -> Color(0xFF2E7D32)
        WalletTransferStatus.MATCHED, WalletTransferStatus.RECEIVED -> Color(0xFF1565C0)
        WalletTransferStatus.OUT_OF_SHIFT -> Color(0xFFE65100)
        WalletTransferStatus.DUPLICATE -> Color(0xFF757575)
        WalletTransferStatus.REJECTED -> Color(0xFFC62828)
        WalletTransferStatus.NEEDS_REVIEW -> Color(0xFFF57F17)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tx.walletCode, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${tx.amount} ريال",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = tx.status.titleAr,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "رقم العملية: ${tx.transactionId} • المرسل: ${tx.sender}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "وقت الوصول: $dateStr • ${if (tx.shiftId != null) "شفت: ${tx.cashierName ?: ""}" else "خارج الدوام"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canManage && (tx.status == WalletTransferStatus.MATCHED || tx.status == WalletTransferStatus.RECEIVED || tx.status == WalletTransferStatus.OUT_OF_SHIFT)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onConfirm) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "تأكيد", tint = Color(0xFF2E7D32))
                    }
                    IconButton(onClick = onReject) {
                        Icon(Icons.Default.Cancel, contentDescription = "رفض", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Uses the same message shapes as real wallet notifications so the simulator
 * tests parsing against provider-like text instead of a special demo-only phrase.
 */
internal fun buildTestWalletSms(
    walletCode: String,
    amount: String,
    transactionId: String,
    sender: String
): String = when (walletCode) {
    "JEEB" -> "اضيف $amount ر.ي تحويل مشترك رص:75000ر.ي من $sender-777123456"
    "FLOOSAK" -> "استلمت حوالة من $sender بمبلغ $amount ر.ي رصيدك 25000 ر.ي"
    else -> "استلمت YER $amount كقيمة مشتريات بمرجع $transactionId من $sender"
}

@Composable
fun TestSmsDialog(
    onDismiss: () -> Unit,
    onSend: (sender: String, body: String) -> Unit
) {
    var selectedWallet by remember { mutableStateOf("JEEB") }
    var amountText by remember { mutableStateOf("5000") }
    var senderText by remember { mutableStateOf("عبدالله الجميلي") }
    var txIdText by remember { mutableStateOf("468486397181") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محاكاة استقبال SMS من المحفظة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اختر المحفظة:", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JEEB" to "جيب", "FLOOSAK" to "فلوسك", "JAWALI" to "جوالي").forEach { (code, name) ->
                        FilterChip(
                            selected = selectedWallet == code,
                            onClick = { selectedWallet = code },
                            label = { Text(name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ (ريال)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = txIdText,
                    onValueChange = { txIdText = it },
                    label = { Text("رقم العملية (TxID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderText,
                    onValueChange = { senderText = it },
                    label = { Text("رقم/اسم المرسل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val senderClean = senderText.ifBlank { "عميل تجريبي" }
                    val amountClean = amountText.ifBlank { "5000" }
                    val txIdClean = txIdText.ifBlank { "468486397181" }
                    val body = buildTestWalletSms(
                        walletCode = selectedWallet,
                        amount = amountClean,
                        transactionId = txIdClean,
                        sender = senderClean
                    )
                    onSend(selectedWallet, body)
                }
            ) {
                Text("إرسال الرسالة إلى النظام")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}