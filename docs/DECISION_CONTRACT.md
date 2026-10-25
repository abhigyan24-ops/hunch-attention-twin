# Decision contract version 1 — Phase 0 review candidate

This contract is frozen only after owner review and merge of the Phase 0 commit. Stage 5 computes; Stage 6 actuates and persists. No real DecisionPipeline exists in Phase 0. Stages 5A–5E remain not_started.

## Semantics and ownership

DecisionProvider.decide is synchronous and must never throw or perform I/O: any missing/invalid/stale/throwing dependency yields ALLOW and failedOpen=true. The safe default AlwaysAllowDecisionProvider always emits reasonCode=no_provider (safetyReason=null) and rejects recheck with Abort(no_provider). OFF skips policy simulation; DRY_RUN computes but never actuates; LIVE permits actuation only after recheck. Never-hold rules, identity and age are rechecked immediately before every actuator operation. Their production implementation is Stage5B/C; the Phase0 default cannot hold.

SHOW=ALLOW; SUPPRESS=DELAY|DIGEST|SILENT. propensity is the probability of the returned action after safety filtering. pShow=propensity for ALLOW, 1-propensity otherwise. EligibleActions must contain only possible actions. For hold-to-ALLOW exploration it contains the cost-selected hold and ALLOW; no inference of randomized support is allowed for deterministic records. Evidence strength is a heuristic, not probability of correctness. Provenance is a controlled label: observed,learned,simulated,estimated,low evidence.

PolicySettings copies and freezes allowListPackages. Other data-class read-only collections must be constructed from immutable collections by producers. DecisionRecordFactory defensively copies factors and eligible actions and strips all explanation/factor text. Hashes must already be pseudonymous; the contract does not hash or inspect platform notifications. OTP/emergency flags are ephemeral in-memory facts, not persisted in the record. Stage6 must not treat a Decision as confirmed actuation: status starts null, and HELD/SNOOZED require confirmed platform results. ACTUATION_UNKNOWN must remain visible.

## Units and fields

All *Ms timestamps are epoch milliseconds, holdMinutes/predicted losses/deltas/factor contributions are focus-minutes. urgencyScore,importance,evidenceStrength,propensity,pShow and snapshot confidence are dimensionless [0,1]. Versions and repeat counts are nonnegative counters. Nullable predictions indicate missing data, not zero. NotificationFacts fields contain hashes, classification tokens, counts, probabilities and booleans only. Factor.text and explanation are controlled template strings, never notification content. DecisionRecord has no text/explanation fields; kind,reasonCode,safetyReason,policyVersion and summary strings are fixed tokens, not arbitrary user text. The exact field and enum inventory is included below from the defining sources.

Defaults mirror AppendixA. MAX_DECISION_AGE_MS=30000 is S5-7. Additional initial snapshot defaults: OFF, killSwitchfalse, explorationEnabledfalse,strictSessionfalse,empty allowlist,seed1. Constructors permit invalid numeric snapshots so the production pipeline can fail open rather than throw during policy receipt; callers must validate before holding. RecordFactory throws fixed identity_mismatch on inconsistent facts/decision; it is not the no-throw decision hot path.

## Shared digest schedule

DigestSchedule.nextDigestTsMs(nowMs,intervalMin=120,zone=systemDefault) returns a strictly future epoch-ms boundary on an interval grid beginning at local midnight. The cost function and Stage 6 release scheduler MUST both pass settings.digestIntervalMin and the same explicit zone to this function; neither may silently use a separate interval or zone. The additive trailing PolicySettings.digestIntervalMin defaults to PolicyDefaults.DIGEST_INTERVAL_MIN (120) and preserves previous positional constructor calls. DST overlaps consider both valid offsets; gaps shift nonexistent local boundary times forward with java.time resolution. Daily grid resets at midnight even for intervals not dividing1440. Supported interval1..1440; invalid programmer arguments throw invalid_interval. This helper does not read a clock. Extreme unsupported java.time epochs can throw and must be guarded by the pipeline/actuator.

## Kill switch

InMemoryKillSwitch is state only. Synchronized transitions are idempotent; listeners are unique and notified serially during transition, so listener implementations must be nonblocking and should not call state transitions recursively. Listener throwables are counted, message contents discarded, and other listeners continue. activate/deactivate/restore never reject negative times: each actual transition and boot restore clamps its timestamp to max(0,value). Idempotent calls retain the previous transition timestamp. active/changedAtMs are volatile reads; use the state semantics rather than claiming an atomic two-field snapshot. restore silently loads persisted values, with no duplicated actuator effects. Stage6 owns durable state, release/restoration and job cancellation. ExpiryPolicy is interface only; defaults are Stage5B work.

## Change protocol and sketch deltas

Owner approval and a new Phase0 contract commit are required for any frozen-file change. Increment DECISION_CONTRACT_VERSION and update tools/decision-contract.lock, then both stage branches rebase. SHA256 hashes normalize CRLF to LF to remain stable on Windows. Pure guard and lock self-tests must reject planted violations/mutations. Lock changes must be reviewed, never blindly regenerated to silence a failing check.

Deltas from the common sketch: Decision adds keyHash/postedTsMs/pShow; DigestSchedule adds zone; KillSource, KillSwitchControl, KillSwitchListener, InMemoryKillSwitch, ExpiryPolicy, AlwaysAllowDecisionProvider and DecisionRecordFactory are explicit. Settings conservative defaults above are proposed for review. Constants/schedule/default-provider/kill-switch implementation are part of the frozen initial contract, not future mutable implementation files. No dependency on Stage4B ImportanceModel is introduced by the contract. Target behavior is not verified on target.

## Pre-freeze amendments (owner requested; version remains 1)

Decision and DecisionRecord add trailing reasonCode:String="unspecified" for source compatibility. AlwaysAllowDecisionProvider uses no_provider; DecisionRecordFactory copies reasonCode unchanged. reasonCode is a controlled general-cause token, with initial vocabulary unspecified, mode_off, kill_switch, invalid_input, never_hold, high_importance, not_ready, low_evidence, cost_selected, explored, no_provider. It never carries notification content, exception messages or free-form explanation. Producers are responsible for controlled tokens; these value constructors do not parse or sanitize input. safetyReason is reserved for the specific never-hold rule token (e.g. vip,otp,call), and is null for general causes such as no_provider or mode_off. A general never_hold reasonCode may accompany a specific safetyReason.

The review candidate is not frozen or merged, so these owner-requested changes regenerate the version1 lock without incrementing DECISION_CONTRACT_VERSION. After freeze, the normal versioned change protocol applies. No pipeline is implemented here.

### DecisionContract.kt

```kotlin
package com.dhyan.app.twin.decision

const val DECISION_CONTRACT_VERSION = 1
enum class Action { ALLOW, DELAY, DIGEST, SILENT }
enum class ActuationStatus { HELD, SNOOZED, SCHEDULED, RELEASED, EXPIRED, FAILED_OPEN, ACTUATION_UNKNOWN }
enum class PolicyMode { OFF, DRY_RUN, LIVE }
enum class KillSource { USER_TILE, USER_NOTIFICATION, USER_SETTINGS, SYSTEM }
```

### NotificationFacts.kt

```kotlin
package com.dhyan.app.twin.decision

/** No notification content. Text-derived flags are ephemeral Stage 6 inputs, never logged. */
data class NotificationFacts(
    val keyHash: String, val postedTsMs: Long, val pkg: String, val appCat: String,
    val notifCat: String, val senderHash: String?, val isVip: Boolean,
    val urgencyScore: Double, val hasReplyAction: Boolean, val isMessagingStyle: Boolean,
    val repeatCount5m: Int, val isOtpLike: Boolean, val hasEmergencyKeyword: Boolean
)
```

### PolicyDefaults.kt

```kotlin
package com.dhyan.app.twin.decision

/** Appendix A defaults; recheck age is explicit policy assumption S5-7. */
object PolicyDefaults {
    const val KAPPA_D_PER_HOUR = 3.0
    const val LAMBDA_MISS_MIN = 5.0
    const val C_DIGEST_MIN = 0.8
    const val C_REL = 0.25
    const val I_MAX = 0.85
    const val I_SILENT = 0.16
    const val EPSILON = 0.05
    const val CONF_MIN = 0.4
    const val C_GLANCE_MIN = 0.4
    const val WINDOW_MIN = 30.0
    const val DIGEST_INTERVAL_MIN = 120
    const val MAX_DECISION_AGE_MS = 30000L
}
```

### PolicySettings.kt

```kotlin
package com.dhyan.app.twin.decision

import java.util.Collections

/** Immutable snapshot. Runtime validation belongs in the fail-open pipeline, not this constructor. */
class PolicySettings(
    val mode: PolicyMode = PolicyMode.OFF,
    val killSwitch: Boolean = false,
    val kappaDPerHour: Double = PolicyDefaults.KAPPA_D_PER_HOUR,
    val explorationEnabled: Boolean = false,
    val epsilon: Double = PolicyDefaults.EPSILON,
    val strictSession: Boolean = false,
    allowListPackages: Set<String> = emptySet(),
    val seed: Long = 1L,
    /** Costing and Stage 6 scheduling must pass this interval and the same zone to DigestSchedule. */
    val digestIntervalMin: Int = PolicyDefaults.DIGEST_INTERVAL_MIN
) {
    val allowListPackages: Set<String> = Collections.unmodifiableSet(LinkedHashSet(allowListPackages))
}
```

### Decision.kt

```kotlin
package com.dhyan.app.twin.decision

/** text is controlled template output, never notification text. contribution is focus-minutes. */
data class Factor(val kind: String, val text: String, val contribution: Double)

/** Read-only value record. Callers must provide immutable factor/action collections. */
data class Decision(
    val action: Action, val explanation: String, val factors: List<Factor>,
    val holdMinutes: Double?, val propensity: Double, val eligibleActions: Set<Action>,
    val explored: Boolean,
    /** Reserved for a never-hold rule token; general decision causes belong in reasonCode. */
    val safetyReason: String?, val failedOpen: Boolean,
    val predictedLossShowMin: Double?, val predictedLossSuppressMin: Double?,
    val deltaLMean: Double?, val deltaLP10: Double?, val deltaLP90: Double?,
    val evidenceStrength: Double?, val provenance: String, val modelVersion: Long,
    val policyVersion: String, val decidedAtMs: Long, val keyHash: String, val postedTsMs: Long,
    /** Controlled token only, never input content or exception messages. */
    val reasonCode: String = "unspecified"
) {
    /** SHOW=ALLOW. SUPPRESS=DELAY|DIGEST|SILENT. Exact chosen-action propensity after filtering. */
    val pShow: Double get() = if (action == Action.ALLOW) propensity else 1.0 - propensity
}
```

### DecisionRecord.kt

```kotlin
package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot
import java.util.Collections

data class RecordedFactor(val kind: String, val contribution: Double)
data class SnapshotSummary(val focusState: String, val taskType: String, val hourBand: String,
    val fatigueBand: String, val laptopState: String, val confidence: Double)
/** Hashes, controlled enum/reason tokens and numbers only; no explanation or factor text. */
data class DecisionRecord(
    val keyHash: String, val postedTsMs: Long, val decidedAtMs: Long, val action: Action,
    val propensity: Double, val pShow: Double, val eligibleActions: Set<Action>, val explored: Boolean,
    val safetyReason: String?, val failedOpen: Boolean, val predictedLossShowMin: Double?,
    val predictedLossSuppressMin: Double?, val deltaLMean: Double?, val deltaLP10: Double?, val deltaLP90: Double?,
    val evidenceStrength: Double?, val factors: List<RecordedFactor>, val snapshotSummary: SnapshotSummary,
    val importance: Double, val modelVersion: Long, val policyVersion: String, val mode: PolicyMode,
    val actuationStatus: ActuationStatus? = null,
    /** Controlled general decision cause; safetyReason is reserved for never-hold rule tokens. */
    val reasonCode: String = "unspecified"
)
object DecisionRecordFactory {
    fun from(facts: NotificationFacts, decision: Decision, snapshot: ContextSnapshot,
        settings: PolicySettings, importance: Double): DecisionRecord {
        require(facts.keyHash == decision.keyHash && facts.postedTsMs == decision.postedTsMs) { "identity_mismatch" }
        return DecisionRecord(facts.keyHash,facts.postedTsMs,decision.decidedAtMs,decision.action,
            decision.propensity,decision.pShow,Collections.unmodifiableSet(LinkedHashSet(decision.eligibleActions)),
            decision.explored,decision.safetyReason,decision.failedOpen,decision.predictedLossShowMin,
            decision.predictedLossSuppressMin,decision.deltaLMean,decision.deltaLP10,decision.deltaLP90,
            decision.evidenceStrength,Collections.unmodifiableList(decision.factors.map { RecordedFactor(it.kind,it.contribution) }),
            SnapshotSummary(snapshot.focusState.wire,snapshot.taskType.wire,snapshot.hourBand,snapshot.fatigueBand,snapshot.laptopState.wire,snapshot.confidence),
            importance,decision.modelVersion,decision.policyVersion,settings.mode,reasonCode=decision.reasonCode)
    }
}
```

### DecisionProvider.kt

```kotlin
package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot

sealed interface Recheck {
    data object Proceed : Recheck
    data class Abort(val reason: String) : Recheck
}
interface DecisionProvider {
    /** Synchronous, no I/O or blocking. Never throws; any problem is ALLOW with failedOpen=true. */
    fun decide(facts: NotificationFacts, snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long): Decision
    /** Immediately before actuation: revalidate mode, safety, identity and age. Never throws. */
    fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck
}
interface DecisionLogStore {
    /** Implemented asynchronously by Stage 6; the decision hot path never calls this. */
    suspend fun append(record: DecisionRecord)
}
```

### AlwaysAllowDecisionProvider.kt

```kotlin
package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot

/** Safe dependency-injection default until the reviewed real pipeline is supplied. */
class AlwaysAllowDecisionProvider : DecisionProvider {
    override fun decide(facts: NotificationFacts, snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long) = Decision(
        Action.ALLOW, "Allowed: not enough information.", emptyList(), null, 1.0, setOf(Action.ALLOW),
        false, null, true, null, null, null, null, null, null, "low evidence", 0L,
        "contract-$DECISION_CONTRACT_VERSION-no-provider", nowMs, facts.keyHash, facts.postedTsMs, reasonCode="no_provider")
    override fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck = Recheck.Abort("no_provider")
}
```

### DigestSchedule.kt

```kotlin
package com.dhyan.app.twin.decision

import java.time.Instant
import java.time.ZoneId

object DigestSchedule {
    /** Strictly next local-midnight grid boundary; both offsets are considered at DST overlaps. */
    fun nextDigestTsMs(nowMs: Long, intervalMin: Int = PolicyDefaults.DIGEST_INTERVAL_MIN,
        zone: ZoneId = ZoneId.systemDefault()): Long {
        require(intervalMin in 1..1440) { "invalid_interval" }
        val now = Instant.ofEpochMilli(nowMs)
        val today = now.atZone(zone).toLocalDate()
        var best: Instant? = null
        // Include tomorrow: at interval1440 the next boundary is the next local midnight.
        for (day in 0L..2L) {
            val date = today.plusDays(day)
            var minute = 0
            while (minute < 1440) {
                val local = date.atStartOfDay().plusMinutes(minute.toLong())
                val offsets = zone.rules.getValidOffsets(local)
                val candidates = if (offsets.isEmpty()) listOf(local.atZone(zone).toInstant()) else offsets.map { local.toInstant(it) }
                for (candidate in candidates) if (candidate > now && (best == null || candidate < best)) best=candidate
                minute += intervalMin
            }
            if (best != null) return best.toEpochMilli()
        }
        error("no_digest_boundary")
    }
}
```

### KillSwitchControl.kt

```kotlin
package com.dhyan.app.safety

import com.dhyan.app.twin.decision.KillSource
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

interface KillSwitchListener {
    fun onActivated(source: KillSource)
    fun onDeactivated()
}
interface KillSwitchControl {
    val active: Boolean
    val changedAtMs: Long
    fun activate(source: KillSource, nowMs: Long)
    fun deactivate(nowMs: Long)
    fun restore(active: Boolean, changedAtMs: Long)
    fun addListener(l: KillSwitchListener)
    fun removeListener(l: KillSwitchListener)
}
/** State only. Transitions never throw; negative timestamps clamp to zero, including boot restore.
 * Stage 6 owns persistence, reboot restoration and notification release effects. */
class InMemoryKillSwitch : KillSwitchControl {
    @Volatile private var state = false
    @Volatile private var changed = 0L
    private val listeners = CopyOnWriteArrayList<KillSwitchListener>()
    private val errors = AtomicLong(0)
    val listenerFailureCount: Long get() = errors.get()
    override val active: Boolean get() = state
    override val changedAtMs: Long get() = changed
    @Synchronized override fun activate(source: KillSource, nowMs: Long) {
        if (state) return
        state=true; changed=nowMs.coerceAtLeast(0L)
        for (l in listeners) try { l.onActivated(source) } catch (_: Throwable) { errors.incrementAndGet() }
    }
    @Synchronized override fun deactivate(nowMs: Long) {
        if (!state) return
        state=false; changed=nowMs.coerceAtLeast(0L)
        for (l in listeners) try { l.onDeactivated() } catch (_: Throwable) { errors.incrementAndGet() }
    }
    @Synchronized override fun restore(active: Boolean, changedAtMs: Long) {
        // Restore persisted state silently; it must not duplicate actuator side effects.
        state=active; changed=changedAtMs.coerceAtLeast(0L)
    }
    override fun addListener(l: KillSwitchListener) { listeners.addIfAbsent(l) }
    override fun removeListener(l: KillSwitchListener) { listeners.remove(l) }
}
```

### ExpiryPolicy.kt

```kotlin
package com.dhyan.app.safety

import com.dhyan.app.twin.decision.NotificationFacts

interface ExpiryPolicy {
    /** Epoch milliseconds, null only when explicit policy says no expiry. */
    fun expiresAtMs(facts: NotificationFacts, decidedAtMs: Long): Long?
}
```
