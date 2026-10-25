package com.dhyan.app.actuator

import com.dhyan.app.actuator.release.ReleaseJobScheduler
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.decision.Decision
import com.dhyan.app.twin.decision.DecisionProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.Recheck
import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 11-step actuation coordinator. Single entry point for all notification actuation.
 *
 * Step sequence (fail-open on any unexpected state):
 *  1.  Check mode (OFF | DRY_RUN → bail, zero gateway calls).
 *  2.  Check kill switch → FAILED_OPEN if active.
 *  3.  Loop guard: reject recently released keyHash.
 *  4.  Choose mechanism (SNOOZE | CANCEL_DIGEST).
 *  5.  Write PENDING row to DB (crash-safe).
 *  6.  recheck() — synchronous, timeout 50ms. ABORT → FAILED_OPEN.
 *  7.  Execute gateway call with [ActuatorConfig.CONFIRM_TIMEOUT_MS] timeout.
 *  8.  Confirm result → HELD / SNOOZED / ACTUATION_UNKNOWN / FAILED_OPEN.
 *  9.  Update DB row to confirmed state.
 * 10.  Register in loop guard.
 * 11.  Schedule release job (if SNOOZED/HELD with holdUntil).
 */
class ActuationCoordinator(
    private val heldStore: HeldStore,
    private val gateway: NotificationGateway,
    private val decisionProvider: DecisionProvider,
    private val releaseJobScheduler: ReleaseJobScheduler,
    private val clock: Clock = SystemClock,
    private val releasedKeyRegistry: ReleasedKeyRegistry = ReleasedKeyRegistry(SystemClock)
) {

    data class ActuateRequest(
        val facts: NotificationFacts,
        val decision: Decision,
        val snapshot: ContextSnapshot,
        val settings: PolicySettings,
        val platformId: Int,
        val packageName: String,
        /** Plaintext for encryption. Null if text unavailable (privacy guard accepted). */
        val holdPayload: String?
    )

    data class ActuateResult(
        val status: ActuationStatus,
        val keyHash: String
    )

    suspend fun actuate(req: ActuateRequest): ActuateResult = withContext(Dispatchers.Default) {
        val nowMs = clock.nowMs()
        val kh = req.facts.keyHash

        // Step 1 — mode check
        if (req.settings.mode == PolicyMode.OFF) {
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }
        if (req.settings.mode == PolicyMode.DRY_RUN) {
            // Log the decision but do nothing to the platform
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }

        // Step 2 — kill switch
        if (req.settings.killSwitch) {
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }

        // Step 3 — loop guard
        if (releasedKeyRegistry.isRecent(kh)) {
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }

        // Step 4 — choose mechanism
        val mechanism = MechanismChooser.choose(
            appCat = req.facts.appCat,
            action = req.decision.action,
            packageName = req.packageName,
            mode = req.settings.mode
        )
        if (mechanism == MechanismChooser.Mechanism.NONE) {
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }

        // Step 5 — write PENDING row
        val retentionMs = ActuatorConfig.RETENTION_DAYS * 24L * 60L * 60L * 1_000L
        val holdUntilMs = if (req.decision.holdMinutes != null)
            nowMs + (req.decision.holdMinutes * 60_000).toLong()
        else 0L

        val pendingEntity = HeldNotifEntity(
            keyHash = kh,
            mechanism = mechanism.name,
            state = "PENDING",
            appCat = req.facts.appCat,
            notifCat = req.facts.notifCat,
            senderHash = req.facts.senderHash,
            postedTsMs = req.facts.postedTsMs,
            heldSinceMs = nowMs,
            holdUntilMs = holdUntilMs,
            expiresAtMs = nowMs + retentionMs,
            ciphertext = null,
            iv = null,
            platformId = req.platformId,
            pkgHash = req.packageName.hashCode().toString(),
            decisionAction = req.decision.action.name,
            propensity = req.decision.propensity
        )
        heldStore.insert(pendingEntity)

        // Step 6 — recheck (50ms budget)
        val recheckResult = withTimeoutOrNull(50L) {
            decisionProvider.recheck(req.facts, req.decision, req.settings, clock.nowMs())
        } ?: Recheck.Abort("recheck_timeout")
        if (recheckResult is Recheck.Abort) {
            heldStore.setState(kh, ActuationStatus.FAILED_OPEN)
            return@withContext ActuateResult(ActuationStatus.FAILED_OPEN, kh)
        }

        // Step 7 — platform call (with timeout)
        val gatewayResult = withTimeoutOrNull(ActuatorConfig.CONFIRM_TIMEOUT_MS) {
            when (mechanism) {
                MechanismChooser.Mechanism.SNOOZE -> gateway.snooze(
                    kh, req.platformId, req.packageName,
                    req.decision.holdMinutes?.let { (it * 60_000).toLong() }
                        ?: ActuatorConfig.DEFAULT_SNOOZE_MS
                )
                MechanismChooser.Mechanism.CANCEL_DIGEST -> gateway.cancel(
                    kh, req.platformId, req.packageName
                )
                MechanismChooser.Mechanism.NONE ->
                    NotificationGateway.Result.Failed("no_mechanism")
            }
        }

        // Step 8 — interpret result
        val finalStatus = when (gatewayResult) {
            is NotificationGateway.Result.Ok -> when (mechanism) {
                MechanismChooser.Mechanism.SNOOZE -> ActuationStatus.SNOOZED
                MechanismChooser.Mechanism.CANCEL_DIGEST -> ActuationStatus.HELD
                else -> ActuationStatus.FAILED_OPEN
            }
            is NotificationGateway.Result.Failed -> ActuationStatus.FAILED_OPEN
            is NotificationGateway.Result.Unknown, null -> ActuationStatus.ACTUATION_UNKNOWN
        }

        // Step 9 — update DB
        if (finalStatus == ActuationStatus.ACTUATION_UNKNOWN) {
            heldStore.setState(kh, ActuationStatus.ACTUATION_UNKNOWN)
            return@withContext ActuateResult(ActuationStatus.ACTUATION_UNKNOWN, kh)
        }
        heldStore.setState(kh, finalStatus)

        // Step 10 — register in loop guard
        releasedKeyRegistry.register(kh)

        // Step 11 — schedule release
        if (holdUntilMs > 0 && finalStatus in listOf(ActuationStatus.HELD, ActuationStatus.SNOOZED)) {
            releaseJobScheduler.scheduleRelease(kh, holdUntilMs)
        }

        SharedActuationEventBus.emit(
            ActuationEvent(
                keyHash = kh,
                status = finalStatus,
                appCat = req.facts.appCat,
                timestampMs = nowMs
            )
        )

        ActuateResult(finalStatus, kh)
    }
}
