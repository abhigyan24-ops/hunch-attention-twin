package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.*
import org.junit.Assert.*
import org.junit.Test

class SavingsDuplicateRegressionTest {
    @Test fun drSavingsRequiresMatchingIdentifiedOverlapForEveryConfirmedHold() {
        val rows=List(20){ExplorationRecord("context",if(it%2==0)Arm.SHOW else Arm.SUPPRESS,.5,if(it%2==0)5.0 else 1.0,false,false,5.0,1.0,true)}
        val report=CounterfactualEngine(20).estimate(rows)
        val d=DecisionTestData.pipeline().decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)
            .copy(propensity=.8,eligibleActions=setOf(Action.DIGEST,Action.ALLOW))
        val held=ActuatedDecision(d,ActuationStatus.HELD,.2,3.0,10.0)
        assertEquals(4.0,SavedMinutes.fromReports(listOf(held),report){"context"}.estimatedSavedMin!!.value,1e-12)
        assertNull(SavedMinutes.fromReports(listOf(held),report){"unknown-bucket"}.estimatedSavedMin)
        assertNull(SavedMinutes.fromReports(listOf(held.copy(decision=d.copy(propensity=1.0))),report){"context"}.estimatedSavedMin)
    }
    @Test fun lifecycleRowsCannotCountOneDecisionTwice() {
        val d=DecisionTestData.pipeline().decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)
        val held=ActuatedDecision(d,ActuationStatus.HELD,.2,3.0,10.0)
        try{SavedMinutes.summarize(listOf(held,held.copy(status=ActuationStatus.RELEASED,heldMinutes=20.0)));fail("Must require one final record per decision")}
        catch(e:IllegalArgumentException){assertEquals("duplicate_decision",e.message)}
    }
}
