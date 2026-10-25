package com.dhyan.app.personas

import com.dhyan.app.personas.world.*
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class HistoryGeneratorTest {
    private fun json(value: Any?): String = when(value) {
        null -> "null"
        is String -> "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\""
        is Map<*,*> -> value.entries.joinToString(",","{","}") { json(it.key.toString())+":"+json(it.value) }
        is Iterable<*> -> value.joinToString(",","[","]") { json(it) }
        is Number, is Boolean -> value.toString()
        else -> error("Unsupported JSON value")
    }
    private fun encoded(h: GeneratedHistory)=h.events.joinToString("\n",postfix="\n") { json(it.envelope()) }

    @Test fun fourteenDaysAllPersonasAreDeterministicAndSchemaReady() {
        val dir=File("build/dhyan-fixtures/4a2").apply { mkdirs() }
        for(name in listOf("Rahul","Anika","Kabir")) {
            val p=PersonaCatalog.byName(name); val c=GeneratorConfig(seed=20261025)
            val h=HistoryGenerator.generate(p,c); val first=encoded(h); val second=encoded(HistoryGenerator.generate(p,c))
            assertEquals(first,second)
            assertTrue(h.events.isNotEmpty()); assertEquals(h.events.size,h.events.map { it.id }.toSet().size)
            assertTrue(h.events.all { it.synthetic && it.source=="persona" && it.schemaVersion=="1.0.0" })
            assertTrue(h.events.zipWithNext().all { (a,b)->a.ts<=b.ts })
            for(device in listOf("phone","laptop")) {
                val seq=h.events.filter { it.device==device }.map { it.seq }
                assertEquals((1L..seq.size.toLong()).toList(),seq)
            }
            for(mix in p.defaults.notificationMix) assertTrue(h.events.any { it.type=="notif_posted" && it.payload.values["notifCat"]==mix.name })
            assertEquals(h.truthRecords.size,h.events.count { it.type=="notif_posted" })
            assertTrue(h.truthRecords.any { it.inSession }); assertTrue(h.truthRecords.any { it.trialEligible })
            assertTrue(h.truthRecords.filter { it.inSession }.none { it.trialEligible })
            for(e in h.events) {
                assertEquals(4, java.util.UUID.fromString(e.id).version())
                assertEquals(2, java.util.UUID.fromString(e.id).variant())
                assertFalse(e.payload.values.keys.any { it in setOf("text","title","body","url","rawText") })
                if(e.type=="notif_posted") assertTrue((e.payload.values["senderId"] as String).matches(Regex("[a-f0-9]{64}")))
            }
            val digest=MessageDigest.getInstance("SHA-256").digest(first.toByteArray()).joinToString("") { "%02x".format(it) }
            println("4A2 $name: events=${h.events.size}; notifications=${h.truthRecords.size}; sha256=$digest; deterministic=true")
            File(dir,"${name.lowercase()}.events.jsonl").writeText(first)
        }
    }

    @Test fun differentSeedsChangeHistory() {
        val p=PersonaCatalog.byName("Rahul")
        assertNotEquals(encoded(HistoryGenerator.generate(p,GeneratorConfig(seed=1,days=1))),encoded(HistoryGenerator.generate(p,GeneratorConfig(seed=2,days=1))))
    }

    @Test fun trialDrawsStayAlignedWhenPickupChanges() {
        val p=PersonaCatalog.byName("Rahul"); val c=GeneratorConfig(seed=42,days=2)
        val low=PersonaDef(p.name,p.role,p.assumed,p.notes,p.pickupProb.mapValues { 0.0 },p.dwell,p.resume,p.fatigueMultiplier,p.focusPeaks,p.selfInitiatedPerHour,p.importantSenders,p.defaults)
        val high=PersonaDef(p.name,p.role,p.assumed,p.notes,p.pickupProb.mapValues { 1.0 },p.dwell,p.resume,p.fatigueMultiplier,p.focusPeaks,p.selfInitiatedPerHour,p.importantSenders,p.defaults)
        val l=HistoryGenerator.generate(low,c).truthRecords; val h=HistoryGenerator.generate(high,c).truthRecords
        assertEquals(l.map { it.ts },h.map { it.ts })
        var changed=0
        for((a,b) in l.zip(h)) {
            val x=a.trial!!; val y=b.trial!!
            assertEquals(x.uSelf,y.uSelf,0.0); assertEquals(x.selfDelayMin,y.selfDelayMin,0.0)
            assertEquals(x.zDself,y.zDself,0.0); assertEquals(x.zRself,y.zRself,0.0)
            assertEquals(x.uLatency,y.uLatency,0.0); assertEquals(x.destNotif,y.destNotif,0.0)
            assertFalse(x.notifEventExists)
            if(x.owner!=y.owner) changed++
        }
        assertTrue(changed>0)
    }

    @Test fun plannerUsesCalendarAndExactBands() {
        val p=PersonaCatalog.byName("Rahul"); val planner=DayPlanner(p,GeneratorConfig())
        assertEquals(2,planner.plan(0).size); assertTrue(planner.plan(5).isEmpty())
        assertEquals(listOf("night","morning","afternoon","evening","late"),listOf(0,6,12,17,22).map { DayPlanner.hourBand(it) })
        assertEquals(0,DayPlanner.fatigueBand(0.0,0.0,0.0))
        assertEquals(1,DayPlanner.fatigueBand(4.0,0.0,0.0))
        assertEquals(2,DayPlanner.fatigueBand(8.0,0.0,0.0))
        assertEquals(0.84,planner.lambdaPerHour(10*60.0),1e-12)
        // Crossing 10:00 integrates 1 minute at 1.4/h then the remainder at .84/h.
        assertEquals(1.0+(.1-1.4/60)/(.84/60),planner.selfDelayMin(599.0,.1),1e-9)
    }

    @Test fun noTwoRealizedPhoneEpisodesOverlap() {
        val h=HistoryGenerator.generate(PersonaCatalog.byName("Kabir"),GeneratorConfig())
        var on=false
        for(e in h.events.filter { it.device=="phone" }) when(e.type) {
            "screen_on" -> { assertFalse(on); on=true }
            "screen_off" -> on=false
        }
        assertFalse(on)
    }

    @Test fun skewPreservesReferenceTimestampsAndPerDeviceSequences() {
        val p=PersonaCatalog.byName("Anika")
        val h=HistoryGenerator.generate(p,GeneratorConfig(days=1,deviceClockSkewMs=mapOf("phone" to 300000L,"laptop" to -120000L)))
        assertTrue(h.events.all { it.ts-it.ingestedTs==if(it.device=="phone") 300000L else -120000L })
        assertTrue(h.events.zipWithNext().all { (a,b)->a.ts<=b.ts })
    }

    /** 4A-3: MILD_MISSPEC and RandomizedHold are now supported. */
    @Test fun mildMisspecAndRandomizedHoldAreSupported() {
        val p=PersonaCatalog.byName("Rahul")
        val h1=HistoryGenerator.generate(p,GeneratorConfig(seed=1L,days=2,world=World.MILD_MISSPEC))
        assertTrue(h1.truthRecords.all { it.world=="MILD_MISSPEC" })
        val h2=HistoryGenerator.generate(p,GeneratorConfig(seed=1L,days=2,loggingPolicy=LoggingPolicy.RandomizedHold(.2,.4)))
        assertTrue(h2.events.isNotEmpty())
    }

    /** 4A-3: All eligible records have non-null realizedArm and propensity. */
    @Test fun eligibleRecordsHaveArmAndPropensity() {
        val h=HistoryGenerator.generate(PersonaCatalog.byName("Rahul"),GeneratorConfig(seed=2L,days=7))
        for(r in h.truthRecords) {
            if(r.trialEligible) {
                assertNotNull(r.realizedArm); assertNotNull(r.propensity)
                assertTrue(r.propensity!! in 0.0..1.0)
            }
        }
    }

    /** 4A-3: Truth records always have finite lShowMin and lSuppressMin. */
    @Test fun costsAlwaysFiniteForEligibleTrials() {
        val h=HistoryGenerator.generate(PersonaCatalog.byName("Anika"),GeneratorConfig(seed=3L,days=7))
        for(r in h.truthRecords.filter { it.trialEligible && it.trial!=null }) {
            val t=r.trial!!
            assertTrue(t.lShowMin.isFinite()); assertTrue(t.lSuppressMin.isFinite())
            assertTrue(t.lShowMin>=0.0); assertTrue(t.lSuppressMin>=0.0)
        }
    }
}
