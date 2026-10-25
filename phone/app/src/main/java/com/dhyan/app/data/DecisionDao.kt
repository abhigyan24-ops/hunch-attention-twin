package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: DecisionEntity): Long

    @Query("SELECT * FROM decisions ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<DecisionEntity>

    @Query("SELECT * FROM decisions ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE ts >= :sinceMs ORDER BY ts DESC")
    suspend fun since(sinceMs: Long): List<DecisionEntity>

    @Query("UPDATE decisions SET actuation_status = :status WHERE key_hash = :keyHash AND posted_ts_ms = :postedTsMs")
    suspend fun updateActuationStatus(keyHash: String, postedTsMs: Long, status: String)
    @Query("SELECT COUNT(*) FROM decisions WHERE action = :action")
    suspend fun countByAction(action: String): Int

    @Query("SELECT COUNT(*) FROM decisions WHERE actuation_status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("DELETE FROM decisions WHERE ts < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
