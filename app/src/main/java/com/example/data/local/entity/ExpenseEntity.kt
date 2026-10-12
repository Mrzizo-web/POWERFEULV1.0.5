package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.PaymentMethod
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val shiftId: String = "",
    val title: String,
    val category: String,
    val amount: Double,
    val paidTo: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)
