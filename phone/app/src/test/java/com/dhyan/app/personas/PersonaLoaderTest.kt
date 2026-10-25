package com.dhyan.app.personas

import com.dhyan.app.personas.json.JsonReader
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PersonaLoaderTest {
    private fun source(name: String = "rahul"): String {
        val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        val file = roots.map { File(it, "tools/personas/$name.json") }.first { it.isFile }
        return file.readText()
    }
    private fun invalid(json: String): List<String> = try {
        PersonaLoader.load(json); fail("Expected validation rejection"); emptyList()
    } catch (e: PersonaValidationException) { e.errors }

    @Test fun realFilesAndBundledCatalogAgree() {
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val actual = PersonaLoader.load(source(name.lowercase()))
            val bundled = PersonaCatalog.byName(name)
            assertEquals(actual.name, bundled.name); assertEquals(actual.role, bundled.role)
            assertEquals(actual.assumed, bundled.assumed); assertEquals(actual.notes, bundled.notes)
            assertEquals(actual.pickupProb, bundled.pickupProb); assertEquals(actual.dwell, bundled.dwell)
            assertEquals(actual.resume, bundled.resume); assertEquals(actual.fatigueMultiplier, bundled.fatigueMultiplier)
            assertEquals(actual.focusPeaks, bundled.focusPeaks); assertEquals(actual.importantSenders, bundled.importantSenders)
            assertEquals(actual.selfInitiatedPerHour, bundled.selfInitiatedPerHour, 0.0)
        }
        assertEquals(3, PersonaCatalog.all().size)
        assertTrue(PersonaCatalog.byName("Kabir").selfInitiatedPerHour > PersonaCatalog.byName("Rahul").selfInitiatedPerHour)
    }
    @Test fun aggregatesIndependentErrors() {
        val bad = source().replace("0.62", "1.7").replace("0.7", "0").replace("2.6", "null")
            .replace("1.2,", "0.5,").replace("10,", "24,").replace("1.4", "-1")
        val errors = invalid(bad)
        for (field in listOf("pickupProb", "dwell.social.sigma", "dwell.social.mu", "fatigueMultiplier", "focusPeaks", "selfInitiatedPerHour"))
            assertTrue("Missing error for $field", errors.any { it.startsWith(field) })
    }
    @Test fun rejectsMissingClassForWorkTask() {
        val errors = invalid(source().replace("social_notif|coding", "other|coding"))
        assertTrue(errors.any { it == "pickupProb: missing social_notif|coding" })
    }
    @Test fun rejectsInvalidStructuresAndUnknownFields() {
        for (bad in listOf(source().replace("\"name\":", "\"other\":"), source().replace("\"developer\"", "null"), source().replace("\"fatigueMultiplier\": [", "\"fatigueMultiplier\": [1,"), source().replace("10,", "10.5,")))
            assertTrue(invalid(bad).isNotEmpty())
        assertTrue(invalid(source().replace("\"mu\": 2.6", "\"mu\": 2.6, \"extra\": 1")).any { it.contains("unknown field") })
    }
    @Test fun assumptionsAreSeparateFromSource() {
        val p = PersonaCatalog.byName("Kabir")
        assertFalse(p.pickupProb.containsKey("social_notif|gaming"))
        assertEquals(0.05, p.effectivePickupProb.getValue("social_notif|gaming"), 0.0)
        assertTrue(p.defaults.workBlocks.any { it.taskType == "gaming" })
        assertEquals(setOf("coding", "gaming"), p.defaults.workBlocks.map { it.taskType }.toSet())
    }
    @Test fun defaultsCannotOverrideTruthOrOmitRequiredProbabilities() {
        val d = DefaultPersonas.forName("Rahul")
        val invalid = PersonaDefaults(d.notificationMix, d.workBlocks, d.pickupLatency, assumedPickupProb = mapOf("social_notif|coding" to 0.2))
        try { PersonaLoader.load(source(), invalid); fail() } catch (e: PersonaValidationException) { assertTrue(e.errors.any { it.contains("override source") }) }
    }
    @Test fun returnedTruthCannotBeMutated() {
        val p = PersonaCatalog.byName("Rahul")
        try { (p.pickupProb as MutableMap)["social_notif|coding"] = 0.0; fail() } catch (_: UnsupportedOperationException) { }
        try { (p.focusPeaks as MutableList).clear(); fail() } catch (_: UnsupportedOperationException) { }
        val input = mutableMapOf("phone" to 0L, "laptop" to 0L)
        val config = GeneratorConfig(deviceClockSkewMs = input); input["phone"] = 123
        assertEquals(0L, config.deviceClockSkewMs.getValue("phone"))
    }
    @Test fun configHasFixedEpochAndRejectsNonfiniteInput() {
        assertEquals("2026-01-04T18:30:00Z", java.time.Instant.ofEpochMilli(GeneratorConfig().startEpochMs).toString())
        assertEquals(14, GeneratorConfig().days)
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            try { GeneratorConfig(windowMin = value); fail() } catch (_: IllegalArgumentException) { }
            try { LoggingPolicy.RandomizedHold(value, 0.4); fail() } catch (_: IllegalArgumentException) { }
        }
    }
}

class JsonReaderTest {
    @Test fun handlesJsonTypesAndEscapes() {
        val parsed = JsonReader.parse("{\"s\":\"a\\n\\u0927\\uD83D\\uDE00\",\"a\":[true,false,null,-1.2e2,0]}") as Map<*, *>
        assertEquals("a\nध😀", parsed["s"])
        assertEquals(listOf(true, false, null, -120.0, 0L), parsed["a"])
    }
    @Test fun rejectsMalformedAmbiguousAndNonfiniteJson() {
        val cases = listOf("{\"a\":1,\"a\":2}", "[1,]", "{\"a\":1,}", "01", "1.", "1e", "NaN", "1e999", "true false", "\"\\uD800\"", "\"\\uDC00\"", "\"\n\"", "\"\\x\"", "[", "{", "\u000b1")
        for (input in cases) {
            try { JsonReader.parse(input); fail("Expected JSON rejection") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun boundsDepthAndInputLength() {
        for (input in listOf("[".repeat(66) + "0" + "]".repeat(66), " ".repeat(1_048_577))) {
            try { JsonReader.parse(input); fail() } catch (_: IllegalArgumentException) { }
        }
    }
}
