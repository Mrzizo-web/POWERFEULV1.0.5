package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.WasteReason
import java.util.UUID

@Entity(tableName = "waste_transactions")
data class WasteTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rawMaterialId: String,
    val rawMaterialName: String,
    val quantity: Double,
    val unit: String,
    val estimatedCost: Double,
    val reason: WasteReason,
    val notes: String = "",
    val userId: String,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)
