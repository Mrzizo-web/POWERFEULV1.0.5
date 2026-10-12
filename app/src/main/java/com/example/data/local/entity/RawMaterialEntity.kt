package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "raw_materials")
data class RawMaterialEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUnit: String,
    val currentStock: Double,
    val minStock: Double = 0.0,
    val avgCostPerUnit: Double = 0.0,
    val lastPurchasePrice: Double = 0.0,
    val supplierId: String = "",
    val sku: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
