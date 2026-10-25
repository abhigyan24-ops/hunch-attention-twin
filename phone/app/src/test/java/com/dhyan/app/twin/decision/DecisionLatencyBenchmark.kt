package com.dhyan.app.twin.decision

import org.junit.Assert.*
import org.junit.Test

class DecisionLatencyBenchmark {
    @Test fun devJvmRealEngineWarmMedianAndP95() {
        val p=DecisionPipeline(TwinEngineLossPredictor(DecisionTestData.store()),ImportanceProvider{.2})
        repeat(500){p.decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW)}
        val values=DoubleArray(2000){val start=System.nanoTime();val d=p.decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW);assertFalse(d.failedOpen);(System.nanoTime()-start)/1e6}.sorted()
        println("5E dev JVM real-twin decide latency: median=${values[1000]} ms; p95=${values[1900]} ms; n=2000; NOT a Reno 14 result")
        assertTrue(values[1000]<10);assertTrue(values[1900]<10)
    }
}
