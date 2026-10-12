package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "mixtures")
data class MixtureEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val outputQuantity: Double,
    val unit: String,
    val totalCost: Double = 0.0,
    val unitCost: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
