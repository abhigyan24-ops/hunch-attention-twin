package com.dhyan.app.twin.decision

import com.dhyan.app.personas.*
import com.dhyan.app.personas.world.HistoryGenerator
import com.dhyan.app.twin.episodes.*
import org.junit.Assert.*
import org.junit.Test

class EndToEndSheetTest {
    @Test fun realEngineColdVersusPersonaReplayLearning() {
        val histories=listOf("Rahul","Kabir").associateWith{HistoryGenerator.generate(PersonaCatalog.byName(it),GeneratorConfig(seed=42))}
        val last=histories.values.maxOf{h->h.events.maxOf{it.ts}}
        val now=java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.of("UTC")).toLocalDate().plusDays(1).atTime(10,0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val facts=DecisionTestData.facts.copy(postedTsMs=now-1000)
        val snapshot=DecisionTestData.snapshot.copy(ts=now)
        val coldStore=DecisionTestData.store()
        val cold=DecisionPipeline(TwinEngineLossPredictor(coldStore),ImportanceProvider{.2})
        val baseline=cold.decide(facts,snapshot,DecisionTestData.settings,now)
        println("E2E: persona | action | Eshow | Esupp | evidence strength | modelVersion")
        println("cold | ${baseline.action} | ${baseline.predictedLossShowMin} | ${baseline.predictedLossSuppressMin} | ${baseline.evidenceStrength} | ${baseline.modelVersion}")
        for(name in listOf("Rahul","Kabir")) {
            val store=DecisionTestData.store();val builder=EpisodeBuilder(onEpisodeClosed=EpisodeCloser(store));val adapter=EventStreamAdapter(builder)
            val history=histories.getValue(name)
            for(e in history.events)adapter.processEvent(e.type,e.ts,e.payload.values)
            builder.checkTimeout(history.events.maxOf{it.ts}+31*60000)
            val pipeline=DecisionPipeline(TwinEngineLossPredictor(store),ImportanceProvider{.2})
            val d=pipeline.decide(facts,snapshot,DecisionTestData.settings,now)
            assertFalse(d.failedOpen);assertTrue(d.evidenceStrength!!>baseline.evidenceStrength!!)
            // Positive ordering is checked for this named Rahul fixture, not a universal model law.
            if(name=="Rahul")assertTrue(d.predictedLossSuppressMin!!<=d.predictedLossShowMin!!)
            assertEquals(Action.ALLOW,pipeline.decide(facts.copy(isVip=true),snapshot,DecisionTestData.settings,now).action)
            println("$name | ${d.action} | ${d.predictedLossShowMin} | ${d.predictedLossSuppressMin} | ${d.evidenceStrength} | ${d.modelVersion}")
        }
    }
}
