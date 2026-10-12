package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SalesEngine(
    private val db: AppDatabase,
    private val inventoryEngine: InventoryEngine
) {
    private val saleDao = db.saleDao()
    private val shiftDao = db.shiftDao()
    private val customerDao = db.customerDao()
    private val debtTransactionDao = db.debtTransactionDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun executeSale(
        shift: ShiftEntity,
        user: UserEntity,
        items: List<CartItem>,
        paymentMethod: PaymentMethod,
        cashReceived: Double = 0.0,
        paymentReference: String = "",
        selectedCustomerId: String? = null,
        discountAmount: Double = 0.0,
        notes: String = ""
    ): SaleResult {
        if (items.isEmpty()) return SaleResult.Error("السلة فارغة")

        val totalAmount = items.sumOf { it.totalPrice }
        val netAmount = (totalAmount - discountAmount).coerceAtLeast(0.0)

        var customer: CustomerEntity? = null
        if (paymentMethod == PaymentMethod.DEBT) {
            if (selectedCustomerId.isNullOrBlank()) {
                return SaleResult.Error("يجب اختيار عميل للبيع بالآجل")
            }
            customer = customerDao.getCustomerById(selectedCustomerId)
            if (customer == null) return SaleResult.Error("العميل غير موجود")
            if (!customer.allowDebt || customer.status == CustomerStatus.BLOCKED) {
                return SaleResult.Error("العميل غير مسموح له بالبيع الآجل")
            }
            if (customer.creditLimit > 0 && (customer.currentDebt + netAmount) > customer.creditLimit) {
                return SaleResult.Error("المبلغ يتجاوز السقف الائتماني للعميل (${customer.creditLimit} ريال)")
            }
        }

        val cashChange = if (paymentMethod == PaymentMethod.CASH && cashReceived > netAmount) {
            cashReceived - netAmount
        } else 0.0

        val count = saleDao.getSalesCount() + 1
        val dateStr = SimpleDateFormat("yyMMdd", Locale.ENGLISH).format(Date())
        val invoiceNumber = "INV-$dateStr-%04d".format(count)
        val saleId = UUID.randomUUID().toString()

        val saleEntity = SaleEntity(
            id = saleId,
            invoiceNumber = invoiceNumber,
            shiftId = shift.id,
            userId = user.id,
            userName = user.name,
            customerId = customer?.id ?: "",
            customerName = customer?.name ?: "",
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            netAmount = netAmount,
            paymentMethod = paymentMethod,
            paymentReference = paymentReference,
            cashReceived = cashReceived,
            cashChange = cashChange,
            notes = notes
        )

        db.withTransaction {
                    saleDao.insertSale(saleEntity)

                    val saleItems = items.map { item ->
                        SaleItemEntity(
                            saleId = saleId,
                            productId = item.product.id,
                            productName = item.product.name,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            totalPrice = item.totalPrice,
                            unitCost = item.product.costPrice,
                            totalCost = item.product.costPrice * item.quantity
                        )
                    }
                    saleDao.insertSaleItems(saleItems)

                    // Deduct inventory for all items
                    for (item in items) {
                        inventoryEngine.consumeForProduct(
                            productId = item.product.id,
                            quantity = item.quantity,
                            saleId = saleId,
                            userId = user.id,
                            userName = user.name
                        )
                    }

                    // Update Shift totals
                    val currentShift = shiftDao.getShiftById(shift.id) ?: shift
                    val updatedShift = when (paymentMethod) {
                        PaymentMethod.CASH -> {
                            val newCashSales = currentShift.totalCashSales + netAmount
                            val newExpected = currentShift.expectedCash + netAmount
                            currentShift.copy(totalCashSales = newCashSales, expectedCash = newExpected)
                        }
                        PaymentMethod.E_WALLET -> {
                            currentShift.copy(totalWalletSales = currentShift.totalWalletSales + netAmount)
                        }
                        PaymentMethod.DEBT -> {
                            currentShift.copy(totalDebtSales = currentShift.totalDebtSales + netAmount)
                        }
                    }
                    shiftDao.updateShift(updatedShift)

                    // If debt, update customer debt and log transaction
                    if (paymentMethod == PaymentMethod.DEBT && customer != null) {
                        val newDebt = customer.currentDebt + netAmount
                        val newStatus = if (customer.creditLimit > 0 && newDebt >= customer.creditLimit) CustomerStatus.BLOCKED else CustomerStatus.ACTIVE
                        customerDao.updateDebt(customer.id, newDebt, newStatus)
                        debtTransactionDao.insertDebtTransaction(
                            DebtTransactionEntity(
                                customerId = customer.id,
                                customerName = customer.name,
                                type = "SALE",
                                amount = netAmount,
                                balanceAfter = newDebt,
                                paymentMethod = PaymentMethod.DEBT,
                                referenceId = invoiceNumber,
                                notes = "فاتورة بيع آجل $invoiceNumber",
                                userId = user.id,
                                userName = user.name
                            )
                        )
                    }

                    auditLogDao.insertLog(
                        AuditLogEntity(
                            userId = user.id,
                            userName = user.name,
                            userRole = user.role.name,
                            action = "SALE_COMPLETED",
                            entityType = "SALE",
                            entityId = saleId,
                            newValue = "$netAmount ريال ($invoiceNumber)",
                            notes = "تمت عملية البيع بنجاح عبر ${paymentMethod.titleAr}"
                        )
                    )

        }
        return SaleResult.Success(saleEntity, invoiceNumber, cashChange)
    }

    suspend fun voidSale(
        saleId: String,
        reason: String,
        user: UserEntity
    ): Result<SaleEntity> {
        val sale = saleDao.getSaleById(saleId) ?: return Result.failure(Exception("الفاتورة غير موجودة"))
        if (sale.status == "VOIDED") return Result.failure(Exception("الفاتورة ملغاة مسبقاً"))

        val updatedSale = sale.copy(
            status = "VOIDED",
            voidedAt = System.currentTimeMillis(),
            voidedByUserId = user.id,
            voidReason = reason
        )
        db.withTransaction {
                    saleDao.updateSale(updatedSale)

                    // Restore inventory
                    val items = saleDao.getSaleItemsSync(saleId)
                    for (item in items) {
                        inventoryEngine.restoreForProduct(
                            productId = item.productId,
                            quantity = item.quantity,
                            saleId = saleId,
                            userId = user.id,
                            userName = user.name
                        )
                    }

                    auditLogDao.insertLog(
                        AuditLogEntity(
                            userId = user.id,
                            userName = user.name,
                            userRole = user.role.name,
                            action = "SALE_VOIDED",
                            entityType = "SALE",
                            entityId = saleId,
                            previousValue = "COMPLETED",
                            newValue = "VOIDED",
                            notes = "إلغاء فاتورة: $reason"
                        )
                    )

        }
        return Result.success(updatedSale)
    }
}
