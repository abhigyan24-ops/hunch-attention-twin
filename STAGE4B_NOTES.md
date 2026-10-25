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

## 2. Key Results & Validation

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

## 3. Test & Purity Verification Summary
- **Unit Tests:**
  - `phone:testDebugUnitTest`: **119/119 PASS** (0 failures, 0 skipped).
  - `node:test`: **55/55 PASS** (0 failures, 0 skipped).
- **Checks:**
  - `npm run checks`: **PASS** (`schema-sync`, `twin-purity`, `no-persona-imports`).
  - `npm run check:twin`: **PASS** (167 twin files verified pure Kotlin, zero Android/AndroidX imports).
  - `npm run check:twin-contract`: **PASS** (7 snapshots match schema).
  - `npm run check:personas`: **PASS** (22 files pure, 5 fixtures 12,567 events validated).
- **Target Hardware Statement:**
  - Reno 14 (ColorOS) actuation and hardware checks: `not verified on target` (desktop JVM unit tests executed in this session).
