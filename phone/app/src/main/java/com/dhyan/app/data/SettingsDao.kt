package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingsEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?
}
