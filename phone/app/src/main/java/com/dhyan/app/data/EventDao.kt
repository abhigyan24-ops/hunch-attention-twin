package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE ts >= :sinceMs ORDER BY ts DESC LIMIT :limit")
    suspend fun recentEvents(sinceMs: Long, limit: Int = 500): List<EventEntity>

    @Query("SELECT * FROM events WHERE kind = :kind AND ts >= :sinceMs ORDER BY ts DESC LIMIT :limit")
    suspend fun recentByKind(kind: String, sinceMs: Long, limit: Int = 100): List<EventEntity>

    @Query("DELETE FROM events WHERE ts < :beforeMs")
    suspend fun purgeOlderThan(beforeMs: Long): Int
}
