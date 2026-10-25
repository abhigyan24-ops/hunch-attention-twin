package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vip: VipEntity)

    @Query("SELECT senderHash FROM vips")
    suspend fun allHashes(): List<String>

    @Query("DELETE FROM vips WHERE senderHash = :hash")
    suspend fun delete(hash: String)
}
