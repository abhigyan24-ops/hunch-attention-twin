package com.dhyan.app.twin.decision

import com.dhyan.app.twin.*
import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.params.InMemoryParamStore

sealed interface LossPrediction {
    data class Ready(val eShowMin:Double,val eSuppMin:Double,val deltaP10:Double,val deltaP90:Double,
        val pPickupMean:Double,val evidenceStrength:Double,val modelVersion:Long,val provenance:String):LossPrediction
    data class NotReady(val reasonCode:String):LossPrediction
}
fun interface LossPredictor {
    /** Explicit time/seed avoids the engine's default ambient clock and mutable clock adapters. */
    fun predict(snapshot:ContextSnapshot,facts:NotificationFacts,nowMs:Long,seed:Long):LossPrediction
}
class TwinEngineLossPredictor(private val store:InMemoryParamStore,private val config:TwinConfig=TwinConfig.default(),
    private val modelConfig:ModelConfig=ModelConfig()):LossPredictor {
    override fun predict(snapshot:ContextSnapshot,facts:NotificationFacts,nowMs:Long,seed:Long):LossPrediction {
        // The real engine performs ContextBucketer mapping and in-memory store resolution.
        val result=TwinEngine(store,config,modelConfig,clock={nowMs}).simulate(snapshot,
            NotificationInput(facts.appCat,facts.notifCat,facts.isVip,facts.urgencyScore,facts.repeatCount5m),seed)
        return when(result) {
            is SimOutcome.NotReady -> LossPrediction.NotReady(result.reason.name)
            is SimOutcome.Ready -> result.result.let { LossPrediction.Ready(it.show.mean,it.suppress.mean,
                it.deltaL80SimIntervalLow,it.deltaL80SimIntervalHigh,it.pPickupSample.average(),it.evidenceStrength,
                it.modelVersion,if(it.evidenceStrength<PolicyDefaults.CONF_MIN)"low evidence" else "simulated") }
        }
    }
}
