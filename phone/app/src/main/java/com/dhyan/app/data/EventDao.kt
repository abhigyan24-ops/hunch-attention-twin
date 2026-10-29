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

    // --- Sync Data Flow queries (Contract Section 5 & 10) ---

    @Query("SELECT * FROM events WHERE device_id = :deviceId AND seq > :afterSeq ORDER BY seq ASC LIMIT :limit")
    suspend fun getOutboundAfterSeq(deviceId: String, afterSeq: Long, limit: Int = 50): List<EventEntity>

    @Query("SELECT MAX(seq) FROM events WHERE device_id = :deviceId")
    suspend fun getHighestSeq(deviceId: String): Long?

    @Query("SELECT * FROM events WHERE device_id = :deviceId AND event_id = :eventId LIMIT 1")
    suspend fun findByDeviceAndId(deviceId: String, eventId: String): EventEntity?
}
