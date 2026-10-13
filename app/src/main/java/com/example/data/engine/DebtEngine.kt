package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.DebtTransactionEntity
import com.example.data.local.entity.ShiftCashMovementEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.CustomerStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker

/** Atomic debt collection: balance, ledger, cashbox and audit all commit together. */
class DebtEngine(private val db: AppDatabase) {
    suspend fun payDebt(
        customerId: String,
        amount: Double,
        paymentMethod: PaymentMethod,
        shiftId: String,
        user: UserEntity,
        notes: String = ""
    ): DebtTransactionEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.RECORD_DEBT_PAYMENT)) {
            "ليس لديك صلاحية تحصيل الديون"
        }
        require(amount.isFinite() && amount > 0.0) { "مبلغ السداد يجب أن يكون رقمًا موجبًا وصالحًا" }
        require(paymentMethod == PaymentMethod.CASH || paymentMethod == PaymentMethod.E_WALLET) {
            "طريقة سداد الدين يجب أن تكون نقدية أو محفظة إلكترونية"
        }

        val customer = db.customerDao().getCustomerById(customerId)
            ?: throw IllegalArgumentException("العميل غير موجود")
        require(customer.currentDebt.isFinite() && customer.currentDebt > 0.0) {
            "لا يوجد رصيد دين مستحق على هذا العميل"
        }
        require(amount <= customer.currentDebt) { "مبلغ السداد لا يمكن أن يتجاوز رصيد الدين" }

        val cleanShiftId = shiftId.trim()
        if (paymentMethod == PaymentMethod.CASH && cleanShiftId.isBlank()) {
            throw IllegalArgumentException("السداد النقدي يتطلب شفتًا مفتوحًا لتسجيل أثر النقدية")
        }
        val shift = if (cleanShiftId.isBlank()) null else {
            db.shiftDao().getShiftById(cleanShiftId)
                ?: throw IllegalArgumentException("الشفت المرتبط بسداد الدين غير موجود")
        }
        if (shift != null) require(shift.status == ShiftStatus.OPEN) {
            "لا يمكن تسجيل سداد دين على شفت مغلق"
        }

        val before = customer.currentDebt
        val after = (before - amount).coerceAtLeast(0.0)
        val status = when {
            customer.status == CustomerStatus.BLOCKED -> CustomerStatus.BLOCKED
            after <= BALANCE_EPSILON -> CustomerStatus.SETTLED
            customer.status == CustomerStatus.SETTLED -> CustomerStatus.ACTIVE
            else -> customer.status
        }
        db.customerDao().updateDebt(customer.id, after, status)

        if (shift != null) {
            when (paymentMethod) {
                PaymentMethod.CASH -> {
                    db.shiftDao().updateShift(
                        shift.copy(
                            expectedCash = shift.expectedCash + amount,
                            totalCashDebtPayments = shift.totalCashDebtPayments + amount
                        )
                    )
                    db.shiftDao().insertCashMovement(
                        ShiftCashMovementEntity(
                            shiftId = shift.id,
                            type = "DEBT_COLLECTION",
                            amount = amount,
                            referenceId = customer.id,
                            notes = "تحصيل دين نقدي من ${customer.name}"
                        )
                    )
                }
                PaymentMethod.E_WALLET -> {
                    db.shiftDao().updateShift(
                        shift.copy(totalWalletDebtPayments = shift.totalWalletDebtPayments + amount)
                    )
                }
                else -> error("Unsupported debt payment method")
            }
        }

        val transaction = DebtTransactionEntity(
            customerId = customer.id,
            customerName = customer.name,
            type = "PAYMENT",
            amount = amount,
            balanceBefore = before,
            balanceAfter = after,
            paymentMethod = paymentMethod,
            shiftId = shift?.id.orEmpty(),
            notes = notes.trim(),
            userId = user.id,
            userName = user.name
        )
        db.debtTransactionDao().insertDebtTransaction(transaction)
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "DEBT_PAYMENT_RECORDED",
                entityType = "CUSTOMER",
                entityId = customer.id,
                previousValue = before.toString(),
                newValue = after.toString(),
                notes = "سداد دين ${customer.name}: مبلغ $amount؛ الطريقة ${paymentMethod.name}؛ الشفت ${shift?.id ?: "خارج الشفت"}"
            )
        )
        transaction
    }

    private companion object {
        const val BALANCE_EPSILON = 1e-9
    }
}
