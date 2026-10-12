package com.example.ui.admin

import androidx.compose.foundation.layout.*
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

@Composable
fun AdminBackupTab(
    onExportSalesCsv: () -> Unit,
    onCreateBackupJson: () -> Unit,
    backupStatusMessage: String?
) {
    var showConfirmRestore by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "نظام تصدير واستيراد البيانات والنسخ الاحتياطي",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "يمكنك تصدير فواتير المبيعات، المخزون، الحوالات والعملاء كملفات CSV / Excel، أو إنشاء نسخة احتياطية محلية كاملة لقاعدة بيانات POS واستعادتها بأمان.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!backupStatusMessage.isNullOrEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(backupStatusMessage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Section 1: Export Data
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("أولاً: تصدير البيانات (Export)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("تصدير جداول البيانات بصيغ CSV و Excel متوافقة مع البرامج الإدارية والحسابية.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onExportSalesCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير المبيعات (CSV)")
                    }

                    Button(
                        onClick = onExportSalesCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير المخزون والتكاليف (CSV)")
                    }
                }
            }
        }

        // Section 2: Full Database Backup & Restore
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ثانياً: النسخ الاحتياطي الكامل (Full Backup & Restore)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("حفظ نسخة احتياطية مشفرة تشمل جميع المبيعات، المخزون، الحسابات، الموظفين، وسجلات التدقيق.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCreateBackupJson,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إنشاء نسخة احتياطية كاملة (JSON)")
                    }

                    OutlinedButton(
                        onClick = { showConfirmRestore = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة من نسخة سابقة")
                    }
                }
            }
        }
    }

    if (showConfirmRestore) {
        AlertDialog(
            onDismissRequest = { showConfirmRestore = false },
            title = { Text("استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل أنت متأكد من استعادة النسخة الاحتياطية؟ سيتم التحقق من سلامة وصحة الملف قبل استبدال أي بيانات.")
            },
            confirmButton = {
                Button(
                    onClick = { showConfirmRestore = false }
                ) {
                    Text("بدء الفحص والاستعادة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestore = false }) { Text("إلغاء") }
            }
        )
    }
}
