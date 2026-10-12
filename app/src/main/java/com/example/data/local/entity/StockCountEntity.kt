package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "stock_counts")
data class StockCountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val countedByUserId: String,
    val countedByUserName: String,
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isApplied: Boolean = false
)
