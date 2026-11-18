package com.smartai.adsmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartai.adsmanager.data.entity.ConnectedAppEntity
import com.smartai.adsmanager.data.entity.RewardEntity
import com.smartai.adsmanager.data.entity.SessionEntity

@Dao
interface RewardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RewardEntity)

    @Update
    suspend fun update(entity: RewardEntity)

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getById(id: String): RewardEntity?

    @Query("SELECT * FROM rewards")
    suspend fun getAll(): List<RewardEntity>

    @Query("SELECT * FROM rewards WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getBySession(sessionId: String): List<RewardEntity>

    @Query("SELECT * FROM rewards WHERE status = :status")
    suspend fun getByStatus(status: String): List<RewardEntity>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SessionEntity)

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC")
    suspend fun all(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ConnectedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConnectedAppEntity)

    @Query("SELECT * FROM connected_apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): ConnectedAppEntity?

    @Query("SELECT * FROM connected_apps ORDER BY appName COLLATE NOCASE ASC")
    suspend fun all(): List<ConnectedAppEntity>

    @Query("DELETE FROM connected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM connected_apps")
    suspend fun clear()
}
