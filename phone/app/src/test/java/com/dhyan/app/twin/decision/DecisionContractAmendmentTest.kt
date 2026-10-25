package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DecisionContractAmendmentTest {
    private val snapshot=ContextSnapshot(1,FocusState.WORKING,TaskType.CODING,TaskSource.INFERRED,.8,"morning",1.0,"low",0,LaptopState.OFFLINE,PhoneState.SCREEN_OFF,false,null)
    private val facts=NotificationFacts("hash",1,"synthetic.app","social","social",null,false,.1,false,false,0,false,false)
    @Test fun oldPositionalSettingsConstructionAndCustomDigestInterval() {
        val old=PolicySettings(PolicyMode.DRY_RUN,false,3.0,false,.05,false,emptySet(),42L)
        assertEquals(PolicyDefaults.DIGEST_INTERVAL_MIN,old.digestIntervalMin)
        val custom=PolicySettings(digestIntervalMin=60)
        val zone=ZoneId.of("Asia/Kolkata")
        val now=Instant.parse("2026-01-05T04:30:00Z").toEpochMilli() // 10:00 local
        val costTime=DigestSchedule.nextDigestTsMs(now,custom.digestIntervalMin,zone)
        val releaseTime=DigestSchedule.nextDigestTsMs(now,custom.digestIntervalMin,zone)
        assertEquals(60,custom.digestIntervalMin)
        assertEquals(Instant.parse("2026-01-05T05:30:00Z").toEpochMilli(),costTime)
        assertNotEquals(DigestSchedule.nextDigestTsMs(now,old.digestIntervalMin,zone),costTime)
        assertEquals(costTime,releaseTime)
    }
    @Test fun oldDecisionConstructionDefaultsToUnspecified() {
        val decision=Decision(Action.ALLOW,"Allowed.",emptyList(),null,1.0,setOf(Action.ALLOW),false,null,false,
            null,null,null,null,null,null,"low evidence",0,"test",2,"hash",1)
        assertEquals("unspecified",decision.reasonCode)
        assertEquals("unspecified",DecisionRecordFactory.from(facts,decision,snapshot,PolicySettings(),.2).reasonCode)
    }
    @Test fun factoryCopiesControlledReasonAndKeepsSafetyReasonSeparate() {
        val base=AlwaysAllowDecisionProvider().decide(facts,snapshot,PolicySettings(),2)
        assertEquals("no_provider",base.reasonCode);assertNull(base.safetyReason)
        for(reason in listOf("mode_off","kill_switch","invalid_input","never_hold","high_importance","not_ready","low_evidence","cost_selected","explored","no_provider")) {
            val decision=base.copy(reasonCode=reason,safetyReason=if(reason=="never_hold")"vip" else null)
            val record=DecisionRecordFactory.from(facts,decision,snapshot,PolicySettings(),.2)
            assertEquals(reason,record.reasonCode);assertEquals(decision.safetyReason,record.safetyReason)
            assertEquals("unspecified",record.copy(reasonCode="unspecified").reasonCode)
        }
    }
}
