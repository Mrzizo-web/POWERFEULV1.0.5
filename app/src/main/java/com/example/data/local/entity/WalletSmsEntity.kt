package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.SmsProcessingStatus
import java.util.UUID

@Entity(tableName = "wallet_sms_inbox")
data class WalletSmsEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletCode: String,
    val receivedAt: Long,
    val sender: String,
    val body: String,
    val parsedAmount: Double = 0.0,
    val parsedTransactionId: String = "",
    val parsedSender: String = "",
    val processingStatus: SmsProcessingStatus = SmsProcessingStatus.PROCESSED,
    val matchedTransactionId: String? = null
)
