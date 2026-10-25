package com.dhyan.app.personas

import com.dhyan.app.personas.export.*
import com.dhyan.app.personas.world.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 4A-4: Fixture export test. Generates and writes fixtures for all three personas.
 * Outputs go to build/dhyan-fixtures/ (not committed).
 * Also prints SHA-256 hashes for determinism verification per prompt 4A-6.
 */
class FixtureExportTest {

    private val fixtureDir = File("build/dhyan-fixtures").apply { mkdirs() }

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun generateFixturesForAllPersonas() {
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val persona = PersonaCatalog.byName(name)
            val config  = GeneratorConfig(seed = 20261025L, days = 14)
            val history = HistoryGenerator.generate(persona, config)

            val eventsFile = File(fixtureDir, "${name.lowercase()}_MATCHED_ShowAll_seed20261025.events.jsonl")
            val truthFile  = File(fixtureDir, "${name.lowercase()}_MATCHED_ShowAll_seed20261025.truth.json")

            EventJsonlWriter.writeToFile(history.events, eventsFile)
            TruthJsonWriter.writeToFile(history.truthRecords, persona, config, truthFile)

            assertTrue("events file missing", eventsFile.exists())
            assertTrue("truth file missing", truthFile.exists())
            assertTrue("events file empty", eventsFile.length() > 0)
            assertTrue("truth file empty", truthFile.length() > 0)

            // Determinism: generate again and compare hashes
            val history2 = HistoryGenerator.generate(persona, config)
            val eventsFile2 = File(fixtureDir, "${name.lowercase()}_check2.events.jsonl")
            EventJsonlWriter.writeToFile(history2.events, eventsFile2)
            val hash1 = sha256(eventsFile); val hash2 = sha256(eventsFile2)
            assertEquals("SHA-256 mismatch for $name (non-deterministic!)", hash1, hash2)
            eventsFile2.delete()

            println("FIXTURE $name: events=${history.events.size} truth=${history.truthRecords.size} sha256=$hash1")
        }
        // Rahul RandomizedHold for 4 days
        val rahul = PersonaCatalog.byName("Rahul")
        val holdConfig = GeneratorConfig(seed = 20261025L, days = 4, loggingPolicy = LoggingPolicy.RandomizedHold(0.2, 0.8))
        val holdHistory = HistoryGenerator.generate(rahul, holdConfig)
        val holdEvents = File(fixtureDir, "rahul_MATCHED_RandomizedHold_seed20261025.events.jsonl")
        val holdTruth  = File(fixtureDir, "rahul_MATCHED_RandomizedHold_seed20261025.truth.json")
        EventJsonlWriter.writeToFile(holdHistory.events, holdEvents)
        TruthJsonWriter.writeToFile(holdHistory.truthRecords, rahul, holdConfig, holdTruth)
        assertTrue(holdEvents.exists()); assertTrue(holdEvents.length() > 0)
        println("FIXTURE Rahul/Hold: events=${holdHistory.events.size} truth=${holdHistory.truthRecords.size}")

        // Rahul MILD_MISSPEC for 4 days
        val msConfig = GeneratorConfig(seed = 20261025L, days = 4, world = World.MILD_MISSPEC)
        val msHistory = HistoryGenerator.generate(rahul, msConfig)
        val msEvents = File(fixtureDir, "rahul_MILD_MISSPEC_ShowAll_seed20261025.events.jsonl")
        val msTruth  = File(fixtureDir, "rahul_MILD_MISSPEC_ShowAll_seed20261025.truth.json")
        EventJsonlWriter.writeToFile(msHistory.events, msEvents)
        TruthJsonWriter.writeToFile(msHistory.truthRecords, rahul, msConfig, msTruth)
        assertTrue(msEvents.exists()); assertTrue(msEvents.length() > 0)
        println("FIXTURE Rahul/MildMisspec: events=${msHistory.events.size} truth=${msHistory.truthRecords.size}")
    }

    @Test fun roundTripJsonlReader() {
        // Quick round-trip: write and read back
        val persona = PersonaCatalog.byName("Rahul")
        val config  = GeneratorConfig(seed = 777L, days = 1)
        val history = HistoryGenerator.generate(persona, config)
        val file    = File(fixtureDir, "roundtrip_test.events.jsonl")
        EventJsonlWriter.writeToFile(history.events, file)
        val maps = EventJsonlReader.readMaps(file)
        assertEquals(history.events.size, maps.size)
        for (m in maps) {
            assertTrue(m.containsKey("id")); assertTrue(m["synthetic"] == true)
            val payload = m["payload"]
            assertNotNull(payload); assertTrue(payload is Map<*, *>)
        }
        // Partial line: append a bad last line
        val bad = File(fixtureDir, "bad_partial.events.jsonl")
        bad.writeText(file.readText() + "{\"partial\":")
        try { EventJsonlReader.readMaps(bad); fail("Expected exception on partial line") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("newline")) }
        file.delete(); bad.delete()
    }
}
