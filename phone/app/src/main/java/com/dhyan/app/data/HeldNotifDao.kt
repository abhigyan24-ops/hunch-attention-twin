package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HeldNotifDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notif: HeldNotifEntity)

    @Query("SELECT * FROM held_notifs WHERE delivered = 0 AND deliver_after_ms <= :nowMs")
    suspend fun pendingDelivery(nowMs: Long): List<HeldNotifEntity>

    @Query("SELECT * FROM held_notifs WHERE delivered = 0")
    suspend fun allPending(): List<HeldNotifEntity>

    @Query("UPDATE held_notifs SET delivered = 1 WHERE notifId = :id")
    suspend fun markDelivered(id: String)
}
