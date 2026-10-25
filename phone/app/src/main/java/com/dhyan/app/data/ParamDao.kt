package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ParamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(params: List<ParamEntity>)

    @Query("SELECT * FROM params")
    suspend fun loadAll(): List<ParamEntity>

    @Query("DELETE FROM params WHERE key = :key")
    suspend fun delete(key: String)
}
