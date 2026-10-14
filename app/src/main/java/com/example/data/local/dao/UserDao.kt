package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveUsers(): Flow<List<UserEntity>>

    /**
     * Login-only recovery list: show the known owner when no account is active,
     * but only if a real salted credential exists to authenticate before activation.
     * Do not include disabled cashiers/admins or an owner whose PIN is unconfigured.
     */
    @Query("""
        SELECT * FROM users
        WHERE isActive = 1 OR (
            id = :ownerId AND role = 'OWNER'
            AND length(trim(pinHash)) > 0 AND length(trim(pinSalt)) > 0
            AND NOT EXISTS (SELECT 1 FROM users WHERE isActive = 1)
        )
        ORDER BY name ASC
    """)
    fun getLoginUsers(ownerId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    suspend fun countActiveUsers(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND isActive = 1")
    suspend fun countActiveOwners(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role IN ('OWNER', 'ADMIN') AND isActive = 1")
    suspend fun countActiveAdminsAndOwners(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String): Int

    @Query("UPDATE users SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivateUser(id: String, updatedAt: Long = System.currentTimeMillis(), isActive: Boolean = false)
}
