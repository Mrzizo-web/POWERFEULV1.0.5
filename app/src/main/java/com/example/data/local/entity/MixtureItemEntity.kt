package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "mixture_items")
data class MixtureItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val mixtureId: String,
    val rawMaterialId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val costContribution: Double = 0.0
)
