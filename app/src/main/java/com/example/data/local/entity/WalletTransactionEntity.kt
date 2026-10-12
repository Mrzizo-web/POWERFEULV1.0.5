package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.WalletTransferStatus
import java.util.UUID

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletCode: String, // JEEB, FLOOSAK, JAWALI
    val amount: Double,
    val transactionId: String,
    val sender: String,
    val receivedAt: Long,
    val shiftId: String? = null,
    val cashierId: String? = null,
    val cashierName: String? = null,
    val status: WalletTransferStatus = WalletTransferStatus.RECEIVED,
    val originalSmsId: String? = null,
    val notes: String = "",
    val rejectionReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
