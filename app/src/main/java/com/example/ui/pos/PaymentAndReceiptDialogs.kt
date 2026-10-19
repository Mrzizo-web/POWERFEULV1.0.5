package com.example.ui.pos

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.CartItem
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.SaleEntity
import com.example.domain.model.PaymentMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentDialog(
    totalAmount: Double,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onConfirmSale: (method: PaymentMethod, cashReceived: Double, ref: String, custId: String?, proofPath: String?) -> Unit
) {
    val context = LocalContext.current
    var proofFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { succeeded ->
        val captured = pendingCaptureFile
        if (succeeded && captured != null && captured.isFile && captured.length() > 0L) {
            proofFile = captured
            cameraError = null
        } else {
            cameraError = "لم تكتمل عملية التقاط الصورة. يمكن إعادة المحاولة."
        }
        pendingCaptureFile = null
    }
    val launchCamera = {
        runCatching {
            val directory = File(context.filesDir, "payment_proofs")
            check(directory.isDirectory || directory.mkdirs()) { "تعذر إنشاء مجلد إثباتات الدفع" }
            val file = File(directory, "proof-${System.currentTimeMillis()}-${UUID.randomUUID()}.jpg")
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCaptureFile = file
            cameraLauncher.launch(uri)
        }.onFailure {
            cameraError = "تعذر فتح الكاميرا أو إنشاء ملف الصورة."
        }
    }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashReceivedText by remember { mutableStateOf(totalAmount.toString()) }
    var referenceText by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    val cashReceived = cashReceivedText.toDoubleOrNull() ?: 0.0
    val change = (cashReceived - totalAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إتمام الدفع والفاتورة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "المبلغ المطلوب: $totalAmount ريال",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Method Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.values().forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.titleAr, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (selectedMethod) {
                    PaymentMethod.CASH -> {
                        OutlinedTextField(
                            value = cashReceivedText,
                            onValueChange = { cashReceivedText = it },
                            label = { Text("المبلغ المستلم من العميل (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (change > 0) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "المتبقي للعميل (الصرف): $change ريال",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                    PaymentMethod.E_WALLET -> {
                        OutlinedTextField(
                            value = referenceText,
                            onValueChange = { referenceText = it },
                            label = { Text("رقم العملية / الحوالة في المحفظة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { launchCamera() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (proofFile == null) "التقاط صورة إثبات الدفع (اختياري)" else "إعادة التقاط صورة إثبات الدفع")
                        }
                        if (proofFile != null) {
                            Text("تم إرفاق صورة إثبات الدفع بهذه العملية.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    PaymentMethod.DEBT -> {
                        Text("اختر العميل لتسجيل الحساب بالآجل:", fontSize = 13.sp)
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedCustomer?.name ?: "اضغط لاختيار العميل")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            customers.filter { it.allowDebt }.forEach { cust ->
                                DropdownMenuItem(
                                    text = { Text("${cust.name} (دين: ${cust.currentDebt} / سقف: ${cust.creditLimit})") },
                                    onClick = {
                                        selectedCustomer = cust
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmSale(
                        selectedMethod,
                        cashReceived,
                        referenceText,
                        selectedCustomer?.id,
                        proofFile?.absolutePath.takeIf { selectedMethod == PaymentMethod.E_WALLET }
                    )
                },
                enabled = when (selectedMethod) {
                    PaymentMethod.CASH -> cashReceived >= totalAmount
                    PaymentMethod.E_WALLET -> true
                    PaymentMethod.DEBT -> selectedCustomer != null
                }
            ) {
                Text("تأكيد الفاتورة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ReceiptDialog(
    sale: SaleEntity,
    items: List<CartItem>,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(8.dp))
                Text("فاتورة رقم ${sale.invoiceNumber}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(sale.createdAt))}", fontSize = 12.sp)
                Text("المحاسب: ${sale.userName}", fontSize = 12.sp)
                if (!sale.customerName.isNullOrEmpty()) {
                    Text("العميل: ${sale.customerName}", fontSize = 12.sp)
                }
                Divider()
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.product.name} × ${item.quantity}", fontSize = 13.sp)
                        Text("${item.totalPrice} ريال", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي الصافي:", fontWeight = FontWeight.Bold)
                    Text("${sale.netAmount} ريال", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("طريقة الدفع: ${sale.paymentMethod.titleAr}", fontSize = 12.sp)
                if (sale.cashChange > 0) {
                    Text("المتبقي: ${sale.cashChange} ريال", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onPrint) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("طباعة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}


@Composable
fun PaymentProofDialog(
    path: String,
    onDismiss: () -> Unit
) {
    val proofBitmap = remember(path) {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إثبات الدفع بالمحفظة", fontWeight = FontWeight.Bold) },
        text = {
            if (proofBitmap != null) {
                Image(
                    bitmap = proofBitmap,
                    contentDescription = "صورة إثبات الدفع",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("الصورة غير موجودة أو انتهت مدة الاحتفاظ بها. سجل العملية المالية ما زال محفوظًا.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
