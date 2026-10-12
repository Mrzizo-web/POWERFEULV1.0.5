package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "stock_adjustments")
data class StockAdjustmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val stockCountId: String = "",
    val rawMaterialId: String,
    val rawMaterialName: String,
    val systemStock: Double,
    val actualStock: Double,
    val discrepancy: Double,
    val unit: String,
    val reason: String,
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)
