package com.dhyan.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true
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

        /**
         * Migration 1 → 2: Drop and recreate held_notifs with the Stage 6 schema.
         * Also drop and recreate decisions with the fuller Stage 6 schema.
         *
         * This is safe for debug builds only. No real user data is held in v1.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- held_notifs: drop and recreate ---
                db.execSQL("DROP TABLE IF EXISTS `held_notifs`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `held_notifs` (
                        `keyHash`        TEXT NOT NULL,
                        `mechanism`      TEXT NOT NULL,
                        `state`          TEXT NOT NULL,
                        `app_cat`        TEXT NOT NULL,
                        `notif_cat`      TEXT NOT NULL,
                        `sender_hash`    TEXT,
                        `posted_ts_ms`   INTEGER NOT NULL,
                        `held_since_ms`  INTEGER NOT NULL,
                        `hold_until_ms`  INTEGER NOT NULL,
                        `expires_at_ms`  INTEGER NOT NULL,
                        `ciphertext`     TEXT,
                        `iv`             TEXT,
                        `platform_id`    INTEGER NOT NULL,
                        `pkg_hash`       TEXT NOT NULL,
                        `decision_action` TEXT NOT NULL,
                        `propensity`     REAL NOT NULL,
                        `user_override`  INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`keyHash`)
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_held_notifs_state_hold_until_ms` ON `held_notifs` (`state`, `hold_until_ms`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_held_notifs_keyHash` ON `held_notifs` (`keyHash`)")

                // --- decisions: drop and recreate ---
                db.execSQL("DROP TABLE IF EXISTS `decisions`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `decisions` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ts`               INTEGER NOT NULL,
                        `key_hash`         TEXT NOT NULL,
                        `posted_ts_ms`     INTEGER NOT NULL,
                        `action`           TEXT NOT NULL,
                        `reason_code`      TEXT NOT NULL,
                        `safety_reason`    TEXT,
                        `p_loss_show`      REAL,
                        `p_loss_suppress`  REAL,
                        `delta_l_mean`     REAL,
                        `delta_l_p10`      REAL,
                        `delta_l_p90`      REAL,
                        `evidence_strength` REAL,
                        `propensity`       REAL NOT NULL,
                        `p_show`           REAL NOT NULL,
                        `exploration`      INTEGER NOT NULL,
                        `failed_open`      INTEGER NOT NULL,
                        `importance`       REAL NOT NULL,
                        `model_version`    INTEGER NOT NULL,
                        `policy_version`   TEXT NOT NULL,
                        `mode`             TEXT NOT NULL,
                        `actuation_status` TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_decisions_ts` ON `decisions` (`ts`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_decisions_key_hash` ON `decisions` (`key_hash`)")
            }
        }

        fun getInstance(context: Context): DhyanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DhyanDatabase::class.java,
                    "dhyan.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
