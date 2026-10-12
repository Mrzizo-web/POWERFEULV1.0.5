package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val supplierId: String,
    val supplierName: String,
    val totalAmount: Double,
    val purchaseDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val createdByUserId: String,
    val createdByUserName: String,
    val createdAt: Long = System.currentTimeMillis()
)
