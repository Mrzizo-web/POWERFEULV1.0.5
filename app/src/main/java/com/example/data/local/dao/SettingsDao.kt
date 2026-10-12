package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CafeteriaSettingEntity

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: CafeteriaSettingEntity)
}
