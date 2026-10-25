package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: AppLabelEntity)

    @Query("SELECT * FROM app_labels WHERE pkg = :pkg LIMIT 1")
    suspend fun findByPkg(pkg: String): AppLabelEntity?

    @Query("SELECT * FROM app_labels")
    suspend fun loadAll(): List<AppLabelEntity>
}
