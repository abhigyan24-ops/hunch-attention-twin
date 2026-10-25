package com.dhyan.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        ParamEntity::class,
        AppLabelEntity::class,
        HeldNotifEntity::class,
        DecisionEntity::class,
        FeedbackEntity::class,
        VipEntity::class,
        SyncCursorEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class DhyanDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun paramDao(): ParamDao
    abstract fun appLabelDao(): AppLabelDao
    abstract fun heldNotifDao(): HeldNotifDao
    abstract fun decisionDao(): DecisionDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun vipDao(): VipDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: DhyanDatabase? = null

        fun getInstance(context: Context): DhyanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DhyanDatabase::class.java,
                    "dhyan.db"
                ).build().also { INSTANCE = it }
            }
    }
}
