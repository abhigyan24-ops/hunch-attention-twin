package com.dhyan.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhyan.app.sync.SyncTestVectorsLoader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DhyanDatabaseMigrationTest {

    @Test
    fun migration_2_3_executesAllRequiredDdlStatements() {
        val executedSql = mutableListOf<String>()

        val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DhyanDatabase.MIGRATION_2_3.migrate(dummyDb)

        // Verify cmd_log table creation
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `cmd_log`") })
        assertTrue(executedSql.any { it.contains("index_cmd_log_ts_ms") })
        assertTrue(executedSql.any { it.contains("index_cmd_log_action") })

        // Verify non-destructive events table migration with canonical envelope columns
        assertTrue(executedSql.any { it.contains("ALTER TABLE `events` ADD COLUMN `event_id`") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `events` ADD COLUMN `seq`") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `events` ADD COLUMN `device_id`") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `events` ADD COLUMN `settled_status`") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `events` ADD COLUMN `schema_version`") })
        assertTrue(executedSql.any { it.contains("index_events_device_id_seq_event_id") })
    }

    @Test
    fun migration_3_4_executesDeduplicationAndUniqueIndexDdl() {
        val executedSql = mutableListOf<String>()

        val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DhyanDatabase.MIGRATION_3_4.migrate(dummyDb)

        // 1. Verify deduplication statement keeping highest id for each (device_id, seq)
        assertTrue(executedSql.any {
            it.contains("DELETE FROM events") && it.contains("MAX(id)") && it.contains("GROUP BY device_id, seq")
        })

        // 2. Verify dropping non-unique index
        assertTrue(executedSql.any { it.contains("DROP INDEX IF EXISTS `index_events_device_id_seq_event_id`") })

        // 3. Verify creating strict UNIQUE index on (device_id, seq)
        assertTrue(executedSql.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_events_device_id_seq` ON `events` (`device_id`, `seq`)")
        })

        // 4. Verify creating index on event_id
        assertTrue(executedSql.any {
            it.contains("CREATE INDEX IF NOT EXISTS `index_events_event_id` ON `events` (`event_id`)")
        })
    }

    @Test
    fun testVectorsResourceIsAccessibleInClasspath() {
        val text = SyncTestVectorsLoader.readText()
        assertNotNull("test-vectors.json must be loaded", text)
        assertTrue("test-vectors.json must contain cryptographic keys", text.contains("laptopKeys") && text.contains("phoneKeys"))
    }
}
