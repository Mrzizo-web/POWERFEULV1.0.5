package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.RawMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMaterialDao {
    @Query("SELECT * FROM raw_materials ORDER BY name ASC")
    fun getAllRawMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials")
    suspend fun getAllRawMaterialsSync(): List<RawMaterialEntity>

    @Query("SELECT * FROM raw_materials WHERE id = :id LIMIT 1")
    suspend fun getRawMaterialById(id: String): RawMaterialEntity?

    @Query("SELECT * FROM raw_materials WHERE currentStock <= minStock")
    fun getLowStockMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials WHERE currentStock <= minStock")
    suspend fun getLowStockMaterialsSync(): List<RawMaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterialEntity)

    @Update
    suspend fun updateRawMaterial(material: RawMaterialEntity)

    @Query("DELETE FROM raw_materials WHERE id = :id")
    suspend fun deleteRawMaterial(id: String)
}
