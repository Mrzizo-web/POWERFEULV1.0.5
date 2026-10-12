package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val categoryId: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val sku: String = "",
    val barcode: String = "",
    val imageUri: String = "",
    val recipeId: String = "",
    val minStockAlert: Double = 5.0,
    val isActive: Boolean = true,
    val isAvailable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
