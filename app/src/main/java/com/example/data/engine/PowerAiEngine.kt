package com.example.data.engine

import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class PowerAiEngine(private val db: AppDatabase) {

    suspend fun getGeminiApiKey(): String = withContext(Dispatchers.IO) {
        val stored = db.settingsDao().getSetting("gemini_api_key") ?: ""
        if (stored.isBlank()) return@withContext ""

        // New keys are always encrypted. Migrate legacy plaintext values on read.
        if (stored.startsWith(PowerAiSecretStore.ENCRYPTED_PREFIX)) {
            return@withContext PowerAiSecretStore.decrypt(stored)
        }
        val encrypted = runCatching { PowerAiSecretStore.encrypt(stored) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return@withContext ""
        db.settingsDao().setSetting(
            com.example.data.local.entity.CafeteriaSettingEntity("gemini_api_key", encrypted)
        )
        stored
    }

    suspend fun saveGeminiApiKey(key: String) {
        withContext(Dispatchers.IO) {
            val normalized = key.trim()
            val protectedValue = if (normalized.isBlank()) "" else PowerAiSecretStore.encrypt(normalized)
            db.settingsDao().setSetting(
                com.example.data.local.entity.CafeteriaSettingEntity("gemini_api_key", protectedValue)
            )
        }
    }

    suspend fun generateRealInsights(): List<AiInsight> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AiInsight>()

        val lowMaterials = db.rawMaterialDao().getLowStockMaterialsSync()
        if (lowMaterials.isNotEmpty()) {
            val names = lowMaterials.take(3).joinToString(", ") { it.name }
            list.add(
                AiInsight(
                    title = "تنبيه انخفاض المخزون",
                    description = "المواد التالية قاربت على النفاد: $names. يرجى توريد كميات جديدة لضمان عدم توقف المشروبات.",
                    type = InsightType.INVENTORY
                )
            )
        }

        val totalSales = db.saleDao().getSalesCount()
        if (totalSales > 0) {
            list.add(
                AiInsight(
                    title = "نشاط المبيعات ونقاط البيع",
                    description = "إجمالي الفواتير المسجلة في النظام: $totalSales فاتورة. تدفق المبيعات مستقر في كافتيريا النادي.",
                    type = InsightType.SALES
                )
            )
        }

        val totalRecipes = db.recipeDao().getAllRecipesSync().size
        if (totalRecipes > 0) {
            list.add(
                AiInsight(
                    title = "تحسين التكلفة والربحية",
                    description = "يمكن استخدام بيانات التكلفة والوصفات لمراجعة هامش الربح؛ لا يغيّر المساعد الأسعار أو القيود المالية.",
                    type = InsightType.COST
                )
            )
        }

        list.add(
            AiInsight(
                title = "بوابة الحوالات (SMS Gateway)",
                description = "تم تفعيل مطابقة رسائل جيب وفلوسك وجوالي مع الشفتات مع كشف التكرار؛ المساعد لا يؤكد الحوالات ولا يعدّل القيود المالية.",
                type = InsightType.GENERAL
            )
        )

        list
    }

    suspend fun processQuery(query: String): String = withContext(Dispatchers.IO) {
        val apiKey = getGeminiApiKey()

        if (apiKey.isNotBlank()) {
            try {
                val geminiResp = callGeminiApi(apiKey, query)
                if (geminiResp.isNotBlank()) {
                    return@withContext geminiResp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // High quality offline fallback tailored for Power Home Gym
        val q = query.trim().lowercase()
        when {
            "مخزون" in q || "نواقص" in q || "مواد" in q -> {
                val low = db.rawMaterialDao().getLowStockMaterialsSync()
                if (low.isEmpty()) "المخزون متوازن حالياً في المستودع ولا توجد أي مواد قاربت على النفاد."
                else "المواد الناقصة في الكافتيريا: " + low.joinToString("، ") { "${it.name} (المتبقي: ${it.currentStock} ${it.baseUnit})" }
            }
            "مبيعات" in q || "ارباح" in q || "دخل" in q -> {
                val count = db.saleDao().getSalesCount()
                "إجمالي الفواتير المحققة في النظام: $count فاتورة. لمزيد من التفاصيل الدقيقة يرجى مراجعة تبويب التقارير والمبيعات."
            }
            "سعر" in q || "سوق" in q || "بروتين" in q || "كرياتين" in q -> {
                "لا توجد لدي هنا بيانات سوق موثقة وحديثة تكفي لإعطاء سعر دقيق. استخدم بحث Google/مصدر موثوق عند الحاجة، ولا تعتمد على هذه الإجابة كمرجع تسعير."
            }
            "حوالات" in q || "جيب" in q || "فلوسك" in q || "محفظة" in q -> {
                val openShift = db.shiftDao().getCurrentOpenShiftSync()
                val statusText = if (openShift != null) "الشفت الحالي مفتوح (#${openShift.shiftNumber} - ${openShift.userName})" else "لا يوجد شفت مفتوح حالياً"
                "نظام الحوالات POWER FEUL يدعم جيب، فلوسك، وجوالي عبر الرسائل القصيرة SMS مع منع التكرار والربط بالشفتات. $statusText."
            }
            else -> "مرحباً زياد، أنا مساعد الذكاء الاصطناعي لنادي Power Home Gym. يمكنك استشارتي في حالة المخزون، حركة المبيعات، تكاليف الوصفات، أو النقر على 'بحث Google' لمعرفة أحدث أسعار المكملات في السوق."
        }
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        val systemInstruction = "أنت مساعد قراءة واستشارة فقط لنظام Power Home Gym وPOWER FEUL POS. لا تنفذ ولا تؤكد ولا ترفض ولا تعيد إسناد أي عملية مالية، ولا تغيّر المخزون أو الشفتات أو الحوالات. أجب بدقة واختصار وبالعربية المهنية."
        val jsonPayload = """
            {
              "contents": [
                {
                  "parts": [
                    {"text": "$systemInstruction: $escapedPrompt"}
                  ]
                }
              ]
            }
        """.trimIndent()

        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(jsonPayload)
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            return parseGeminiResponse(responseText)
        }
        conn.disconnect()
        return ""
    }

    private fun parseGeminiResponse(json: String): String {
        val matcher = Pattern.compile(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""").matcher(json)
        if (matcher.find()) {
            val raw = matcher.group(1) ?: ""
            return raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return ""
    }
}
