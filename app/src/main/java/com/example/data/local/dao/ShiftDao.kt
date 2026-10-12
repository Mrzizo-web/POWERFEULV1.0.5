package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ShiftCashMovementEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.ShiftHandoverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun getAllShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    fun getCurrentOpenShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    suspend fun getCurrentOpenShiftSync(): ShiftEntity?

    @Query("""
        SELECT * FROM shifts
        WHERE startTime <= :receivedAt
          AND (endTime IS NULL OR endTime >= :receivedAt)
        ORDER BY startTime DESC
        LIMIT 1
    """)
    suspend fun getShiftContainingTime(receivedAt: Long): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getShiftById(id: String): ShiftEntity?

    @Query("SELECT MAX(shiftNumber) FROM shifts")
    suspend fun getLastShiftNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: ShiftCashMovementEntity)

    @Query("SELECT * FROM shift_cash_movements WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun getCashMovements(shiftId: String): Flow<List<ShiftCashMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandover(handover: ShiftHandoverEntity)

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC LIMIT 20")
    fun getRecentHandovers(): Flow<List<ShiftHandoverEntity>>

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHandover(): ShiftHandoverEntity?
}
