package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.WasteTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Query("SELECT * FROM waste_transactions ORDER BY createdAt DESC")
    fun getAllWaste(): Flow<List<WasteTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaste(waste: WasteTransactionEntity)
}
