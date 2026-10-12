package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.InventoryTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_transactions ORDER BY createdAt DESC LIMIT 100")
    fun getRecentTransactions(): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions WHERE rawMaterialId = :materialId ORDER BY createdAt DESC")
    fun getTransactionsForMaterial(materialId: String): Flow<List<InventoryTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: InventoryTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txList: List<InventoryTransactionEntity>)
}
