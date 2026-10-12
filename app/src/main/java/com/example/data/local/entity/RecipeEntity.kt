package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val name: String,
    val calculatedCost: Double = 0.0,
    val version: Int = 1,
    val isActive: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
