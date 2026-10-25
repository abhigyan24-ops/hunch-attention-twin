package com.dhyan.app

import android.app.Application
import com.dhyan.app.actuator.ActuationCoordinator
import com.dhyan.app.actuator.AndroidNotificationGateway
import com.dhyan.app.actuator.RoomHeldStore
import com.dhyan.app.actuator.release.WorkManagerReleaseJobScheduler
import com.dhyan.app.collectors.NotifCollector
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.data.RoomDecisionLogStore
import com.dhyan.app.data.RoomParamPersistence
import com.dhyan.app.twin.decision.DecisionPipeline
import com.dhyan.app.twin.decision.ImportanceModel
import com.dhyan.app.twin.decision.ModelImportanceProvider
import com.dhyan.app.twin.decision.TwinEngineLossPredictor
import com.dhyan.app.twin.params.InMemoryParamStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DhyanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DhyanApplication)
        }

        initializePipeline()
    }

    private fun initializePipeline() {
        val db = DhyanDatabase.getInstance(this)
        val persistence = RoomParamPersistence(db.paramDao())
        val paramStore = InMemoryParamStore(persistence)
        val importanceModel = ImportanceModel()
        val lossPredictor = TwinEngineLossPredictor(paramStore)
        val importanceProvider = ModelImportanceProvider(importanceModel)
        val decisionPipeline = DecisionPipeline(lossPredictor, importanceProvider)

        val heldStore = RoomHeldStore(db.heldNotifDao())
        val gateway = AndroidNotificationGateway(this)
        val releaseScheduler = WorkManagerReleaseJobScheduler(this)
        val coordinator = ActuationCoordinator(heldStore, gateway, decisionPipeline, releaseScheduler)
        val decisionLogStore = RoomDecisionLogStore(db.decisionDao())

        NotifCollector.decisionProvider = decisionPipeline
        NotifCollector.decisionLogStore = decisionLogStore
        NotifCollector.coordinator = coordinator
        NotifCollector.importanceProvider = importanceProvider

        CoroutineScope(Dispatchers.IO).launch {
            try {
                paramStore.load()
                val vips = db.vipDao().allHashes().toSet()
                NotifCollector.vipHashSet = vips
            } catch (_: Exception) {
            }
        }
    }
}
