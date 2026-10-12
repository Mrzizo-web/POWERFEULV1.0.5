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

    /**
     * Atomic claim: only one worker can move a pending row to IN_PROGRESS.
     * Returns 1 to the winner, 0 to concurrent workers.
     */
    @Query("UPDATE gateway_offline_queue SET status = 'IN_PROGRESS', lastAttemptAt = :now WHERE id = :id AND status IN ('PENDING', 'RETRYING')")
    suspend fun claimItem(id: String, now: Long = System.currentTimeMillis()): Int

    /** Recover rows left in flight if Android killed the process mid-request. */
    @Query("UPDATE gateway_offline_queue SET status = 'RETRYING', nextAttemptAt = :now WHERE status = 'IN_PROGRESS'")
    suspend fun recoverInProgressItems(now: Long = System.currentTimeMillis()): Int

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
