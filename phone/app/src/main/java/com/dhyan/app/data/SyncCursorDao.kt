package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE deviceId = :deviceId LIMIT 1")
    suspend fun find(deviceId: String): SyncCursorEntity?
}
