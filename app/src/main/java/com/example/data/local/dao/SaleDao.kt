package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun getSalesByShift(shiftId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): SaleEntity?

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSalesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsSync(saleId: String): List<SaleItemEntity>
}
