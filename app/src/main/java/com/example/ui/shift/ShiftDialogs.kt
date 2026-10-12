package com.example.ui.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShiftEntity

@Composable
fun StartShiftDialog(
    onDismiss: () -> Unit,
    onConfirm: (openingCash: Double, notes: String) -> Unit
) {
    var cashText by remember { mutableStateOf("0.0") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فتح شفت جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("المبلغ الافتتاحي في الدرج (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات البداية") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = cashText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount, notes)
                }
            ) {
                Text("بدء الشفت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CloseShiftDialog(
    shift: ShiftEntity,
    onDismiss: () -> Unit,
    onConfirm: (actualCash: Double, handedOverCash: Double, leftForNextShift: Double, notes: String) -> Unit
) {
    var actualCashText by remember { mutableStateOf(shift.expectedCash.toString()) }
    var handedOverText by remember { mutableStateOf(shift.expectedCash.toString()) }
    var leftText by remember { mutableStateOf("0.0") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إغلاق الشفت رقم ${shift.shiftNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المبلغ المتوقع في الدرج: ${shift.expectedCash} ريال", fontWeight = FontWeight.SemiBold)
                Text("مبيعات نقداً: ${shift.totalCashSales} | محافظ: ${shift.totalWalletSales} | آجل: ${shift.totalDebtSales}", fontSize = 12.sp)

                OutlinedTextField(
                    value = actualCashText,
                    onValueChange = { actualCashText = it },
                    label = { Text("النقد الفعلي المعدود (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = handedOverText,
                    onValueChange = { handedOverText = it },
                    label = { Text("المبلغ المسلّم للإدارة (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leftText,
                    onValueChange = { leftText = it },
                    label = { Text("المبلغ المتروك للشفت القادم (افتتاحي)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الإغلاق والتسليم") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actual = actualCashText.toDoubleOrNull() ?: 0.0
                    val handed = handedOverText.toDoubleOrNull() ?: 0.0
                    val left = leftText.toDoubleOrNull() ?: 0.0
                    onConfirm(actual, handed, left, notes)
                }
            ) {
                Text("تأكيد الإغلاق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun HandoverDialog(
    lastShift: ShiftEntity?,
    onDismiss: () -> Unit,
    onConfirm: (actualReceived: Double, notes: String) -> Unit
) {
    var amountText by remember { mutableStateOf(lastShift?.leftForNextShiftCash?.toString() ?: "0.0") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استلام العهدة من الشفت السابق", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lastShift != null) {
                    Text("مسلّم من: ${lastShift.userName} (شفت ${lastShift.shiftNumber})", fontSize = 13.sp)
                    Text("المبلغ المتوقع تركه: ${lastShift.leftForNextShiftCash} ريال", fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ الفعلي المستلم (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الاستلام") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actual = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(actual, notes)
                }
            ) {
                Text("تأكيد الاستلام وبدء الشفت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
