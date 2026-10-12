package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val purchaseId: String,
    val rawMaterialId: String,
    val rawMaterialName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)
