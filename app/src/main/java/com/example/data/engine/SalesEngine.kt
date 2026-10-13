package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker
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
    private val costEngine = CostEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao())

    private companion object {
        const val PRICE_EPSILON = 0.001
    }

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
        if (!PermissionChecker.hasPermission(user.role, AppPermission.PROCESS_SALE)) {
            return SaleResult.Error("ليس لديك صلاحية تنفيذ المبيعات")
        }
        if (items.isEmpty()) return SaleResult.Error("السلة فارغة")
        if (items.any { it.quantity <= 0 }) {
            return SaleResult.Error("كمية أي منتج يجب أن تكون أكبر من صفر")
        }
        if (items.any {
                !it.unitPrice.isFinite() || it.unitPrice < 0.0 ||
                    !it.totalPrice.isFinite() || it.totalPrice < 0.0
            }
        ) {
            return SaleResult.Error("توجد أسعار أو تكاليف غير صالحة في السلة")
        }

        val totalAmount = items.sumOf { it.totalPrice }
        if (!totalAmount.isFinite() || totalAmount <= 0.0) {
            return SaleResult.Error("إجمالي الفاتورة يجب أن يكون أكبر من صفر")
        }
        if (!discountAmount.isFinite() || discountAmount < 0.0 || discountAmount >= totalAmount) {
            return SaleResult.Error("الخصم غير صالح؛ يجب أن يكون غير سالب وأقل من إجمالي الفاتورة")
        }
        val netAmount = totalAmount - discountAmount
        if (paymentMethod == PaymentMethod.CASH &&
            (!cashReceived.isFinite() || cashReceived < netAmount)
        ) {
            return SaleResult.Error("المبلغ النقدي المستلم أقل من صافي الفاتورة")
        }
        val effectivePaymentReference = if (
            paymentMethod == PaymentMethod.E_WALLET && paymentReference.isBlank()
        ) {
            "WAL-" + SimpleDateFormat("yyMMdd", Locale.ENGLISH).format(Date()) + "-" +
                UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)
        } else {
            paymentReference.trim()
        }

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

        val saleId = UUID.randomUUID().toString()
        var invoiceNumber = ""
        var saleEntity: SaleEntity? = null

        try {
        db.withTransaction {
                    val openShift = shiftDao.getShiftById(shift.id)
                        ?: throw InventoryValidationException("الشفت غير موجود")
                    if (openShift.status != com.example.domain.model.ShiftStatus.OPEN) {
                        throw InventoryValidationException("لا يمكن البيع على شفت مغلق")
                    }
                    if (openShift.userId != user.id && user.role !in setOf(UserRole.OWNER, UserRole.ADMIN)) {
                        throw InventoryValidationException("لا يمكن تنفيذ مبيعات على شفت مستخدم آخر")
                    }
                    val currentProducts = linkedMapOf<String, ProductEntity>()
                    val currentUnitCosts = linkedMapOf<String, Double>()
                    for (item in items) {
                        val currentProduct = db.productDao().getProductById(item.product.id)
                            ?: throw InventoryValidationException("المنتج لم يعد موجودًا: ${item.product.name}")
                        if (!currentProduct.isActive || !currentProduct.isAvailable) {
                            throw InventoryValidationException("المنتج غير نشط أو غير متاح للبيع: ${currentProduct.name}")
                        }
                        if (kotlin.math.abs(item.unitPrice - currentProduct.price) > PRICE_EPSILON) {
                            throw InventoryValidationException(
                                "تغير سعر المنتج ${currentProduct.name} من ${item.unitPrice} إلى ${currentProduct.price} ريال؛ حدّث السلة ثم أعد البيع"
                            )
                        }

                        // The cost stored on a cart item can be stale after supplier prices change.
                        // Recalculate the active recipe from current raw-material costs inside
                        // the sale transaction, then snapshot that cost into this sale only.
                        val activeRecipe = db.recipeDao().getActiveRecipeForProductSync(currentProduct.id)
                        val currentUnitCost = if (activeRecipe != null) {
                            val recalculated = costEngine.calculateRecipeTotalCost(activeRecipe.id)
                            if (activeRecipe.calculatedCost != recalculated) {
                                db.recipeDao().updateRecipe(activeRecipe.copy(calculatedCost = recalculated))
                            }
                            recalculated
                        } else {
                            currentProduct.costPrice
                        }
                        if (!currentUnitCost.isFinite() || currentUnitCost < 0.0) {
                            throw InventoryValidationException("تكلفة المنتج غير صالحة: ${currentProduct.name}")
                        }
                        if (currentProduct.costPrice != currentUnitCost) {
                            db.productDao().updateProduct(currentProduct.copy(costPrice = currentUnitCost))
                        }
                        currentProducts[currentProduct.id] = currentProduct.copy(costPrice = currentUnitCost)
                        currentUnitCosts[currentProduct.id] = currentUnitCost
                    }
                    val currentTotalCost = items.sumOf { item ->
                        (currentUnitCosts[item.product.id]
                            ?: throw InventoryValidationException("تعذر حساب تكلفة المنتج: ${item.product.name}")) * item.quantity
                    }
                    if (!currentTotalCost.isFinite() || currentTotalCost < 0.0) {
                        throw InventoryValidationException("إجمالي تكلفة الفاتورة غير صالح")
                    }
                    if (paymentMethod == PaymentMethod.DEBT) {
                        val refreshedCustomer = selectedCustomerId?.let { customerDao.getCustomerById(it) }
                            ?: throw InventoryValidationException("العميل غير موجود")
                        if (!refreshedCustomer.allowDebt || refreshedCustomer.status == CustomerStatus.BLOCKED) {
                            throw InventoryValidationException("العميل غير مسموح له بالبيع الآجل")
                        }
                        if (refreshedCustomer.creditLimit > 0 && refreshedCustomer.currentDebt + netAmount > refreshedCustomer.creditLimit) {
                            throw InventoryValidationException("المبلغ يتجاوز السقف الائتماني للعميل (${refreshedCustomer.creditLimit} ريال)")
                        }
                        customer = refreshedCustomer
                    }
                    // Allocate invoice number under the same serialized Room transaction.
                    val count = saleDao.getSalesCount() + 1
                    val dateStr = SimpleDateFormat("yyMMdd", Locale.ENGLISH).format(Date())
                    invoiceNumber = "INV-$dateStr-%04d".format(count)
                    val saleRecord = SaleEntity(
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
                        paymentReference = effectivePaymentReference,
                        cashReceived = cashReceived,
                        cashChange = cashChange,
                        totalCost = currentTotalCost,
                        notes = notes
                    )
                    saleEntity = saleRecord
                    saleDao.insertSale(saleRecord)

                    val saleItems = items.map { item ->
                        val unitCost = currentUnitCosts.getValue(item.product.id)
                        val currentProduct = currentProducts.getValue(item.product.id)
                        SaleItemEntity(
                            saleId = saleId,
                            productId = currentProduct.id,
                            productName = currentProduct.name,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            totalPrice = item.totalPrice,
                            unitCost = unitCost,
                            totalCost = unitCost * item.quantity
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
                                balanceBefore = customer.currentDebt,
                                shiftId = shift.id,
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
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            return SaleResult.Error(e.message ?: "تعذر إتمام البيع. تم التراجع عن التغييرات.")
        }
        return SaleResult.Success(requireNotNull(saleEntity), invoiceNumber, cashChange)
    }

    suspend fun voidSale(
        saleId: String,
        reason: String,
        user: UserEntity
    ): Result<SaleEntity> {
        if (!PermissionChecker.hasPermission(user.role, AppPermission.VOID_SALE)) {
            return Result.failure(SecurityException("ليس لديك صلاحية إلغاء الفواتير"))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("سبب إلغاء الفاتورة مطلوب"))
        }
        return try {
            db.withTransaction {
                val sale = saleDao.getSaleById(saleId)
                    ?: return@withTransaction Result.failure(Exception("الفاتورة غير موجودة"))
                if (sale.status == "VOIDED") {
                    return@withTransaction Result.failure(Exception("الفاتورة ملغاة مسبقاً"))
                }

                val updatedSale = sale.copy(
                    status = "VOIDED",
                    voidedAt = System.currentTimeMillis(),
                    voidedByUserId = user.id,
                    voidReason = reason
                )

                // Restore the transaction snapshot recorded at sale time; never
                // calculate from a recipe that might have changed since the sale.
                inventoryEngine.restoreSaleConsumption(saleId, user.id, user.name)

                val shift = shiftDao.getShiftById(sale.shiftId)
                if (shift != null) {
                    val reversedShift = when (sale.paymentMethod) {
                        PaymentMethod.CASH -> {
                            val remainingCashSales = shift.totalCashSales - sale.netAmount
                            val revisedExpectedCash = shift.expectedCash - sale.netAmount
                            if (remainingCashSales < -0.01 || revisedExpectedCash < -0.01) {
                                throw IllegalStateException("تعذر عكس الفاتورة: مجاميع النقدية لا تطابق سجل الفاتورة")
                            }
                            shift.copy(
                                totalCashSales = remainingCashSales.coerceAtLeast(0.0),
                                expectedCash = revisedExpectedCash.coerceAtLeast(0.0),
                                discrepancyAmount = if (shift.status == com.example.domain.model.ShiftStatus.OPEN) {
                                    shift.discrepancyAmount
                                } else {
                                    shift.actualCash - revisedExpectedCash.coerceAtLeast(0.0)
                                }
                            )
                        }
                        PaymentMethod.E_WALLET -> {
                            if (shift.totalWalletSales + 0.01 < sale.netAmount) {
                                throw IllegalStateException("تعذر عكس الفاتورة: إجمالي مبيعات المحفظة لا يطابق سجل الفاتورة")
                            }
                            shift.copy(totalWalletSales = (shift.totalWalletSales - sale.netAmount).coerceAtLeast(0.0))
                        }
                        PaymentMethod.DEBT -> {
                            if (shift.totalDebtSales + 0.01 < sale.netAmount) {
                                throw IllegalStateException("تعذر عكس الفاتورة: إجمالي مبيعات الدين لا يطابق سجل الفاتورة")
                            }
                            shift.copy(totalDebtSales = (shift.totalDebtSales - sale.netAmount).coerceAtLeast(0.0))
                        }
                    }
                    shiftDao.updateShift(reversedShift)
                }

                if (sale.paymentMethod == PaymentMethod.DEBT && sale.customerId.isNotBlank()) {
                    val customer = customerDao.getCustomerById(sale.customerId)
                    if (customer != null) {
                        if (customer.currentDebt + 0.01 < sale.netAmount) {
                            throw IllegalStateException(
                                "تعذر إلغاء الفاتورة الآجلة: رصيد العميل الحالي أقل من قيمة الفاتورة، ويجب تسوية سجل الدين أولًا"
                            )
                        }
                        val newDebt = customer.currentDebt - sale.netAmount
                        // Preserve a deliberately blocked/settled status; reverse only the balance.
                        customerDao.updateDebt(customer.id, newDebt, customer.status)
                        debtTransactionDao.insertDebtTransaction(
                            DebtTransactionEntity(
                                customerId = customer.id,
                                customerName = customer.name,
                                type = "VOID",
                                amount = -sale.netAmount,
                                balanceAfter = newDebt,
                                balanceBefore = customer.currentDebt,
                                shiftId = sale.shiftId,
                                paymentMethod = PaymentMethod.DEBT,
                                referenceId = sale.invoiceNumber,
                                notes = "عكس دين الفاتورة الملغاة ${sale.invoiceNumber}",
                                userId = user.id,
                                userName = user.name
                            )
                        )
                    }
                }

                saleDao.updateSale(updatedSale)
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
                        notes = "إلغاء فاتورة: $reason؛ تم عكس إجماليات الشفت والدين واسترجاع حركات المخزون"
                    )
                )
                Result.success(updatedSale)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
