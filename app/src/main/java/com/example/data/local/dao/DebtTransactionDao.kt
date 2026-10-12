package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.DebtTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtTransactionDao {
    @Query("SELECT * FROM debt_transactions ORDER BY createdAt DESC")
    fun getRecentDebtTransactions(): Flow<List<DebtTransactionEntity>>

    @Query("SELECT * FROM debt_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getTransactionsForCustomer(customerId: String): Flow<List<DebtTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtTransaction(tx: DebtTransactionEntity)
}
