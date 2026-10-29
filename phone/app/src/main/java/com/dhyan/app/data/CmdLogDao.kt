package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CmdLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CmdLogEntity)

    @Query("SELECT * FROM cmd_log WHERE cmd_id = :cmdId LIMIT 1")
    suspend fun getById(cmdId: String): CmdLogEntity?

    @Query("SELECT * FROM cmd_log ORDER BY executed_at_ms DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<CmdLogEntity>

    @Query("SELECT * FROM cmd_log ORDER BY executed_at_ms DESC")
    fun observeAll(): Flow<List<CmdLogEntity>>

    @Query("DELETE FROM cmd_log WHERE executed_at_ms < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long): Int
}
