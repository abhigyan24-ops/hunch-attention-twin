package com.dhyan.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhyan.app.sync.SyncTestVectorsLoader
import org.junit.Assert.assertEquals
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

        // 1. Verify quarantine table creation
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `events_quarantine`") })

        // 2. Verify per-device row numbering assignment
        assertTrue(executedSql.any {
            it.contains("ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY id)")
        })

        // 3. Verify exact event_id deduplication moving to quarantine
        assertTrue(executedSql.any {
            it.contains("INSERT INTO events_quarantine") && it.contains("duplicate_event_id")
        })

        // 4. Verify dropping non-unique index
        assertTrue(executedSql.any { it.contains("DROP INDEX IF EXISTS `index_events_device_id_seq_event_id`") })

        // 5. Verify creating strict UNIQUE index on (device_id, seq)
        assertTrue(executedSql.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_events_device_id_seq` ON `events` (`device_id`, `seq`)")
        })

        // 6. Verify creating index on event_id
        assertTrue(executedSql.any {
            it.contains("CREATE INDEX IF NOT EXISTS `index_events_event_id` ON `events` (`event_id`)")
        })
    }

    @Test
    fun migration_3_4_preserves1000LegacyRowsAndQuarantinesDuplicates() {
        // Collect exact SQL from MIGRATION_3_4
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

        // Verify statements
        assertTrue("Must create events_quarantine table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `events_quarantine`") })
        assertTrue("Must NOT delete rows grouped by (device_id, seq)", executedSql.none { it.contains("GROUP BY device_id, seq") })
        assertTrue("Must re-sequence per device in id order", executedSql.any {
            it.contains("ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY id)")
        })

        val statementsJoined = executedSql.joinToString(";;;\n")
        val pyCode = """
import sqlite3, sys

conn = sqlite3.connect(':memory:')
c = conn.cursor()

c.execute('''
CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    ts INTEGER NOT NULL,
    kind TEXT NOT NULL,
    app_cat TEXT NOT NULL,
    surface TEXT NOT NULL,
    duration_min REAL NOT NULL,
    sender_hash TEXT,
    notif_cat TEXT,
    scroll_count INTEGER NOT NULL,
    extras_json TEXT,
    event_id TEXT,
    seq INTEGER NOT NULL DEFAULT 0,
    device_id TEXT NOT NULL DEFAULT 'phone',
    settled_status TEXT NOT NULL DEFAULT 'valid',
    schema_version TEXT NOT NULL DEFAULT '1.1.0'
)
''')

rows = []
for i in range(1, 1001):
    rows.append((10000 + i, 'screen_on', 'system', 'launcher', 0.0, None, None, 0, None, f'uuid-{i}', 0, 'phone', 'valid', '1.1.0'))
c.executemany('''
INSERT INTO events (ts, kind, app_cat, surface, duration_min, sender_hash, notif_cat, scroll_count, extras_json, event_id, seq, device_id, settled_status, schema_version)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
''', rows)

for i in range(1, 6):
    c.execute('''
    INSERT INTO events (ts, kind, app_cat, surface, duration_min, sender_hash, notif_cat, scroll_count, extras_json, event_id, seq, device_id, settled_status, schema_version)
    VALUES (?, 'screen_on', 'system', 'launcher', 0.0, NULL, NULL, 0, NULL, ?, 0, 'phone', 'valid', '1.1.0')
    ''', (90000 + i, f'uuid-{i}'))

assert c.execute('SELECT COUNT(*) FROM events').fetchone()[0] == 1005

stmts = '''${statementsJoined}'''.split(';;;\n')
for s in stmts:
    s = s.strip()
    if s:
        c.execute(s)

events_count = c.execute('SELECT COUNT(*) FROM events').fetchone()[0]
quarantine_count = c.execute('SELECT COUNT(*) FROM events_quarantine').fetchone()[0]
min_seq, max_seq = c.execute('SELECT MIN(seq), MAX(seq) FROM events').fetchone()

if events_count != 1000:
    print(f'FAIL: Expected 1000 preserved events, got {events_count}', file=sys.stderr)
    sys.exit(1)

if quarantine_count != 5:
    print(f'FAIL: Expected 5 quarantined rows, got {quarantine_count}', file=sys.stderr)
    sys.exit(2)

if min_seq != 1 or max_seq != 1000:
    print(f'FAIL: Expected sequence 1..1000, got min={min_seq}, max={max_seq}', file=sys.stderr)
    sys.exit(3)

print('SUCCESS')
"""
        val process = ProcessBuilder("python", "-c", pyCode)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals("Migration script output: $output", 0, exitCode)
        assertTrue("Output must contain SUCCESS", output.contains("SUCCESS"))
    }

    @Test
    fun testVectorsResourceIsAccessibleInClasspath() {
        val text = SyncTestVectorsLoader.readText()
        assertNotNull("test-vectors.json must be loaded", text)
        assertTrue("test-vectors.json must contain cryptographic keys", text.contains("laptopKeys") && text.contains("phoneKeys"))
    }
}
