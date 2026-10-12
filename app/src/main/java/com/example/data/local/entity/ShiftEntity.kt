package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.ShiftStatus
import java.util.UUID

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val shiftNumber: Int = 1,
    val userId: String,
    val userName: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val openingCash: Double = 0.0,
    val totalCashSales: Double = 0.0,
    val totalWalletSales: Double = 0.0,
    val totalDebtSales: Double = 0.0,
    val totalExpensesCash: Double = 0.0,
    val expectedCash: Double = 0.0,
    val actualCash: Double = 0.0,
    val discrepancyAmount: Double = 0.0,
    val handedOverCash: Double = 0.0,
    val leftForNextShiftCash: Double = 0.0,
    val status: ShiftStatus = ShiftStatus.OPEN,
    val notes: String = ""
)
