package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import java.time.Instant
import java.time.ZoneId
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DecisionContractTest {
    private val snapshot=ContextSnapshot(1,FocusState.WORKING,TaskType.CODING,TaskSource.INFERRED,.8,"morning",1.0,"low",0,LaptopState.OFFLINE,PhoneState.SCREEN_OFF,false,null)
    private val facts=NotificationFacts("hash",1,"synthetic.app","social","social",null,false,.1,false,false,0,false,false)
    private fun time(iso:String)=Instant.parse(iso).toEpochMilli()
    @Test fun scheduleBoundariesAndMidnight() {
        val utc=ZoneId.of("UTC")
        for(interval in listOf(60,120)) {
            assertEquals(time("2026-01-05T02:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-01-05T01:59:59.999Z"),interval,utc))
            val next=if(interval==60) "2026-01-05T03:00:00Z" else "2026-01-05T04:00:00Z"
            assertEquals(time(next),DigestSchedule.nextDigestTsMs(time("2026-01-05T02:00:00Z"),interval,utc))
            assertEquals(time("2026-01-06T00:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-01-05T23:59:59Z"),interval,utc))
        }
        assertEquals(time("2026-01-06T00:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-01-05T00:00:00Z"),1440,utc))
        for(i in listOf(0,-1,1441))try{DigestSchedule.nextDigestTsMs(1,i,utc);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun handlesBothDstDirections() {
        val zone=ZoneId.of("America/New_York")
        assertEquals(time("2026-03-08T07:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-03-08T06:59:59Z"),120,zone))
        assertEquals(time("2026-11-01T06:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-11-01T05:30:00Z"),60,zone))
        assertEquals(time("2026-11-01T07:00:00Z"),DigestSchedule.nextDigestTsMs(time("2026-11-01T06:30:00Z"),120,zone))
    }
    @Test fun fallbackAlwaysAllowsAndRejectsActuation() {
        val p=AlwaysAllowDecisionProvider()
        for(mode in PolicyMode.entries) for(now in listOf(-1L,0L,42L,Long.MAX_VALUE)) {
            val d=p.decide(facts,snapshot,PolicySettings(mode=mode,kappaDPerHour=Double.NaN),now)
            assertEquals(Action.ALLOW,d.action);assertTrue(d.failedOpen);assertNull(d.holdMinutes)
            assertEquals(Recheck.Abort("no_provider"),p.recheck(facts,d,PolicySettings(mode=mode),now))
        }
    }
    @Test fun pShowAndRecordNeverIncludeExplanationText() {
        val base=AlwaysAllowDecisionProvider().decide(facts,snapshot,PolicySettings(),2)
        for(a in Action.entries) {
            val d=base.copy(action=a,propensity=.2,explanation="synthetic-canary",factors=listOf(Factor("loss","synthetic-canary",2.0)))
            assertEquals(if(a==Action.ALLOW).2 else .8,d.pShow,0.0)
            val record=DecisionRecordFactory.from(facts,d,snapshot,PolicySettings(),.2)
            assertFalse(record.toString().contains("synthetic-canary"));assertNull(record.actuationStatus)
        }
        try{DecisionRecordFactory.from(facts.copy(keyHash="different"),base,snapshot,PolicySettings(),.2);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun settingsDefensivelyCopyAllowList() {
        val values=mutableSetOf("synthetic.one");val s=PolicySettings(allowListPackages=values);values.clear()
        assertEquals(setOf("synthetic.one"),s.allowListPackages)
        try{(s.allowListPackages as MutableSet).clear();fail()}catch(_:UnsupportedOperationException){}
        assertEquals(PolicyMode.OFF,PolicySettings().mode)
    }
    @Test fun defaultsExportForNodeComparison() {
        val output=File("build/decision-contract").apply{mkdirs()}
        File(output,"defaults.json").writeText("""{"kappaD":${PolicyDefaults.KAPPA_D_PER_HOUR},"digest":${PolicyDefaults.DIGEST_INTERVAL_MIN},"version":$DECISION_CONTRACT_VERSION}""")
        assertEquals(3.0,PolicyDefaults.KAPPA_D_PER_HOUR,0.0)
        assertEquals(30000L,PolicyDefaults.MAX_DECISION_AGE_MS)
    }
}
