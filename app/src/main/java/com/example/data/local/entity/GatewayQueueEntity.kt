package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "gateway_offline_queue")
data class GatewayQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // INCOMING_SMS, OUTGOING_SYNC, WALLET_NOTIFICATION
    val payload: String, // JSON payload or text
    val targetEndpoint: String = "", // e.g. http://192.168.1.100:8080/api/sync
    val status: String = "PENDING", // PENDING, RETRYING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val lastAttemptAt: Long = 0L,
    val nextAttemptAt: Long = 0L,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
