package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.PaymentMethod
import java.util.UUID

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val shiftId: String,
    val userId: String,
    val userName: String,
    val customerId: String = "",
    val customerName: String = "",
    val totalAmount: Double,
    val discountAmount: Double = 0.0,
    val netAmount: Double,
    val paymentMethod: PaymentMethod,
    val paymentReference: String = "",
    val cashReceived: Double = 0.0,
    val cashChange: Double = 0.0,
    val totalCost: Double = 0.0,
    val status: String = "COMPLETED",
    val notes: String = "",
    val isSynced: Boolean = false,
    val voidedAt: Long? = null,
    val voidedByUserId: String = "",
    val voidReason: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
