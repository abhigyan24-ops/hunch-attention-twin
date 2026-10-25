package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CounterfactualTest {
    private fun row(arm:Arm=Arm.SHOW,p:Double=.5,y:Double=10.0)=ExplorationRecord("test",arm,p,y,false,false,8.0,4.0,true)
    @Test fun handArithmeticForNormalizedHtAndDr() {
        val rows=listOf(row(p=.25),row(Arm.SUPPRESS,.75,4.0))
        // weights4/4: Hajek10-4=6; HT(40-16)/2=12; DR[(8+4*2-4)+(8-4)]/2=8.
        assertEquals(6.0,IpwEstimator.hajek(rows),0.0);assertEquals(12.0,IpwEstimator.horvitzThompson(rows),0.0)
        assertEquals(8.0,DoublyRobustEstimator.delta(rows),0.0)
        assertEquals(6.0,DoublyRobustEstimator.delta(rows,2.0),0.0)
    }
    @Test fun filtersThresholdAndLabels() {
        val base=List(20){row(if(it%2==0)Arm.SHOW else Arm.SUPPRESS,y=if(it%2==0)10.0 else 4.0)}
        val engine=CounterfactualEngine(100,42)
        assertEquals(EstimateStatus.MODEL_BASED_ONLY,engine.estimate(base.take(19)).single().status)
        val invalid=listOf(row().copy(censored=true),row().copy(actuationFailed=true),row().copy(eligible=false),row(p=0.0),row(p=1.0),row(y=Double.NaN))
        val result=engine.estimate(base+invalid).single()
        assertEquals(EstimateStatus.ESTIMATED,result.status);assertEquals(20,result.nUsed)
        assertEquals(FilterCounts(1,1,1,2,1),result.filters)
        assertEquals("simulated",result.modelMeanDelta!!.label);assertEquals("estimated",result.drDelta!!.label)
        assertEquals("observed",result.observedMean!!.label);assertEquals("80% bootstrap interval",result.intervals.getValue("dr").label)
        assertEquals(EstimateStatus.MODEL_BASED_ONLY,engine.estimate(List(20){row()}).single().status)
    }
    @Test fun extremeWeightsReduceEssAndTruncationReportsChange() {
        val rows=List(20){row(if(it%2==0)Arm.SHOW else Arm.SUPPRESS,p=if(it==0).001 else .5,y=if(it==0)30.0 else 5.0)}
        assertTrue(Diagnostics.overlap(rows).essShow<Diagnostics.overlap(rows,5.0).essShow)
        assertNotEquals(IpwEstimator.hajek(rows),IpwEstimator.hajek(rows,5.0))
        val report=CounterfactualEngine(100).estimate(rows).single();assertEquals(listOf(null,20.0,10.0,5.0),report.sensitivity.map{it.cap})
        assertTrue(report.overlap!!.top5PercentWeightShare>.8)
    }
    private fun synthetic(seed:Long,n:Int,wrongPropensity:Boolean,wrongModel:Boolean,constantP:Boolean=false):Pair<List<ExplorationRecord>,Double> {
        val rng=PolicyRng(seed);var truth=0.0
        val rows=List(n){
            val x=if(rng.nextDouble()<.5)0.0 else 1.0
            val p=if(constantP).2 else if(x==0.0).2 else .8
            val muShow=5+4*x;val muSupp=1+x;truth+=muShow-muSupp
            val arm=if(rng.nextDouble()<p)Arm.SHOW else Arm.SUPPRESS
            val noise=(rng.nextDouble()-.5)*2
            ExplorationRecord("synthetic",arm,if(wrongPropensity).5 else p,(if(arm==Arm.SHOW)muShow else muSupp)+noise,
                false,false,muShow*(if(wrongModel)1.3 else 1.0),muSupp*(if(wrongModel)1.3 else 1.0),true)
        }
        return rows to truth/n
    }
    @Test fun repeatedFixedSeedUnbiasednessAndDoubleRobustness() {
        fun assess(wrongP:Boolean,wrongM:Boolean,constantP:Boolean=false):Pair<Double,Double>{
            val ipwErrors=mutableListOf<Double>();val drErrors=mutableListOf<Double>()
            for(seed in 1L..30L){val (r,truth)=synthetic(seed,3000,wrongP,wrongM,constantP);ipwErrors.add(IpwEstimator.hajek(r)-truth);drErrors.add(DoublyRobustEstimator.delta(r)-truth)}
            fun check(values:List<Double>){val mean=values.average();val se=sqrt(values.sumOf{(it-mean).pow(2)}/(values.size-1)/values.size);assertEquals(0.0,mean,4*se+.005)}
            if(!wrongP)check(ipwErrors)
            if(!wrongP||!wrongM)check(drErrors)
            println("DR validation wrongP=$wrongP wrongModel=$wrongM constantP=$constantP: IPWbias=${ipwErrors.average()}, DRbias=${drErrors.average()}")
            return ipwErrors.average() to drErrors.average()
        }
        assess(false,false,true);assess(false,true)
        val correctModel=assess(true,false);assertTrue(abs(correctModel.first)>.5)
        val bothWrong=assess(true,true);assertTrue(abs(bothWrong.second)>.1)
    }
    @Test fun bootstrapDeterminismAndWideCoverageSanity() {
        val (rows,_)=synthetic(42,200,false,false,true)
        assertEquals(Bootstrap.run(rows,7,200),Bootstrap.run(rows,7,200))
        var covered=0
        for(seed in 1L..20L){val(r,truth)=synthetic(seed,200,false,false,true);val interval=Bootstrap.run(r,seed+100,200).intervals.getValue("dr");if(truth in interval.p10..interval.p90)covered++}
        println("80% bootstrap interval coverage sanity: $covered/20 fixed-seed replications")
        assertTrue(covered in 10..20)
    }
    @Test fun regretAndSavingsKeepModelDelayAndDrSeparate() {
        val cost=CostFunction.compute(10.0,1.0,.2,3.0,false,30.0,120.0)
        assertEquals(7.96,Regret.modelRegretMin(RegretRecord(Action.ALLOW,cost,true)),1e-12)
        assertEquals(7.96,Regret.explorationCostMin(listOf(RegretRecord(Action.ALLOW,cost,true))),1e-12)
        val decision=DecisionTestData.pipeline().decide(DecisionTestData.facts,DecisionTestData.snapshot,DecisionTestData.settings,DecisionTestData.NOW).copy(action=Action.DIGEST,deltaLMean=4.0)
        val records=listOf(ActuatedDecision(decision,ActuationStatus.HELD,.5,3.0,60.0),ActuatedDecision(decision,ActuationStatus.ACTUATION_UNKNOWN,.5,3.0,60.0))
        val savings=SavedMinutes.summarize(records,8.0)
        assertEquals(4.0,savings.modelSavedMin.value,0.0);assertEquals(.75,savings.delayCostMin.value,0.0);assertEquals(8.0,savings.estimatedSavedMin!!.value,0.0)
    }
}
