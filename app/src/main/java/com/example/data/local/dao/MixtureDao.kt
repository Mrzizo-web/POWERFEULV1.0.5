package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.MixtureEntity
import com.example.data.local.entity.MixtureItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MixtureDao {
    @Query("SELECT * FROM mixtures ORDER BY name ASC")
    fun getAllMixtures(): Flow<List<MixtureEntity>>

    @Query("SELECT * FROM mixtures")
    suspend fun getAllMixturesSync(): List<MixtureEntity>

    @Query("SELECT * FROM mixtures WHERE id = :id LIMIT 1")
    suspend fun getMixtureById(id: String): MixtureEntity?

    @Query("SELECT * FROM mixture_items WHERE mixtureId = :mixtureId")
    fun getMixtureItems(mixtureId: String): Flow<List<MixtureItemEntity>>

    @Query("SELECT * FROM mixture_items WHERE mixtureId = :mixtureId")
    suspend fun getMixtureItemsSync(mixtureId: String): List<MixtureItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixture(mixture: MixtureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixtureItems(items: List<MixtureItemEntity>)

    @Query("DELETE FROM mixture_items WHERE mixtureId = :mixtureId")
    suspend fun deleteMixtureItems(mixtureId: String)

    @Query("DELETE FROM mixtures WHERE id = :id")
    suspend fun deleteMixture(id: String)
}
