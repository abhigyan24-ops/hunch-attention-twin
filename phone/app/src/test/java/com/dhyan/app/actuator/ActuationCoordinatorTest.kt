package com.dhyan.app.actuator

import com.dhyan.app.actuator.release.NoOpReleaseJobScheduler
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.decision.Decision
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.Recheck
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActuationCoordinatorTest {

    private lateinit var heldStore: InMemoryHeldStore
    private lateinit var gateway: FakeNotificationGateway
    private lateinit var scheduler: NoOpReleaseJobScheduler
    private lateinit var clock: FakeClock
    private lateinit var registry: ReleasedKeyRegistry

    private val baseFacts = NotificationFacts(
        keyHash = "test-hash-001",
        postedTsMs = 1_000_000L,
        pkg = "com.example.app",
        appCat = "social",
        notifCat = "post",
        senderHash = null,
        isVip = false,
        urgencyScore = 0.3,
        hasReplyAction = false,
        isMessagingStyle = false,
        repeatCount5m = 0,
        isOtpLike = false,
        hasEmergencyKeyword = false
    )

    private val baseDecision = Decision(
        action = Action.DIGEST,
        explanation = "focus session active",
        factors = emptyList(),
        holdMinutes = 30.0,
        propensity = 0.7,
        eligibleActions = setOf(Action.ALLOW, Action.DIGEST),
        explored = false,
        safetyReason = null,
        failedOpen = false,
        predictedLossShowMin = 24.0,
        predictedLossSuppressMin = 8.0,
        deltaLMean = 16.0,
        deltaLP10 = 10.0,
        deltaLP90 = 22.0,
        evidenceStrength = 0.7,
        provenance = "estimated",
        modelVersion = 1L,
        policyVersion = "v1",
        decidedAtMs = 1_000_100L,
        keyHash = "test-hash-001",
        postedTsMs = 1_000_000L
    )

    private fun makeSnapshot() = ContextSnapshot(
        ts = 1_000_000L,
        focusState = FocusState.DEEP_FOCUS,
        taskType = TaskType.CODING,
        taskSource = TaskSource.INFERRED,
        confidence = 0.75,
        hourBand = "afternoon",
        minutesInState = 30.0,
        fatigueBand = "low",
        recentInterrupts30m = 0,
        laptopState = LaptopState.FLOWING,
        phoneState = PhoneState.SCREEN_OFF,
        secondScreenOverlap = false,
        declaredSession = null
    )

    private fun makeCoordinator(
        gatewayResult: NotificationGateway.Result = NotificationGateway.Result.Ok,
        mode: PolicyMode = PolicyMode.LIVE
    ): Triple<ActuationCoordinator, PolicySettings, ContextSnapshot> {
        gateway = FakeNotificationGateway(cancelResult = gatewayResult, snoozeResult = gatewayResult)
        val settings = PolicySettings(mode = mode)
        return Triple(
            ActuationCoordinator(heldStore, gateway, PassThroughDecisionProvider(), scheduler, clock, registry),
            settings,
            makeSnapshot()
        )
    }

    @Before
    fun setup() {
        heldStore = InMemoryHeldStore()
        scheduler = NoOpReleaseJobScheduler()
        clock = FakeClock(ms = 1_000_000L)
        registry = ReleasedKeyRegistry(clock)
    }

    @Test
    fun `OFF mode returns FAILED_OPEN with zero gateway calls`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator(mode = PolicyMode.OFF)
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.FAILED_OPEN, result.status)
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun `DRY_RUN mode returns FAILED_OPEN with zero gateway calls`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator(mode = PolicyMode.DRY_RUN)
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.FAILED_OPEN, result.status)
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun `kill switch returns FAILED_OPEN`() = runBlocking {
        gateway = FakeNotificationGateway()
        val settings = PolicySettings(mode = PolicyMode.LIVE, killSwitch = true)
        val coordinator = ActuationCoordinator(heldStore, gateway, PassThroughDecisionProvider(), scheduler, clock, registry)
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, makeSnapshot(), settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.FAILED_OPEN, result.status)
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun `successful DIGEST action results in HELD status`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator()
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.HELD, result.status)
        assertEquals(1, gateway.calls.size)
        assertEquals("cancel", gateway.calls[0].type)
    }

    @Test
    fun `successful DELAY action on messaging package results in SNOOZED`() = runBlocking {
        gateway = FakeNotificationGateway()
        val settings = PolicySettings(mode = PolicyMode.LIVE)
        val coordinator = ActuationCoordinator(heldStore, gateway, PassThroughDecisionProvider(), scheduler, clock, registry)
        val delayDecision = baseDecision.copy(action = Action.DELAY, keyHash = "hash-wa", postedTsMs = 1_000_000L)
        val whatsappFacts = baseFacts.copy(keyHash = "hash-wa", appCat = "msg", pkg = "com.whatsapp")
        val req = ActuationCoordinator.ActuateRequest(whatsappFacts, delayDecision, makeSnapshot(), settings, 99, "com.whatsapp", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.SNOOZED, result.status)
        assertEquals("snooze", gateway.calls[0].type)
    }

    @Test
    fun `gateway failure returns FAILED_OPEN`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator(
            gatewayResult = NotificationGateway.Result.Failed("SecurityException")
        )
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.FAILED_OPEN, result.status)
    }

    @Test
    fun `gateway unknown returns ACTUATION_UNKNOWN`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator(
            gatewayResult = NotificationGateway.Result.Unknown
        )
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.ACTUATION_UNKNOWN, result.status)
    }

    @Test
    fun `loop guard prevents re-actuation of same keyHash`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator()
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        val first = coordinator.actuate(req)
        assertEquals(ActuationStatus.HELD, first.status)
        // Second call same keyHash — loop guard active
        val req2 = ActuationCoordinator.ActuateRequest(
            baseFacts.copy(postedTsMs = 2_000_000L),
            baseDecision.copy(postedTsMs = 2_000_000L),
            snapshot, settings, 43, "com.example.app", null
        )
        val second = coordinator.actuate(req2)
        assertEquals(ActuationStatus.FAILED_OPEN, second.status)
        // Only 1 gateway call total
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `ALLOW action results in no hold (FAILED_OPEN via NONE mechanism)`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator()
        val allowDecision = baseDecision.copy(action = Action.ALLOW, keyHash = "hash-allow", postedTsMs = 1_000_000L)
        val allowFacts = baseFacts.copy(keyHash = "hash-allow")
        val req = ActuationCoordinator.ActuateRequest(allowFacts, allowDecision, snapshot, settings, 42, "com.example.app", null)
        val result = coordinator.actuate(req)
        assertEquals(ActuationStatus.FAILED_OPEN, result.status)
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun `release job is scheduled after successful hold`() = runBlocking {
        val (coordinator, settings, snapshot) = makeCoordinator()
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        coordinator.actuate(req)
        assertEquals(1, scheduler.scheduledKeys.size)
        assertEquals(baseFacts.keyHash, scheduler.scheduledKeys[0].first)
    }

    @Test
    fun `PENDING row written before gateway call — crash recovery`() = runBlocking {
        // Use a gateway that returns Unknown to simulate a crash scenario
        val (coordinator, settings, snapshot) = makeCoordinator(
            gatewayResult = NotificationGateway.Result.Unknown
        )
        val req = ActuationCoordinator.ActuateRequest(baseFacts, baseDecision, snapshot, settings, 42, "com.example.app", null)
        coordinator.actuate(req)
        // Row should exist in store (may be ACTUATION_UNKNOWN state)
        val row = heldStore.get(baseFacts.keyHash)
        assertNotNull(row)
        assertEquals("ACTUATION_UNKNOWN", row!!.state)
    }
}
