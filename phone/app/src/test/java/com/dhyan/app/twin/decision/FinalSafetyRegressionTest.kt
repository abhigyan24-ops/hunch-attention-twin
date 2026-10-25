package com.dhyan.app.twin.decision

import org.junit.Assert.*
import org.junit.Test

class FinalSafetyRegressionTest {
    @Test fun optionalPickupAndMissingExplanationMetadataAreSkipped() {
        val p=DecisionTestData.prediction.copy(pPickupMean=null)
        val factors=Explainer().factors(p,DecisionTestData.snapshot.copy(fatigueBand="",minutesInState=Double.NaN),.2,3.0,120.0)
        assertFalse(factors.any{it.kind in setOf("pickup","fatigue","laptop")})
        val d=DecisionTestData.pipeline(.2,p).decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)
        assertFalse(d.failedOpen);assertFalse(d.factors.any{it.kind=="pickup"})
    }
    @Test fun unknownCategoriesFailOpenWithoutBeingCalledNeverHold() {
        val p=DecisionTestData.pipeline()
        val d=p.decide(DecisionTestData.facts.copy(notifCat="unrecognized-synthetic-category"),DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)
        assertEquals(Action.ALLOW,d.action);assertTrue(d.failedOpen);assertNull(d.safetyReason)
        assertEquals("invalid_input",d.reasonCode)
        assertEquals(1L,p.diagnostics.snapshot().getValue("unknown_notif_cat"))
    }
    @Test fun explorationHelperIndependentlyRespectsCriticalFlags() {
        val settings=PolicySettings(mode=PolicyMode.LIVE,explorationEnabled=true,epsilon=1.0)
        for(f in listOf(DecisionTestData.facts.copy(isOtpLike=true),DecisionTestData.facts.copy(hasEmergencyKeyword=true),DecisionTestData.facts.copy(notifCat="call"))) {
            val c=ExplorationPolicy().choose(Action.DIGEST,f,settings,.2,20.0)
            assertFalse(c.explored);assertEquals(1.0,c.propensity,0.0)
        }
    }
}
