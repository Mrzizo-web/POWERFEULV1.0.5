package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val code: String, // JEEB, FLOOSAK, JAWALI
    val name: String, // جيب, فلوسك, جوالي
    val enabled: Boolean = true,
    val iconName: String = "account_balance_wallet"
)
