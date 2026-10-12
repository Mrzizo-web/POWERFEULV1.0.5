package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CustomerStatus
import java.util.UUID

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val creditLimit: Double = 0.0,
    val currentDebt: Double = 0.0,
    val allowDebt: Boolean = true,
    val status: CustomerStatus = CustomerStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastTransactionDate: Long = System.currentTimeMillis()
)
