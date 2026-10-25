package com.dhyan.app.twin.decision

import org.junit.Assert.*
import org.junit.Test

class PipelineLatencySmokeTest {
    @Test fun realTwinDevJvmWarmLatency() {
        val p=DecisionPipeline(TwinEngineLossPredictor(DecisionTestData.store()),ImportanceProvider{.2})
        repeat(200){p.decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)}
        val times=DoubleArray(300){val start=System.nanoTime();val d=p.decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW);assertFalse(d.failedOpen);(System.nanoTime()-start)/1e6}.sorted()
        println("5C dev JVM real-twin latency: median=${times[150]} ms; p95=${times[285]} ms; n=300; NOT a Reno 14 result")
        assertTrue(times[150]<10);assertTrue(times[285]<10)
    }
    @Test fun explainerUsesControlledFactorsAndStableRanking() {
        val e=Explainer();val p=DecisionTestData.prediction;val s=DecisionTestData.snapshot
        val factors=e.factors(p,s,.2,3.0,120.0)
        assertEquals(listOf("loss","pickup"),factors.map{it.kind})
        assertTrue(e.held(factors).contains("80% simulation interval"))
        assertEquals(e.factors(p,s,.2,3.0,120.0),factors)
        for(reason in listOf("mode_off","kill_switch","high_importance","low_evidence","invalid_input","explored"))assertTrue(e.allow(reason).startsWith("Allowed:"))
        val small=e.factors(p.copy(eShowMin=1.1,eSuppMin=1.0),s,.1,100.0,120.0)
        assertEquals("importance",small.first().kind)
    }
}
