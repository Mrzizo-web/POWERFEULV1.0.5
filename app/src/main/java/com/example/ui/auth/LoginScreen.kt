package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.domain.model.UserProfile

@Composable
fun LoginScreen(
    userProfiles: List<UserProfile>,
    onAuthenticate: (userId: String, pin: String) -> Unit,
    errorMessage: String? = null,
    onClearError: () -> Unit = {}
) {
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var enteredPin by remember { mutableStateOf("") }

    LaunchedEffect(userProfiles) {
        if (selectedUser == null && userProfiles.isNotEmpty()) {
            selectedUser = userProfiles.firstOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "POWER FEUL POS",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "نادي Power Home Gym",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // User Selection Chips
                Text(
                    text = "اختر المستخدم لتسجيل الدخول",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (userProfiles.isEmpty()) {
                    Text(
                        text = errorMessage ?: "جارٍ تهيئة حساب المالك لأول تشغيل...",
                        color = if (errorMessage.isNullOrEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(userProfiles) { profile ->
                            val isSelected = selectedUser?.id == profile.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedUser = profile
                                    enteredPin = ""
                                    onClearError()
                                },
                                label = {
                                    Text("${profile.name} (${profile.role.titleAr})")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) { // PIN max 6 digits
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric Keypad (4x3)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("clear", "0", "back")
                    )

                    for (row in rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (key in row) {
                                when (key) {
                                    "clear" -> {
                                        OutlinedButton(
                                            onClick = { enteredPin = ""; onClearError() },
                                            modifier = Modifier.size(72.dp),
                                            shape = CircleShape
                                        ) {
                                            Text("مسح", fontSize = 12.sp)
                                        }
                                    }
                                    "back" -> {
                                        OutlinedButton(
                                            onClick = {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                    onClearError()
                                                }
                                            },
                                            modifier = Modifier.size(72.dp),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "حذف")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                if (enteredPin.length < 6) {
                                                    enteredPin += key
                                                    onClearError()
                                                }
                                            },
                                            modifier = Modifier.size(72.dp).testTag("keypad_$key"),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val user = selectedUser
                        if (user != null && enteredPin.length in 4..6) {
                            onAuthenticate(user.id, enteredPin)
                        }
                    },
                    enabled = selectedUser != null && enteredPin.length in 4..6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_submit_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("دخول إلى النظام", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
