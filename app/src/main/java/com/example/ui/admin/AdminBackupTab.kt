package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminBackupTab(
    onExportSalesCsv: (Uri) -> Unit,
    onExportInventoryCsv: (Uri) -> Unit,
    onExportCustomersCsv: (Uri) -> Unit = {},
    onExportExpensesCsv: (Uri) -> Unit = {},
    onExportDebtTransactionsCsv: (Uri) -> Unit = {},
    onCreateBackup: (Uri, String) -> Unit,
    onRestoreBackup: (Uri, String) -> Unit,
    backupStatusMessage: String?
) {
    var backupPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    val salesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) onExportSalesCsv(uri)
    }
    val inventoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) onExportInventoryCsv(uri)
    }

    val customersLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> if (uri != null) onExportCustomersCsv(uri) }
    val expensesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> if (uri != null) onExportExpensesCsv(uri) }
    val debtsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> if (uri != null) onExportDebtTransactionsCsv(uri) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val error = validateBackupPassword(backupPassword)
            when {
                error != null -> localError = error
                confirmPassword != backupPassword -> localError = "تأكيد كلمة المرور لا يطابق كلمة المرور"
                else -> {
                    localError = null
                    onCreateBackup(uri, backupPassword)
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val error = validateBackupPassword(backupPassword)
            if (error != null) {
                localError = error
            } else {
                localError = null
                onRestoreBackup(uri, backupPassword)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("النسخ الاحتياطي والتصدير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(
            "تُحفظ ملفات CSV في الموقع الذي تختاره. النسخة الكاملة تشمل جداول قاعدة البيانات وتُشفّر بكلمة مرور تختارها؛ احتفظ بكلمة المرور منفصلة، فلا يمكن استعادتها عند نسيانها.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = { salesLauncher.launch("POWER_FEUL_POS_sales.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("تصدير سجل المبيعات CSV")
        }
        OutlinedButton(
            onClick = { inventoryLauncher.launch("POWER_FEUL_POS_inventory.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("تصدير المخزون والتكاليف CSV")
        }

        OutlinedButton(
            onClick = { customersLauncher.launch("POWER_FEUL_POS_customers.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("تصدير العملاء وأرصدة الديون CSV")
        }
        OutlinedButton(
            onClick = { expensesLauncher.launch("POWER_FEUL_POS_expenses.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("تصدير المصروفات CSV")
        }
        OutlinedButton(
            onClick = { debtsLauncher.launch("POWER_FEUL_POS_debt_transactions.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("تصدير سجل حركات الديون والسداد CSV")
        }

        HorizontalDivider()
        Text("نسخة احتياطية كاملة مشفّرة", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it; localError = null },
            label = { Text("كلمة مرور النسخة (12 حرفًا على الأقل)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; localError = null },
            label = { Text("تأكيد كلمة مرور النسخة") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val error = validateBackupPassword(backupPassword)
                when {
                    error != null -> localError = error
                    confirmPassword != backupPassword -> localError = "تأكيد كلمة المرور لا يطابق كلمة المرور"
                    else -> {
                        localError = null
                        backupLauncher.launch("POWER_FEUL_POS_backup.pfbackup")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Backup, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("إنشاء نسخة كاملة مشفّرة")
        }

        OutlinedButton(
            onClick = {
                val error = validateBackupPassword(backupPassword)
                if (error != null) localError = error else {
                    localError = null
                    showRestoreConfirmation = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("استعادة نسخة احتياطية")
        }

        Text(
            "تنبيه: الاستعادة تستبدل بيانات قاعدة الكاشير الحالية ببيانات النسخة، وتُرفض إذا كان هناك شفت مفتوح أو كان مخطط قاعدة البيانات مختلفًا. لا تستخدمها أثناء البيع.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        if (localError != null) {
            Text(localError!!, color = MaterialTheme.colorScheme.error)
        }
        if (backupStatusMessage != null) {
            Text(backupStatusMessage, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("تأكيد استبدال قاعدة البيانات") },
            text = {
                Text(
                    "ستُستبدل بيانات المبيعات والمستخدمين والمخزون والوصفات والديون والشفتات بالبيانات الموجودة في الملف. يجب أن تكون النسخة موثوقة وأن تتطابق كلمة المرور. سيُطلب منك تسجيل الدخول مجددًا."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirmation = false
                    restoreLauncher.launch(arrayOf("*/*"))
                }) { Text("اختيار ملف الاستعادة") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) { Text("إلغاء") }
            }
        )
    }
}

private fun validateBackupPassword(password: String): String? = when {
    password.length < 12 -> "استخدم كلمة مرور للنسخة الاحتياطية من 12 حرفًا على الأقل"
    password.length > 256 -> "كلمة مرور النسخة طويلة جدًا"
    else -> null
}
