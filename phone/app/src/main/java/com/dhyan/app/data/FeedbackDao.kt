package com.dhyan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: FeedbackEntity): Long

    @Query("SELECT * FROM feedback WHERE decision_id = :decisionId")
    suspend fun forDecision(decisionId: Long): List<FeedbackEntity>
}
