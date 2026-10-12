package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.InventoryTxType
import java.util.UUID

@Entity(tableName = "inventory_transactions")
data class InventoryTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rawMaterialId: String,
    val rawMaterialName: String,
    val type: InventoryTxType,
    val quantityChange: Double,
    val unit: String,
    val previousQuantity: Double,
    val newQuantity: Double,
    val referenceId: String = "",
    val notes: String = "",
    val userId: String = "",
    val userName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
