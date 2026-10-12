package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.WalletEntity
import com.example.data.local.entity.WalletSmsEntity
import com.example.data.local.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY name ASC")
    fun getAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets")
    suspend fun getAllWalletsSync(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE code = :code LIMIT 1")
    suspend fun getWalletByCode(code: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Update
    suspend fun updateWallet(wallet: WalletEntity)

    // SMS Inbox
    @Query("SELECT * FROM wallet_sms_inbox ORDER BY receivedAt DESC")
    fun getAllSms(): Flow<List<WalletSmsEntity>>

    @Query("SELECT * FROM wallet_sms_inbox ORDER BY receivedAt DESC")
    suspend fun getAllSmsSync(): List<WalletSmsEntity>

    @Query("SELECT * FROM wallet_sms_inbox WHERE id = :smsId LIMIT 1")
    suspend fun getSmsById(smsId: String): WalletSmsEntity?

    @Query("SELECT * FROM wallet_sms_inbox WHERE matchedTransactionId = :txId ORDER BY receivedAt ASC")
    fun getSmsForTransaction(txId: String): Flow<List<WalletSmsEntity>>

    @Query("SELECT * FROM wallet_sms_inbox WHERE matchedTransactionId = :txId ORDER BY receivedAt ASC")
    suspend fun getSmsForTransactionSync(txId: String): List<WalletSmsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSms(sms: WalletSmsEntity)

    @Update
    suspend fun updateSms(sms: WalletSmsEntity)

    // Wallet Transactions
    @Query("SELECT * FROM wallet_transactions ORDER BY receivedAt DESC")
    fun getAllTransactions(): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions")
    suspend fun getAllTransactionsSync(): List<WalletTransactionEntity>

    @Query("SELECT * FROM wallet_transactions WHERE shiftId = :shiftId ORDER BY receivedAt DESC")
    fun getTransactionsByShift(shiftId: String): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions WHERE walletCode = :walletCode ORDER BY receivedAt DESC")
    fun getTransactionsByWallet(walletCode: String): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions WHERE status = 'OUT_OF_SHIFT' ORDER BY receivedAt DESC")
    fun getTransactionsOutOfShift(): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): WalletTransactionEntity?

    @Query("SELECT * FROM wallet_transactions WHERE walletCode = :walletCode AND transactionId = :txId LIMIT 1")
    suspend fun findTransactionByTxId(walletCode: String, txId: String): WalletTransactionEntity?

    @Query("SELECT * FROM wallet_transactions WHERE walletCode = :walletCode AND amount = :amount AND sender = :sender AND receivedAt BETWEEN :minTime AND :maxTime LIMIT 1")
    suspend fun findPotentialDuplicate(walletCode: String, amount: Double, sender: String, minTime: Long, maxTime: Long): WalletTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: WalletTransactionEntity)

    @Update
    suspend fun updateTransaction(tx: WalletTransactionEntity)

    @Query("DELETE FROM wallet_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)
}
