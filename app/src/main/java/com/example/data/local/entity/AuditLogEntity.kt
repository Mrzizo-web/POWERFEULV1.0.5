package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val userRole: String,
    val action: String,
    val entityType: String = "",
    val entityId: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
