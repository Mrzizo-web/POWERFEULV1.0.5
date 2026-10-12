package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.GatewayQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GatewayQueueDao {
    @Query("SELECT * FROM gateway_offline_queue ORDER BY createdAt DESC")
    fun getAllQueueItems(): Flow<List<GatewayQueueEntity>>

    @Query("SELECT * FROM gateway_offline_queue WHERE status IN ('PENDING', 'RETRYING') ORDER BY createdAt ASC")
    fun getPendingQueueItems(): Flow<List<GatewayQueueEntity>>

    @Query("SELECT * FROM gateway_offline_queue WHERE status IN ('PENDING', 'RETRYING') ORDER BY createdAt ASC")
    suspend fun getPendingQueueItemsSync(): List<GatewayQueueEntity>

    @Query("SELECT COUNT(*) FROM gateway_offline_queue WHERE status IN ('PENDING', 'RETRYING')")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GatewayQueueEntity)

    @Update
    suspend fun updateItem(item: GatewayQueueEntity)

    @Query("DELETE FROM gateway_offline_queue WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM gateway_offline_queue WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM gateway_offline_queue")
    suspend fun clearAll()
}
