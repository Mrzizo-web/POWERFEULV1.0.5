package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.ShiftCashMovementEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker

class ExpenseValidationException(message: String) : IllegalArgumentException(message)

/** Persists an expense and its cash-box impact atomically. */
class ExpenseEngine(private val db: AppDatabase) {
    suspend fun recordExpense(
        title: String,
        amount: Double,
        category: String,
        paidTo: String,
        notes: String,
        paymentMethod: PaymentMethod,
        shiftId: String,
        user: UserEntity
    ): ExpenseEntity = db.withTransaction {
        check(PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_EXPENSES)) {
            "ليس لديك صلاحية إدارة المصروفات"
        }
        require(title.isNotBlank()) { "عنوان المصروف مطلوب" }
        require(category.isNotBlank()) { "تصنيف المصروف مطلوب" }
        if (!amount.isFinite() || amount <= 0.0) {
            throw ExpenseValidationException("قيمة المصروف يجب أن تكون رقمًا موجبًا وصالحًا")
        }

        val shift = shiftId.trim().takeIf { it.isNotEmpty() }?.let { id ->
            db.shiftDao().getShiftById(id)
                ?: throw ExpenseValidationException("الشفت المرتبط بالمصروف غير موجود")
        }
        if (shift != null && shift.status != ShiftStatus.OPEN) {
            throw ExpenseValidationException("لا يمكن تسجيل مصروف على شفت مغلق")
        }
        if (shift != null && paymentMethod == PaymentMethod.CASH &&
            amount > shift.expectedCash + CASH_EPSILON
        ) {
            throw ExpenseValidationException(
                "النقدية المتوقعة غير كافية للمصروف: المطلوب " + amount +
                    " ريال، المتاح " + shift.expectedCash + " ريال"
            )
        }

        val expense = ExpenseEntity(
            shiftId = shift?.id ?: "",
            title = title.trim(),
            category = category.trim(),
            amount = amount,
            paidTo = paidTo.trim(),
            paymentMethod = paymentMethod,
            notes = notes.trim(),
            userId = user.id,
            userName = user.name
        )
        db.expenseDao().insertExpense(expense)

        if (shift != null && paymentMethod == PaymentMethod.CASH) {
            db.shiftDao().updateShift(
                shift.copy(
                    expectedCash = shift.expectedCash - amount,
                    totalExpensesCash = shift.totalExpensesCash + amount
                )
            )
            db.shiftDao().insertCashMovement(
                ShiftCashMovementEntity(
                    shiftId = shift.id,
                    type = "EXPENSE",
                    amount = amount,
                    referenceId = expense.id,
                    notes = "مصروف نقدي: " + expense.title
                )
            )
        }

        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "EXPENSE_RECORDED",
                entityType = "EXPENSE",
                entityId = expense.id,
                newValue = expense.amount.toString() + " ريال",
                notes = "مصروف " + expense.title +
                    "؛ طريقة الدفع " + expense.paymentMethod.name +
                    "؛ الشفت " + expense.shiftId.ifBlank { "خارج الشفت" }
            )
        )
        expense
    }

    private companion object {
        const val CASH_EPSILON = 1e-9
    }
}
