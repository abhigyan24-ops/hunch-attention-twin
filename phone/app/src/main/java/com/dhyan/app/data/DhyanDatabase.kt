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
        CmdLogEntity::class,
    ],
    version = 3,
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
    abstract fun cmdLogDao(): CmdLogDao

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

                // --- decisions: drop and recreate ---
                db.execSQL("DROP TABLE IF EXISTS `decisions`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `decisions` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ts`               INTEGER NOT NULL,
                        `key_hash`         TEXT NOT NULL,
                        `action`           TEXT NOT NULL,
                        `reason`           TEXT NOT NULL,
                        `explanation`      TEXT NOT NULL,
                        `cost_diff`        REAL,
                        `delta_l_hat`      REAL,
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

        /**
         * Migration 2 → 3: Non-destructive addition of cmd_log table and canonical
         * sync envelope identity columns to events table (Stage 8P1, Schema 1.1.0).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create cmd_log table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cmd_log` (
                        `cmd_id`         TEXT NOT NULL,
                        `source`         TEXT NOT NULL,
                        `action`         TEXT NOT NULL,
                        `status`         TEXT NOT NULL,
                        `reason`         TEXT,
                        `ts_ms`          INTEGER NOT NULL,
                        `executed_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`cmd_id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cmd_log_ts_ms` ON `cmd_log` (`ts_ms`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cmd_log_action` ON `cmd_log` (`action`)")

                // 2. Add envelope columns to events table non-destructively
                db.execSQL("ALTER TABLE `events` ADD COLUMN `event_id` TEXT")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `seq` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'phone'")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `settled_status` TEXT NOT NULL DEFAULT 'valid'")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `schema_version` TEXT NOT NULL DEFAULT '1.1.0'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_device_id_seq_event_id` ON `events` (`device_id`, `seq`, `event_id`)")
            }
        }

        fun getInstance(context: Context): DhyanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DhyanDatabase::class.java,
                    "dhyan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
