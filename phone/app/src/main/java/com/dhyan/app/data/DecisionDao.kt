package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: DecisionEntity): Long

    @Query("SELECT * FROM decisions ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<DecisionEntity>

    @Query("SELECT * FROM decisions WHERE ts >= :sinceMs ORDER BY ts DESC")
    suspend fun since(sinceMs: Long): List<DecisionEntity>
}
