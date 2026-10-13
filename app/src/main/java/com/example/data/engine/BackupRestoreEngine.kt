package com.example.data.engine

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * CSV exports and password-encrypted, full logical database backup/restore.
 * The encrypted backup is portable because its AES key is derived from the
 * owner-chosen password, not from this device's Android Keystore.
 */
class BackupRestoreEngine(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun exportSalesCsv(): String = withContext(Dispatchers.IO) {
        val sales = db.saleDao().getAllSales().first()
        val sb = StringBuilder()
        sb.append("رقم الفاتورة,التاريخ,المستخدم,العميل,طريقة الدفع,الإجمالي,الخصم,الصافي,الحالة\n")
        for (sale in sales) {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(sale.createdAt))
            val fields = listOf(
                sale.invoiceNumber,
                date,
                sale.userName,
                sale.customerName,
                sale.paymentMethod.name,
                sale.totalAmount.toString(),
                sale.discountAmount.toString(),
                sale.netAmount.toString(),
                sale.status.name
            )
            sb.append(fields.joinToString(",") { csvCell(it) }).append('\n')
        }
        val file = File(context.cacheDir, "sales_export_" + System.currentTimeMillis() + ".csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        file.absolutePath
    }

    suspend fun exportInventoryCsv(): String = withContext(Dispatchers.IO) {
        val materials = db.rawMaterialDao().getAllRawMaterialsSync()
        val sb = StringBuilder()
        sb.append("SKU,المادة الخام,الوحدة,الرصيد,حد التنبيه,تكلفة الوحدة,آخر سعر شراء,الحالة\n")
        for (material in materials) {
            val status = when {
                material.currentStock <= 0.0 -> "نافد"
                material.currentStock <= material.minStock * 0.5 -> "حرج"
                material.currentStock <= material.minStock -> "منخفض"
                else -> "جيد"
            }
            val fields = listOf(
                material.sku,
                material.name,
                material.baseUnit,
                material.currentStock.toString(),
                material.minStock.toString(),
                material.avgCostPerUnit.toString(),
                material.lastPurchasePrice.toString(),
                status
            )
            sb.append(fields.joinToString(",") { csvCell(it) }).append('\n')
        }
        val file = File(context.cacheDir, "inventory_export_" + System.currentTimeMillis() + ".csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        file.absolutePath
    }

    /**
     * Returns an encrypted .pfbackup payload. Every user table and row is
     * captured from one Room transaction. Secrets are protected by AES-GCM;
     * the owner must keep the password separately because it is not recoverable.
     */
    suspend fun createFullBackupBytes(
        userId: String,
        userName: String,
        password: String
    ): ByteArray = withContext(Dispatchers.IO) {
        validatePassword(password)
        val snapshot = db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            val tables = readTableSchemas(sqlDb)
            val tableArray = JSONArray()
            for (schema in tables) {
                val tableObject = JSONObject()
                tableObject.put("name", schema.name)
                val columns = JSONArray()
                schema.columns.forEach { columns.put(it) }
                tableObject.put("columns", columns)

                val rows = JSONArray()
                val select = "SELECT * FROM " + quoteIdentifier(schema.name)
                sqlDb.query(select).use { cursor ->
                    val secretKeyIndex = schema.columns.indexOfFirst { it.equals("key", ignoreCase = true) }
                    val secretValueIndex = schema.columns.indexOfFirst { it.equals("value", ignoreCase = true) }
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        val settingKey = if (
                            schema.name.equals("settings", ignoreCase = true) &&
                            secretKeyIndex >= 0 && !cursor.isNull(secretKeyIndex)
                        ) cursor.getString(secretKeyIndex).lowercase(Locale.ROOT) else ""

                        for (i in schema.columns.indices) {
                            if (
                                schema.name.equals("settings", ignoreCase = true) &&
                                i == secretValueIndex &&
                                settingKey in DEVICE_BOUND_SECRETS
                            ) {
                                // This credential belongs to this device; never export it,
                                // even inside the encrypted backup.
                                row.put(JSONObject().put("t", "S").put("v", ""))
                            } else {
                                row.put(encodeCursorCell(cursor, i))
                            }
                        }
                        rows.put(row)
                    }
                }
                tableObject.put("rows", rows)
                tableArray.put(tableObject)
            }

            JSONObject()
                .put("format", PAYLOAD_FORMAT)
                .put("appName", "POWER FEUL POS")
                .put("schemaVersion", sqlDb.version)
                .put("createdAt", System.currentTimeMillis())
                .put("createdByUserId", userId)
                .put("createdByUserName", userName)
                .put("tableCount", tables.size)
                .put("tables", tableArray)
        }
        val plaintext = snapshot.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_BACKUP_BYTES) { "حجم قاعدة البيانات أكبر من الحد المسموح للنسخ الاحتياطي" }
        val encrypted = encryptPayload(plaintext, password)
        require(encrypted.size <= MAX_BACKUP_BYTES) { "حجم ملف النسخة المشفر يتجاوز الحد المسموح" }
        encrypted
    }

    /**
     * Validates and restores into the existing Room database transactionally.
     * A failure at any stage rolls back the deletes/inserts. Only same-schema
     * backups containing the current active owner's valid PIN hash are accepted.
     */
    suspend fun restoreFullBackup(
        encryptedBytes: ByteArray,
        password: String,
        requiredOwnerId: String
    ): Int = withContext(Dispatchers.IO) {
        validatePassword(password)
        require(encryptedBytes.isNotEmpty() && encryptedBytes.size <= MAX_BACKUP_BYTES) {
            "حجم ملف النسخة الاحتياطية غير صالح أو أكبر من الحد المسموح"
        }
        val payload = decryptPayload(encryptedBytes, password)
        require(payload.optString("format") == PAYLOAD_FORMAT) {
            "صيغة النسخة الاحتياطية غير مدعومة"
        }
        val archivedSchemaVersion = payload.optInt("schemaVersion", -1)
        val archivedTables = payload.optJSONArray("tables")
            ?: throw IllegalArgumentException("ملف النسخة لا يحتوي على جداول قاعدة البيانات")
        require(archivedTables.length() > 0) { "النسخة الاحتياطية فارغة" }
        validateOwnerIncluded(archivedTables, requiredOwnerId)

        db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            require(sqlDb.version == archivedSchemaVersion) {
                "إصدار قاعدة البيانات في النسخة (" + archivedSchemaVersion +
                    ") لا يطابق إصدار التطبيق الحالي (" + sqlDb.version + "). لن تُستبدل أي بيانات."
            }
            require(db.shiftDao().getCurrentOpenShiftSync() == null) {
                "أغلق الشفت الحالي قبل استعادة نسخة احتياطية"
            }

            val currentSchemas = readTableSchemas(sqlDb)
            val validatedTables = validateTablePayloads(archivedTables, currentSchemas)

            sqlDb.execSQL("PRAGMA defer_foreign_keys = ON")
            for (schema in currentSchemas.sortedByDescending { it.name }) {
                sqlDb.execSQL("DELETE FROM " + quoteIdentifier(schema.name))
            }

            for (schema in currentSchemas) {
                val tableObject = validatedTables[schema.name]
                    ?: throw IllegalArgumentException("جدول مفقود في النسخة: " + schema.name)
                val rows = tableObject.getJSONArray("rows")
                if (rows.length() == 0) continue

                val columnsSql = schema.columns.joinToString(",") { quoteIdentifier(it) }
                val placeholders = schema.columns.joinToString(",") { "?" }
                val insertSql = "INSERT INTO " + quoteIdentifier(schema.name) +
                    " (" + columnsSql + ") VALUES (" + placeholders + ")"
                val statement = sqlDb.compileStatement(insertSql)
                try {
                    for (rowIndex in 0 until rows.length()) {
                        val row = rows.getJSONArray(rowIndex)
                        statement.clearBindings()
                        for (columnIndex in schema.columns.indices) {
                            bindCell(statement, columnIndex + 1, row.getJSONObject(columnIndex))
                        }
                        statement.execute()
                    }
                } finally {
                    statement.close()
                }
            }
            validatedTables.size
        }
    }

    private fun validateOwnerIncluded(tables: JSONArray, ownerId: String) {
        var users: JSONObject? = null
        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            if (table.optString("name") == "users") {
                users = table
                break
            }
        }
        val userTable = users ?: throw IllegalArgumentException("النسخة لا تحتوي على جدول المستخدمين")
        val columns = userTable.getJSONArray("columns")
        fun indexOfColumn(name: String): Int {
            for (i in 0 until columns.length()) {
                if (columns.getString(i).equals(name, ignoreCase = true)) return i
            }
            return -1
        }
        val idIndex = indexOfColumn("id")
        val roleIndex = indexOfColumn("role")
        val activeIndex = indexOfColumn("isActive")
        val hashIndex = indexOfColumn("pinHash")
        val saltIndex = indexOfColumn("pinSalt")
        require(listOf(idIndex, roleIndex, activeIndex, hashIndex, saltIndex).all { it >= 0 }) {
            "مخطط جدول المستخدمين في النسخة غير صالح"
        }

        val rows = userTable.getJSONArray("rows")
        var ownerValid = false
        for (r in 0 until rows.length()) {
            val row = rows.getJSONArray(r)
            if (decodeCellAsString(row.getJSONObject(idIndex)) != ownerId) continue
            val role = decodeCellAsString(row.getJSONObject(roleIndex))
            val active = decodeCellAsLong(row.getJSONObject(activeIndex)) == 1L
            val hash = decodeCellAsString(row.getJSONObject(hashIndex))
            val salt = decodeCellAsString(row.getJSONObject(saltIndex))
            ownerValid = role == "OWNER" && active && hash.isNotBlank() && salt.isNotBlank()
            break
        }
        require(ownerValid) {
            "النسخة لا تحتوي على حساب المالك الحالي النشط مع PIN صالح؛ رُفضت الاستعادة لمنع فقدان الوصول"
        }
    }

    private fun validateTablePayloads(
        archivedTables: JSONArray,
        currentSchemas: List<TableSchema>
    ): Map<String, JSONObject> {
        val tableMap = linkedMapOf<String, JSONObject>()
        for (i in 0 until archivedTables.length()) {
            val table = archivedTables.getJSONObject(i)
            val name = table.optString("name")
            require(name.isNotBlank() && tableMap.put(name, table) == null) {
                "اسم جدول مكرر أو غير صالح في النسخة"
            }
        }
        val currentNames = currentSchemas.map { it.name }.toSet()
        require(tableMap.keys == currentNames) {
            "قائمة جداول النسخة لا تطابق مخطط قاعدة البيانات الحالي"
        }
        for (schema in currentSchemas) {
            val table = tableMap[schema.name]!!
            val archivedColumns = table.getJSONArray("columns")
            require(archivedColumns.length() == schema.columns.size) {
                "عدد أعمدة الجدول غير متطابق: " + schema.name
            }
            for (i in schema.columns.indices) {
                require(archivedColumns.getString(i) == schema.columns[i]) {
                    "مخطط أعمدة الجدول غير متطابق: " + schema.name
                }
            }
            val rows = table.getJSONArray("rows")
            for (r in 0 until rows.length()) {
                val row = rows.getJSONArray(r)
                require(row.length() == schema.columns.size) {
                    "عدد قيم الصف غير متطابق في الجدول: " + schema.name
                }
                for (c in 0 until row.length()) {
                    validateCell(row.getJSONObject(c))
                }
            }
        }
        return tableMap
    }

    private fun readTableSchemas(sqlDb: SupportSQLiteDatabase): List<TableSchema> {
        val names = mutableListOf<String>()
        sqlDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table', 'room_table_modification_log') ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name.isNotBlank()) names.add(name)
            }
        }

        return names.map { tableName ->
            val columns = mutableListOf<String>()
            sqlDb.query("PRAGMA table_info(" + quoteIdentifier(tableName) + ")").use { cursor ->
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
            }
            require(columns.isNotEmpty()) { "تعذر قراءة مخطط الجدول: " + tableName }
            TableSchema(tableName, columns)
        }
    }

    private fun encodeCursorCell(cursor: Cursor, index: Int): JSONObject = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject().put("t", "N")
        Cursor.FIELD_TYPE_INTEGER -> JSONObject().put("t", "I").put("v", cursor.getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JSONObject().put("t", "F").put("v", cursor.getDouble(index))
        Cursor.FIELD_TYPE_STRING -> JSONObject().put("t", "S").put("v", cursor.getString(index))
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(
            "t", "B"
        ).put("v", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> throw IllegalArgumentException("نوع خلية SQLite غير مدعوم")
    }

    private fun validateCell(cell: JSONObject) {
        when (cell.optString("t")) {
            "N" -> Unit
            "I" -> cell.getLong("v")
            "F" -> {
                val number = cell.getDouble("v")
                require(number.isFinite()) { "قيمة رقمية غير صالحة في النسخة الاحتياطية" }
            }
            "S" -> cell.getString("v")
            "B" -> Base64.decode(cell.getString("v"), Base64.NO_WRAP)
            else -> throw IllegalArgumentException("نوع خلية غير صالح في النسخة الاحتياطية")
        }
    }

    private fun bindCell(statement: SupportSQLiteStatement, index: Int, cell: JSONObject) {
        when (cell.getString("t")) {
            "N" -> statement.bindNull(index)
            "I" -> statement.bindLong(index, cell.getLong("v"))
            "F" -> statement.bindDouble(index, cell.getDouble("v"))
            "S" -> statement.bindString(index, cell.getString("v"))
            "B" -> statement.bindBlob(index, Base64.decode(cell.getString("v"), Base64.NO_WRAP))
            else -> throw IllegalArgumentException("نوع خلية غير صالح في النسخة الاحتياطية")
        }
    }

    private fun decodeCellAsString(cell: JSONObject): String = when (cell.optString("t")) {
        "S" -> cell.optString("v")
        "I" -> cell.optLong("v").toString()
        "F" -> cell.optDouble("v").toString()
        "N" -> ""
        else -> ""
    }

    private fun decodeCellAsLong(cell: JSONObject): Long = when (cell.optString("t")) {
        "I" -> cell.optLong("v", -1L)
        "F" -> cell.optDouble("v", -1.0).toLong()
        else -> -1L
    }

    private fun encryptPayload(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return JSONObject()
            .put("format", ENCRYPTED_FORMAT)
            .put("iterations", KDF_ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun decryptPayload(encryptedBytes: ByteArray, password: String): JSONObject {
        val envelope = try {
            JSONObject(String(encryptedBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            throw IllegalArgumentException("ملف النسخة الاحتياطية ليس ملف POWER FEUL POS صالحًا")
        }
        require(envelope.optString("format") == ENCRYPTED_FORMAT) {
            "صيغة ملف النسخة الاحتياطية غير مدعومة"
        }
        val iterations = envelope.optInt("iterations", -1)
        require(iterations in 100_000..500_000) { "إعدادات اشتقاق المفتاح في النسخة غير صالحة" }
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size >= GCM_TAG_BYTES) {
            "محتوى النسخة الاحتياطية غير مكتمل"
        }

        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("كلمة مرور النسخة غير صحيحة أو أن الملف تالف")
        } catch (error: Exception) {
            throw IllegalArgumentException("تعذر فك تشفير النسخة الاحتياطية: " + (error.message ?: "خطأ تشفير"))
        }
        require(plaintext.size <= MAX_BACKUP_BYTES) { "محتوى النسخة المفكوك يتجاوز الحد المسموح" }
        return try {
            JSONObject(String(plaintext, Charsets.UTF_8))
        } catch (_: Exception) {
            throw IllegalArgumentException("محتوى النسخة بعد فك التشفير غير صالح")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(bytes, "AES")
    }

    private fun validatePassword(password: String) {
        require(password.length in 12..256) {
            "كلمة مرور النسخة الاحتياطية يجب أن تتكون من 12 حرفًا على الأقل"
        }
    }

    private fun csvCell(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun quoteIdentifier(identifier: String): String {
        require(identifier.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            "اسم جدول أو عمود غير صالح"
        }
        return "\"" + identifier + "\""
    }

    private data class TableSchema(val name: String, val columns: List<String>)

    companion object {
        const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
        private const val ENCRYPTED_FORMAT = "POWER_FEUL_POS_ENCRYPTED_BACKUP_V1"
        private const val PAYLOAD_FORMAT = "POWER_FEUL_POS_LOGICAL_BACKUP_V1"
        private const val KDF_ITERATIONS = 250_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16
        private val DEVICE_BOUND_SECRETS = setOf("gateway_token", "gateway_shared_secret", "gateway_hmac_key")
    }
}
