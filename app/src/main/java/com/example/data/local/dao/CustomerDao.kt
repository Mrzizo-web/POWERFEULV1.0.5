package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CustomerEntity
import com.example.domain.model.CustomerStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE currentDebt > 0 ORDER BY currentDebt DESC")
    fun getIndebtedCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE allowDebt = 1 AND status = 'ACTIVE' ORDER BY name ASC")
    fun getEligibleDebtCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET currentDebt = :newDebt, status = :status, lastTransactionDate = :now WHERE id = :customerId")
    suspend fun updateDebt(customerId: String, newDebt: Double, status: CustomerStatus, now: Long = System.currentTimeMillis())
}
