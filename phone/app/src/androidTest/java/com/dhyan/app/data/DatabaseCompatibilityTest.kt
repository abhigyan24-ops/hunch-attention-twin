package com.dhyan.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses isolated synthetic databases, never the user's dhyan.db. */
class DatabaseCompatibilityTest {
    @Test fun existingVersionFourOpensWithoutDowngradingOrLosingSettings() = verifyOpen(4)

    @Test fun versionThreeUpgradesToFourAndPreservesSettings() = verifyOpen(3)

    private fun verifyOpen(version: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "startup-compatibility-$version.db"
        context.deleteDatabase(name)
        try {
            val schema = instrumentation.context.assets
                .open("com.dhyan.app.data.DhyanDatabase/$version.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    fun execute(sql: String) = sqlite.execSQL(sql.replace("\${TABLE_NAME}", table))
                    execute(entity.getString("createSql"))
                    // Room omits the indices member for tables without indices.
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) {
                        for (j in 0 until indices.length()) execute(indices.getJSONObject(j).getString("createSql"))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
                sqlite.execSQL("INSERT INTO settings (`key`, `value`, `updated_ms`) VALUES ('startup_test', 'synthetic', 0)")
                sqlite.version = version
            }
            val room = Room.databaseBuilder(context, DhyanDatabase::class.java, name)
                .addMigrations(DhyanDatabase.MIGRATION_2_3, DhyanDatabase.MIGRATION_3_4)
                .build()
            try {
                val sqlite = room.openHelper.writableDatabase
                assertEquals(4, sqlite.version)
                sqlite.query("SELECT value FROM settings WHERE `key` = 'startup_test'").use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals("synthetic", cursor.getString(0))
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
