package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.StockAdjustmentEntity
import com.example.data.local.entity.StockCountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_adjustments ORDER BY createdAt DESC")
    fun getAllStockAdjustments(): Flow<List<StockAdjustmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockAdjustment(adjustment: StockAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockCount(count: StockCountEntity)

    @Query("SELECT * FROM stock_counts ORDER BY date DESC")
    fun getAllStockCounts(): Flow<List<StockCountEntity>>
}
