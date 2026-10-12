package com.example.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SessionLockDialog(
    userName: String,
    onUnlock: (pin: String) -> Boolean,
    onLogout: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { /* cannot dismiss */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.width(360.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("الجلسة مقفلة تلقائياً", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("المستخدم الحالي: $userName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) { pin = value; error = null } },
                    label = { Text("أدخل رمز PIN لإلغاء القفل") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("خروج")
                    }
                    Button(
                        onClick = {
                            if (pin.length !in 4..6) {
                                error = "رمز PIN يجب أن يكون من 4 إلى 6 أرقام"
                            } else {
                                val ok = onUnlock(pin)
                                if (!ok) {
                                    error = "رمز PIN غير صحيح"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء القفل")
                    }
                }
            }
        }
    }
}
