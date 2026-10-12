package com.example.ui.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.AiInsight
import kotlinx.coroutines.launch

@Composable
fun AdminAiTab(
    insights: List<AiInsight>,
    onAskAi: suspend (String) -> String,
    onSaveApiKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var apiKeyText by remember { mutableStateOf("") }
    var showApiKeyConfig by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مساعد الذكاء الاصطناعي وبحث السوق — POWER FEUL AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "محرك Gemini للتحليل المالي والتشغيلي وبحث Google المباشر لأسعار المكملات والمواد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = { showApiKeyConfig = !showApiKeyConfig }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مفتاح Gemini API", fontSize = 11.sp)
                }
            }
        }

        // Optional API Key Config
        if (showApiKeyConfig) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إعداد مفتاح Google Gemini API:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "عند إدخال المفتاح، يتم ربط الاستشارات مباشرة بنموذج Gemini 2.5 Flash. في حال عدم وجوده أو انقطاع الإنترنت، يعمل المحرك الداخلي الذكي تلقائياً بدون توقف.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { apiKeyText = it },
                                placeholder = { Text("أدخل AIzaSy...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                if (apiKeyText.isNotBlank()) {
                                    onSaveApiKey(apiKeyText)
                                    showApiKeyConfig = false
                                }
                            }) {
                                Text("حفظ المفتاح")
                            }
                        }
                    }
                }
            }
        }

        // Section 1: AI Prompt & Analysis
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اسأل الذكاء الاصطناعي أو ابحث في أسعار السوق:", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    // Quick Suggestion Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ما هي النواقص في المخزون؟",
                            "سعر واي بروتين وكرياتين في السوق",
                            "كيف ارفع ارباح كافتيريا النادي؟"
                        ).forEach { query ->
                            SuggestionChip(
                                onClick = { prompt = query },
                                label = { Text(query, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: ما هي المواد الناقصة؟ أو سعر واي بروتين في السوق") },
                        singleLine = false,
                        maxLines = 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (prompt.isNotBlank()) {
                                    isLoading = true
                                    coroutineScope.launch {
                                        aiResponse = onAskAi(prompt)
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && prompt.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استشارة AI")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                val query = if (prompt.isNotBlank()) prompt else "اسعار مكملات وبروتينات الجيم"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بحث Google")
                        }
                    }

                    if (!aiResponse.isNullOrBlank()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("تحليل المساعد الذكي:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(aiResponse ?: "", fontSize = 13.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Proactive Recommendations & Insights
        item {
            Text("رؤى وتوصيات تشغيلية ذكية تلقائية:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(insights) { insight ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(insight.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(insight.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
