package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.params.*
import kotlinx.coroutines.runBlocking

internal object DecisionTestData {
    const val NOW=1767596400000L
    val facts=NotificationFacts("synthetic-key-903",NOW-1000,"synthetic.app","social","social","synthetic-sender-804",false,.2,false,false,0,false,false)
    val snapshot=ContextSnapshot(NOW,FocusState.DEEP_FOCUS,TaskType.CODING,TaskSource.INFERRED,.8,"morning",40.0,"med",0,LaptopState.FLOWING,PhoneState.SCREEN_OFF,false,null)
    val settings=PolicySettings(mode=PolicyMode.LIVE)
    val prediction=LossPrediction.Ready(20.0,1.0,2.0,40.0,.6,.8,1,"simulated")
    fun pipeline(i:Double=.3,p:LossPrediction=prediction)=DecisionPipeline(LossPredictor{_,_,_,_->p},ImportanceProvider{i})
    fun store(loaded:Boolean=true):InMemoryParamStore {
        val persistence=object:ParamPersistence {
            override suspend fun loadAll():List<ParamRecord> = emptyList()
            override suspend fun upsert(records:List<ParamRecord>){}
        }
        return InMemoryParamStore(persistence).also{if(loaded)runBlocking{it.load()}}
    }
}
