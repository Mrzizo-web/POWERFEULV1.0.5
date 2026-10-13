package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.PaymentMethod
import java.util.UUID

@Entity(tableName = "debt_transactions")
data class DebtTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val customerName: String,
    val type: String, // SALE, PAYMENT, ADJUSTMENT
    val amount: Double,
    val balanceAfter: Double,
    val balanceBefore: Double = 0.0,
    val shiftId: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val referenceId: String = "",
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)
