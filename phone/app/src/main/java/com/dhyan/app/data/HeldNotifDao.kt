package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HeldNotifDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notif: HeldNotifEntity)

    @Update
    suspend fun update(notif: HeldNotifEntity)

    @Query("SELECT * FROM held_notifs WHERE keyHash = :keyHash")
    suspend fun get(keyHash: String): HeldNotifEntity?

    /** Items due for release right now (state = HELD | SNOOZED and past holdUntilMs). */
    @Query("""
        SELECT * FROM held_notifs
        WHERE state IN ('HELD', 'SNOOZED')
          AND hold_until_ms > 0
          AND hold_until_ms <= :nowMs
    """)
    suspend fun dueForRelease(nowMs: Long): List<HeldNotifEntity>

    /** Items currently held (pending digest display). */
    @Query("SELECT * FROM held_notifs WHERE state IN ('HELD', 'SNOOZED') ORDER BY held_since_ms DESC")
    suspend fun allActive(): List<HeldNotifEntity>

    /** Flow of active items for UI. */
    @Query("SELECT * FROM held_notifs WHERE state IN ('HELD', 'SNOOZED') ORDER BY held_since_ms DESC")
    fun observeActive(): Flow<List<HeldNotifEntity>>

    /** Flow of released/expired history for UI strip. */
    @Query("SELECT * FROM held_notifs WHERE state IN ('RELEASED', 'EXPIRED', 'FAILED_OPEN', 'ACTUATION_UNKNOWN') ORDER BY held_since_ms DESC LIMIT :limit")
    fun observeHistory(limit: Int = 20): Flow<List<HeldNotifEntity>>

    /** Rows stuck in PENDING (crash recovery). */
    @Query("SELECT * FROM held_notifs WHERE state = 'PENDING' AND held_since_ms < :olderThanMs")
    suspend fun stuckPending(olderThanMs: Long): List<HeldNotifEntity>

    @Query("UPDATE held_notifs SET state = :state WHERE keyHash = :keyHash")
    suspend fun setState(keyHash: String, state: String)

    @Query("UPDATE held_notifs SET state = :state, user_override = 1 WHERE keyHash = :keyHash")
    suspend fun setStateWithOverride(keyHash: String, state: String)

    /** Expire rows that have passed their retention window. */
    @Query("DELETE FROM held_notifs WHERE expires_at_ms < :nowMs AND state NOT IN ('PENDING', 'HELD', 'SNOOZED')")
    suspend fun deleteExpired(nowMs: Long)

    /** Count by state for insights. */
    @Query("SELECT COUNT(*) FROM held_notifs WHERE state = :state")
    suspend fun countByState(state: String): Int
}
