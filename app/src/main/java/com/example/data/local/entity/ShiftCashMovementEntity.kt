package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shift_cash_movements")
data class ShiftCashMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val shiftId: String,
    val type: String, // EXPENSE, DROP, PAYOUT, ADD
    val amount: Double,
    val referenceId: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
