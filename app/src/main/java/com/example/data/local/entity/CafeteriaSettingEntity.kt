package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class CafeteriaSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
