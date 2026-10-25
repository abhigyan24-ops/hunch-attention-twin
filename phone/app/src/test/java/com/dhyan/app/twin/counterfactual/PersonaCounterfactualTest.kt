package com.dhyan.app.twin.counterfactual

import com.dhyan.app.personas.*
import com.dhyan.app.personas.world.*
import org.junit.Assert.*
import org.junit.Test

class PersonaCounterfactualTest {
    @Test fun estimatorsCompareAgainstActualFiniteFixtureCounterfactuals() {
        var covered=0;var estimated=0
        println("persona | n | history true delta | IPW | DR | 80% bootstrap interval | TrueEffect constant-hazard reference")
        for(name in listOf("Rahul","Kabir")) {
            val persona=PersonaCatalog.byName(name)
            val truths=listOf(1L,42L,2026L).flatMap{seed->HistoryGenerator.generate(persona,GeneratorConfig(seed=seed,loggingPolicy=LoggingPolicy.RandomizedHold(.2,.4))).truthRecords}
                .filter{it.trialEligible&&!it.inSession&&it.eligibleForExploration&&it.trial!=null&&it.realizedArm!=null&&it.propensity!=null}
            val records=truths.map{t->val arm=if(t.realizedArm==RealizedArm.SHOW)Arm.SHOW else Arm.SUPPRESS
                ExplorationRecord(name,arm,if(arm==Arm.SHOW)t.propensity!! else 1-t.propensity!!,
                    if(arm==Arm.SHOW)t.trial!!.lShowMin else t.trial!!.lSuppressMin,false,false,25.0,18.0,true)}
            val report=CounterfactualEngine(seed=42).estimate(records).single()
            val truth=truths.map{it.trial!!.deltaLMin}.average()
            val contexts=truths.groupBy{it.context}
            val reference=contexts.entries.sumOf{(context,rows)->TrueEffect.estimate(persona,context,GeneratorConfig(),nDraws=2000,seed=42).eDeltaL*rows.size}/truths.size
            if(report.status==EstimateStatus.ESTIMATED){estimated++;val interval=report.intervals.getValue("dr");val width=interval.p90-interval.p10
                if(truth in (interval.p10-width)..(interval.p90+width))covered++
                println("$name | ${report.nUsed} | $truth | ${report.ipwDelta!!.value} | ${report.drDelta!!.value} | ${interval.p10}..${interval.p90} | $reference")
            }else println("$name | ${report.nUsed} | $truth | MODEL_BASED_ONLY | reference=$reference")
            assertTrue(truth.isFinite());assertTrue(reference.isFinite())
        }
        assertTrue(estimated>0);assertTrue(covered*2>=estimated)
    }
}
