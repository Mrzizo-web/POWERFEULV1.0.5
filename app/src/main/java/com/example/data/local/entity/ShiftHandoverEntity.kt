package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shift_handovers")
data class ShiftHandoverEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromShiftId: String,
    val fromUserId: String,
    val fromUserName: String,
    val toShiftId: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val expectedLeftAmount: Double,
    val actualReceivedAmount: Double,
    val discrepancy: Double = 0.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
