package com.example.data.engine

import android.content.Context
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            sb.append("${sale.invoiceNumber},${date},${sale.userName},${sale.customerName},${sale.paymentMethod},${sale.totalAmount},${sale.discountAmount},${sale.netAmount},${sale.status}\n")
        }
        val file = File(context.cacheDir, "sales_export_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        file.absolutePath
    }

    suspend fun createFullBackupJson(userId: String, userName: String): String = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val backupFile = File(context.filesDir, "power_feul_pos_backup_$dateStr.json")
        val users = db.userDao().countUsers()
        val sales = db.saleDao().getSalesCount()
        val products = db.productDao().getAllProductsSync().size
        val json = """
        {
          "appName": "POWER FEUL POS",
          "version": "1.0",
          "timestamp": ${System.currentTimeMillis()},
          "creator": "$userName",
          "stats": {
             "usersCount": $users,
             "salesCount": $sales,
             "productsCount": $products
          }
        }
        """.trimIndent()
        backupFile.writeText(json, Charsets.UTF_8)
        backupFile.absolutePath
    }
}
