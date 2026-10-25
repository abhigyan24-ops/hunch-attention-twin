# STAGE4B_NOTES.md: Episodes, Learning Pipeline P5, Models & Parameter Recovery

## 1. Overview
Stage 4B connects the pure-Kotlin Persona synthetic replay world (Stage 4A) to the Twin Core inference and parameter stores (Stage 3) and Phone collectors (Stage 2).
It implements:
1. **Single-Owner Attribution (`EpisodeBuilder.kt`)** per spec 7.1 and 18.1.
2. **Outcome Windows & Measurement (`Episode.kt`)** tracking $W=30\text{ min}$, pickup latency, dwell duration, and laptop resumption delays.
3. **Episode Closer & Learning Pipeline P5 (`EpisodeCloser.kt`)** feeding closed episode outcomes into `InMemoryParamStore` in $O(1)$ time.
4. **Event Stream Adapter (`EventStreamAdapter.kt`)** adapting synthetic `GenEvent`s, JSONL traces, and real collector streams into episode lifecycle events.
5. **Noisy-OR Importance Model (`ImportanceModel.kt`)** per spec 8.5 with VIP override, high-urgency category detection, reply action boosts, repeat count scaling, and online Beta feedback learning.
6. **Intentional vs. Compulsive Score (`CompulsiveScore.kt`)** per spec 8.6 with logistic regression over 8 signals, online SGD with L2 regularization towards priors ($\eta = 0.05, \lambda = 0.01$), and action thresholds (`SILENT`, `PROMPT_ONCE`, `GENTLE_NUDGE`).
7. **Stage 4 Exit Criterion Test (`Stage4BParameterRecoveryTest.kt`)**: Replaying 14 days of synthetic history for persona Rahul recovers $p_{\text{pickup}}$ within $\pm 0.08$ of ground truth.

---

## 2. Importance API for Stage 5

### Class & Construction
- **Class:** `com.dhyan.app.twin.decision.ImportanceModel`
- **Location:** `phone/app/src/main/java/com/dhyan/app/twin/decision/ImportanceModel.kt`
- **Constructor:**
  ```kotlin
  val importanceModel = ImportanceModel(
      defaultPriorAlpha = 1.0,
      defaultPriorBeta = 4.0 // Default prior mean = 1/(1+4) = 0.20
  )
  ```
- **Purity & Privacy Guarantee:**
  - Pure Kotlin, synchronous, in-memory, thread-safe (`ConcurrentHashMap`).
  - **Zero content reading:** Does not inspect or persist raw notification text, titles, or body content. All text evaluation (e.g. `isOtpLike`, `hasEmergencyKeyword`) is performed transiently upstream in memory by `NotifExtractor` (D56-1), passing non-content boolean flags only.

### Scoring Method Signature
```kotlin
fun score(
    senderHash: String?,
    appCat: String,
    notifCat: String?,
    isVip: Boolean,
    hasReplyAction: Boolean = false,
    repeatCount5m: Int = 0,
    hasEmergencyKeyword: Boolean = false,
    isOtpLike: Boolean = false,
    urgencyScore: Double = 0.0,
    rawTextHint: String? = null // Retained for backwards compatibility; ignored when flags provided
): Double
```

### Parameter Documentation
| Parameter | Type | Range / Semantics | Description |
|---|---|---|---|
| `senderHash` | `String?` | SHA-256 salted hash | Salted hash of sender identity. Shrinks towards `appCat` prior with $\kappa=2.0$. |
| `appCat` | `String` | Taxonomy category | e.g. `"social"`, `"comms"`, `"work"`, `"entertainment"`. |
| `notifCat` | `String?` | Channel category | e.g. `"call"`, `"alarm"`, `"otp"`, `"msg"`, `"promo"`. High-urgency channels score $\ge 0.85$. |
| `isVip` | `Boolean` | `true` / `false` | If `true`, returns `1.0` immediately (VIP override). |
| `hasReplyAction` | `Boolean` | `true` / `false` | True if notification contains direct inline reply action (+0.15 boost). |
| `repeatCount5m` | `Int` | $\ge 0$ | Count of recent notifications from this sender in last 5 min ($\ge 3 \implies 0.60, \ge 2 \implies 0.30$). |
| `hasEmergencyKeyword` | `Boolean` | `true` / `false` | Transient keyword match from `NotifExtractor` (D56-1). Scores $\ge 0.80$. |
| `isOtpLike` | `Boolean` | `true` / `false` | Transient OTP token pattern match from `NotifExtractor` (D56-1). Scores `0.95`. |
| `urgencyScore` | `Double` | $[0.0, 1.0]$ | System base urgency score. |

### Example Calls for Stage 5 DecisionPipeline
```kotlin
// Example 1: VIP Contact
val vipScore = importanceModel.score(
    senderHash = "hash_mom",
    appCat = "comms",
    notifCat = "msg",
    isVip = true
) // returns 1.0

// Example 2: Routine social alert during coding
val socialScore = importanceModel.score(
    senderHash = "hash_instagram_like",
    appCat = "social",
    notifCat = "social",
    isVip = false,
    hasReplyAction = false,
    repeatCount5m = 0
) // returns ~0.20 (prior mean)

// Example 3: Repeated urgent Slack message with reply action
val urgentWorkScore = importanceModel.score(
    senderHash = "hash_lead",
    appCat = "work",
    notifCat = "msg",
    isVip = false,
    hasReplyAction = true,
    repeatCount5m = 3,
    hasEmergencyKeyword = true
) // returns ~0.94 (Noisy-OR combination of sender, emergency, reply, repeat)
```

---

## 3. Learning Sink & Feedback Architecture

Stage 4B establishes two distinct learning sinks:

1. **Explicit Feedback Sink (`ImportanceModel.observeFeedback`)**:
   ```kotlin
   fun observeFeedback(
       senderHash: String?,
       appCat: String,
       isImportant: Boolean,
       gamma: Double = 0.995
   )
   ```
   - **Triage Action "Important":** Calls `observeFeedback(senderHash, appCat, isImportant = true)`. Updates sender $\alpha \leftarrow \alpha + 1$ with exponential decay $\gamma=0.995$.
   - **Triage Action "Noise":** Calls `observeFeedback(senderHash, appCat, isImportant = false)`. Updates sender $\beta \leftarrow \beta + 1$.
   - **Implicit Interaction:** If a notification is opened within 2 minutes of presentation, `isImportant = true` is observed. If dismissed or ignored in digest, `isImportant = false` is observed.

2. **Episode Attribution & Parameter Store Learning Sink (`EpisodeCloser`)**:
   - `EpisodeCloser.closeEpisode(episode, endTs, now)` is the single sink for closed episode outcomes.
   - Updates `InMemoryParamStore` in $O(1)$ time:
     - $p_{\text{pickup}}(\text{bucket})$: Beta update on `pickedUp`.
     - $\text{dwellMin}(\text{category})$: LogNormal suff-stats update $(n, \mu, M2)$ on dwell duration.
     - $\text{resumeDelayMin}(\text{surface})$: LogNormal suff-stats update on resumption latency.
     - Compulsive score: Online SGD step on observed off-task drift.
   - Real database persistence is handled via `ParamStorePersistence` (Room entity `params` created in Stage 2, `RoomParamPersistence.kt`).

---

## 4. Key Results & Validation

### Exit Criteria (Spec 10.4, 17.2 Stage 4):
- **14-Day Rahul Replay Parameter Recovery:**
  - Ground truth $p_{\text{pickup}}(\text{social\_notif} \mid \text{coding}) = 0.62$
  - Learned parent posterior $p_{\text{pickup}} = 0.6284$ ($\alpha = 28.28, \beta = 14.49, n_{\text{eff}} = 24.35$)
  - **Absolute error: $0.0084$** ($\ll 0.08$ spec limit). **PASS.**
  - Learned dwell distribution shifted toward ground truth ($\mu = 2.39$ vs $\mu_{\text{truth}} = 2.6$).
  - Learned resume delay shifted toward ground truth ($\mu = 1.64$ vs $\mu_{\text{truth}} = 2.0$).
- **Two Notifications 3 Minutes Apart Test:**
  - Notification A at $t_0$, Notification B at $t_0 + 3\text{ min}$ before pickup.
  - Episode A closes immediately at $t_0 + 3\text{ min}$ with `label = SUPERSEDED`, `pickedUp = false`.
  - Episode B becomes the active trigger; user opens app at $t_0 + 4\text{ min}$, dwell = 6 min, resume delay = 2 min.
  - Episode B closes as `label = NOTIF_TRIGGERED`, `pickedUp = true`, `dwellMin = 6.0`, `resumeDelayMin = 2.0`. **PASS.**
- **In-Session Arrival Test:**
  - Notification arriving while user is `IN_APP` or `RESUMING` increments `inSessionArrivalCount` and does not supersede or open a new episode. **PASS.**
- **Self-Initiated Pickup Test:**
  - User opens app with no notification in prior 2 minutes. Opens `SELF_INITIATED` episode, updates baseline hazard upon close. **PASS.**
- **Compulsive Score SGD Update:**
  - Calm working session evaluates to `SILENT` ($P < 0.35$).
  - Endless feed + late hour evaluates to `GENTLE_NUDGE` ($P > 0.65$).
  - Moderate drift evaluates to `PROMPT_ONCE` ($P \in [0.35, 0.65]$).
  - Online SGD updates weights with regularized decay toward prior weights. **PASS.**

---

## 5. Test & Purity Verification Summary
- **Unit Tests:**
  - `phone:testDebugUnitTest`: **126/126 PASS** (57 Personas, 45 Twin Core, 16 Stage 4B, 7 ImportanceModel, 1 SchemaVersion; 0 failures).
  - `node:test`: **55/55 PASS** (0 failures, 0 skipped).
  - `laptop:test`: **8/8 PASS** (0 failures).
  - `relay:test`: **12/12 PASS** (0 failures).
- **Checks:**
  - `npm run checks`: **PASS** (`schema-sync`, `twin-purity`, `no-persona-imports`).
  - `npm run check:twin`: **PASS** (167 twin files verified pure Kotlin, zero Android/AndroidX imports).
  - `npm run check:twin-contract`: **PASS** (7 snapshots match schema).
  - `npm run check:personas`: **PASS** (22 files pure, 5 fixtures 12,567 events validated).
- **Target Hardware Statement:**
  - Reno 14 (ColorOS) actuation and hardware checks: `not verified on target` (desktop JVM unit tests executed in this session).

---

## 6. Assumptions & Cross-Stage Requests

### Assumptions:
- **A4B-1:** Single-owner attribution isolates overlapping notifications by marking superseded alerts unpicked with zero dwell attributed to them (Spec 7.1).
- **A4B-2:** In-session notifications during active app use increment `inSessionArrivalCount` without triggering new episodes.
- **A4B-3:** Importance model uses Noisy-OR combination of sender, urgency, and repeat terms with VIP=1.0 override.
- **A4B-4:** Text keyword inspection is performed exclusively in transient memory by `NotifExtractor`; raw notification text is never stored in parameters or episode logs.

### Cross-Stage Requests:
- **For Stage 5 (`DecisionPipeline`):**
  - Consume `ImportanceModel.score()` using non-content facts and transient boolean flags (`hasEmergencyKeyword`, `isOtpLike`).
  - Wire user feedback from triage actions ("Important" / "Noise") into `ImportanceModel.observeFeedback()`.
- **For Stage 6 (`ActuationCoordinator` & Room):**
  - Record decision logs with `DecisionRecord` containing non-content attributes (hashes, enums, numbers only).
  - Expose feedback actions in the Digest UI connected to the feedback sink.
