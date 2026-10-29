package com.dhyan.app.ui.viewmodel

import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.DecisionPipeline
import com.dhyan.app.twin.decision.ImportanceModel
import com.dhyan.app.twin.decision.ModelImportanceProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.TwinEngineLossPredictor
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DecisionPipelineFocusSensitivityTest {

    private class EmptyPersistence : ParamPersistence {
        override suspend fun loadAll(): List<ParamRecord> = emptyList()
        override suspend fun upsert(records: List<ParamRecord>) {}
    }

    @Test
    fun testDecisionPipelineSensitivityToFocusState() = runBlocking {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        store.load()

        val lossPredictor = TwinEngineLossPredictor(store)
        val importanceProvider = ModelImportanceProvider(ImportanceModel())
        val pipeline = DecisionPipeline(lossPredictor, importanceProvider)
        val now = 100_000L

        fun snap(focus: FocusState): ContextSnapshot = ContextSnapshot(
            ts = now,
            focusState = focus,
            taskType = TaskType.CODING,
            taskSource = TaskSource.DECLARED,
            confidence = 0.85,
            hourBand = "afternoon",
            minutesInState = 45.0,
            fatigueBand = "low",
            recentInterrupts30m = 0,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = true,
            declaredSession = null
        )

        val facts = NotificationFacts(
            keyHash = "test_key",
            postedTsMs = now,
            pkg = "com.whatsapp",
            appCat = "communication",
            notifCat = "msg",
            senderHash = "hash123",
            isVip = false,
            urgencyScore = 0.2,
            hasReplyAction = true,
            isMessagingStyle = true,
            repeatCount5m = 0,
            isOtpLike = false,
            hasEmergencyKeyword = false
        )

        val settings = PolicySettings(mode = PolicyMode.LIVE, seed = 42L, kappaDPerHour = 1.0)

        val decDeep = pipeline.decide(facts, snap(FocusState.DEEP_FOCUS), settings, now)
        val decBreak = pipeline.decide(facts, snap(FocusState.BREAK), settings, now)
        val decWorking = pipeline.decide(facts, snap(FocusState.WORKING), settings, now)

        println("=== DECISION PIPELINE FOCUS STATE TEST ===")
        println("DEEP_FOCUS: action=${decDeep.action}, holdMin=${decDeep.holdMinutes}, reason=${decDeep.reasonCode}, explanation=${decDeep.explanation}")
        println("BREAK:      action=${decBreak.action}, holdMin=${decBreak.holdMinutes}, reason=${decBreak.reasonCode}, explanation=${decBreak.explanation}")
        println("WORKING:    action=${decWorking.action}, holdMin=${decWorking.holdMinutes}, reason=${decWorking.reasonCode}, explanation=${decWorking.explanation}")
        println("==========================================")
    }
}
