package com.dhyan.app.twin.decision

import org.junit.Assert.*
import org.junit.Test

class UrgentSuiteTest {
    @Test fun namedSyntheticUrgentSuiteNeverHoldsAcrossGrid() {
        val f=DecisionTestData.facts
        val cases=(0 until 6).flatMap{variant->listOf(f.copy(notifCat="call"),f.copy(notifCat="alarm"),f.copy(notifCat="navigation"),
            f.copy(isOtpLike=true),f.copy(hasEmergencyKeyword=true),f.copy(isVip=true),f.copy(pkg="synthetic.allow")).mapIndexed{i,v->v.copy(keyHash="urgent-$variant-$i")}}
        val p=DecisionPipeline(LossPredictor{_,_,_,_->DecisionTestData.prediction.copy(eShowMin=1000000.0)},ImportanceProvider{.1})
        var configs=0;var count=0;var holds=0
        for(mode in PolicyMode.entries)for(kill in listOf(false,true))for(strict in listOf(false,true))for(explore in listOf(false,true))for(kappa in listOf(.01,3.0,1e6))for(seed in listOf(1L,42L,2026L)) {
            configs++;val settings=PolicySettings(mode=mode,killSwitch=kill,kappaDPerHour=kappa,strictSession=strict,explorationEnabled=explore,seed=seed,allowListPackages=setOf("synthetic.allow"))
            for(facts in cases){count++;if(p.decide(facts,DecisionTestData.snapshot,settings,DecisionTestData.NOW).action!=Action.ALLOW)holds++}
        }
        println("urgent suite: ${cases.size} cases x $configs configs = $count decisions, holds = $holds; measured synthetic suite only")
        assertEquals(0,holds)
    }
}
