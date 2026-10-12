package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CafeteriaSettingEntity

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: CafeteriaSettingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingIfMissing(setting: CafeteriaSettingEntity): Long

    @Query("DELETE FROM settings WHERE `key` LIKE 'gateway_nonce:%' AND CAST(value AS INTEGER) < :cutoff")
    suspend fun deleteExpiredGatewayNonces(cutoff: Long): Int

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
