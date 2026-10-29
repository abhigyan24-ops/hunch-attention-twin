# DHYAN: Stage 5 (Decision, safety, counterfactuals), Codex pack

Read together with `docs/STAGE5_6_COMMON.md` (the common prompt). If this file and the common prompt disagree on a name, **Prompt 5.0's output wins** (section 5.2 lists the small deltas).

Spec sources: 8.5 (importance), 9.1 to 9.6 (decision engine and safety), 10.1 to 10.3 (counterfactuals), 17.2 Stage 5, 25.3, 25.5, 25.6, 25.7, 25.14.

**Stage 5 decides. It never touches a notification, a database, a screen or the clock.** It is pure Kotlin, tested on the JVM only. Anything about the Reno 14 is "not verified on target".

**Spec exit check:** a 10-notification test sheet produces the expected verdicts, each of `SILENT / DIGEST / DELAY / ALLOW` is triggered at least once, and no never-hold item is ever held.

---

## 1. What Stage 5 delivers

| In scope | Not in scope |
|---|---|
| Frozen contract types and interfaces (5.0) | Any actuation: snooze, cancel, digest posting, Undo effects (Stage 6) |
| `CostFunction`, `ActionSelector`, `HoldTimeEstimator`, `DigestSchedule` | Room, `DecisionLogStore` implementation, UI (Stage 6) |
| Safety rules, `KillSwitchControl` state machine, fail-open wrapper, `ExpiryPolicy`, `recheck()` | Kill-switch effects, release scheduling, boot recovery (Stage 6) |
| `Explainer`, `ExplorationPolicy`, `DecisionPipeline` (real `DecisionProvider`) | Fusion that fills the `ContextSnapshot` (Stage 8); phone-only stopgap is Stage 6 |
| IPW, doubly robust, regret, saved minutes, overlap diagnostics | Mapping real Room decisions plus episodes into `ExplorationRecord` (integration, Stage 10) |
| Verdict sheets, urgent suite, latency benchmark | Importance model itself (Stage 4B owns it; Stage 5 consumes it) |

---

## 2. Prerequisites (you, about 15 minutes)

1. **Stage 3, Stage 4A, Stage 4B (or at least the importance model) and Stage 2 are merged on `main`.** If 4B's importance model is **not** merged, tell me before starting, because Stage 5 depends on it.
2. **Commit the common prompt** to `main` as `docs/STAGE5_6_COMMON.md` and this file as `docs/STAGE5_GUIDE.md`.
3. **Phase 0 branch:** `git checkout -b contract-5-0` on `main` for Prompts 5-PRE and 5.0. After you review and merge it, create the Stage 5 worktree **from that commit**:
   ```
   git worktree add ..\dhyan-s5 -b stage5-decision
   ```
   (Stage 6 uses `..\dhyan-s6`, created from the same commit.)
4. Worktree extras: copy `phone\local.properties`, run `npm install`, confirm `java -version` is 17, run `.\gradlew.bat :app:testDebugUnitTest` once before Codex starts.
5. Append the Part 3 block to `AGENTS.md` and commit it on the branch.
6. Answer the decisions in section 7 of the common prompt (D56-4 matters here).
7. Codex mode: one prompt per task, in order. Review the diff and run the commands yourself after each one.

---

## 3. AGENTS.md addendum (Stage 5 and the 5.0 contract branch)

```text
## Stage 5 rules (decision, safety, counterfactuals)
- Branches: contract-5-0 (Prompts 5-PRE, 5.0) then stage5-decision (5A to 5E). Commit after each prompt ("stage5: <name>"). Never push. Never touch main.
- OWNED: phone/app/src/main/java/com/dhyan/app/twin/decision/**, twin/counterfactual/**, safety/**, and matching src/test paths; docs/DECISION_CONTRACT.md; STAGE5_NOTES.md; tools/check-decision-purity.mjs, tools/check-decision-contract.mjs, tools/decision-contract.lock; one script key "check:decision" in root package.json.
- READ-ONLY: everything else in twin/ (params, simulator, engine, context, model), personas/ (tests may import it), core-spec/. FORBIDDEN: collectors/, ingest/, data/, ui/, actuator/, sync/, fusion/, inference/, Gradle files, AndroidManifest.xml, DI modules, and governance docs (TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md, DECISIONS_PENDING.md). Report needed changes in STAGE5_NOTES.md under "Cross-stage requests".
- Pure Kotlin in twin/decision, twin/counterfactual and safety: no android.*, androidx.*, Room, or any I/O. java.time is allowed. kotlinx.coroutines is allowed but DecisionProvider.decide and recheck are synchronous. No new dependencies.
- FAIL OPEN: decide() and recheck() never throw. Any missing, stale, invalid or throwing input yields ALLOW with failedOpen = true. Never put an exception message in a Decision (messages can leak content); use a fixed reason code.
- NEVER-HOLD is absolute and independent of cost, importance, exploration and mode. It is checked in decide() and again in recheck().
- Determinism: no Math.random, kotlin.random.Random.Default, wall clock, System.nanoTime or HashMap iteration order may influence a Decision. Time is passed as nowMs. Exploration uses a seeded hash stream (settings.seed, keyHash, postedTsMs).
- No text: NotificationFacts, Decision, DecisionRecord and Factor never contain notification content. Explanations are template output built from numbers and enums.
- Units: minutes for durations and hold times, kappaD per hour. Name variables with units. Every tau/60 conversion is explicit and tested.
- Constants live in core-spec/default-params.json and PolicyDefaults. Never hard-code a result to match the pitch (24 min, 0.62, 0.09). Never claim "urgent delayed = 0" except as the measured result of the named suite.
- Tests use several fixed seeds; never pick a seed to make a test pass. Never claim a test passed without running it and pasting the summary line. Anything about a device is "not verified on target".
- After Prompt 5.0 is merged the contract files are FROZEN (tools/decision-contract.lock). Do not edit them. Write a "Contract change request" in STAGE5_NOTES.md instead.
```

---

## 4. Design reference (authoritative for the prompts below)

Implement this. If you disagree with something, change this section and the tests together.

### 4.1 Pipeline (spec 9.1), in order
`decide(facts, snapshot, settings, nowMs)`:
1. Wrap everything in the fail-open guard.
2. `settings.mode == OFF` or `settings.killSwitch` → `ALLOW` (reason `mode_off` or `kill_switch`), no simulation.
3. Validate inputs (finite numbers, non-empty `keyHash`, `kappaD > 0`, `epsilon` in [0,1]); invalid → `ALLOW`, `failedOpen = true`.
4. `NeverHoldRules.check(facts, settings)` matches → `ALLOW`, `safetyReason` set.
5. `I = importance(facts)`; `I >= I_max (0.85)` → `ALLOW` (reason `high_importance`).
6. `LossPredictor.predict(snapshot, facts)`; `NotReady` (not loaded, stale snapshot, invalid) → `ALLOW`, `failedOpen = true`.
7. Risk aversion: `evidenceStrength < conf_min (0.4)` and `I > 0.5` → `ALLOW` (reason `low_evidence`).
8. Compute `J` for all four actions (4.2), select the minimum (4.3).
9. Exploration (4.5).
10. Explainer (4.7). Build the `Decision`. Nothing is logged here; the caller logs via `DecisionRecordFactory`.

`DRY_RUN` runs exactly the same path. The difference is only in Stage 6 (no actuation).

### 4.2 Cost function (spec 9.2, 25.6), units: focus-minutes
Inputs: `E_show`, `E_supp` (means of `L_show`, `L_suppress` in minutes from the simulator; `E_show` already includes `c_glance`), `I` in [0,1], `kappaD` per hour (strict session multiplies by 0.5), `tauRelease` and `tauDigest` in **minutes**, constants `c_rel = 0.25`, `c_digest = 0.8`, `lambda_miss = 5.0`.

```
J(ALLOW)  = E_show
J(DELAY)  = E_supp + kappaD * I^2 * (tauRelease / 60) + c_rel * E_show
J(DIGEST) = E_supp + kappaD * I^2 * (tauDigest  / 60) + c_digest
J(SILENT) = E_supp + kappaD * I^2 * (tauDigest  / 60) + lambda_miss * I
```
- `J` uses **expected values only**. Tail statistics (p10, p90, `P(L>1)`) are reported, never mixed into `J` (25.6).
- Boundary that must hold: `J(SILENT) < J(DIGEST)` exactly when `lambda_miss * I < c_digest`, i.e. `I < 0.16`.
- Tie-break order (S5-1): `ALLOW`, then `DELAY`, then `DIGEST`, then `SILENT` (least intrusive wins). Ties are values within `1e-9`.
- `ActionSelector` returns the winner, all four `J` values and the margin to the runner-up (the Explainer and tests use them).

### 4.3 Overrides summary (all before cost, none overridable by cost)
mode OFF, kill switch, invalid input, never-hold, `I >= 0.85`, `NotReady`, low evidence with `I > 0.5`. Each produces `ALLOW` with a fixed reason code. Strict session changes `kappaD` and disables exploration, but never relaxes never-hold.

### 4.4 Hold times
- `tauDigest = clamp((DigestSchedule.nextDigestTsMs(nowMs, intervalMin, zone) - nowMs) / 60000.0, 1.0, intervalMin)`.
- `DigestSchedule.nextDigestTsMs`: boundaries are multiples of `intervalMin` minutes from **local midnight** in the given `ZoneId` (default 120 → 00:00, 02:00, …). Strictly greater than `nowMs`. Handles DST by computing in local time. Both the cost function and Stage 6's scheduler call this one function.
- `tauRelease` (S5-2, D56-4): context-based estimate from the snapshot, defaults in `HoldTimeEstimator`: `break`/`recovering` or laptop `idle_open`/`away` → 15; `deep_focus`/`flowing` → 45; otherwise 30. Always capped at `tauDigest`. Documented assumption; no break predictor exists yet.
- `Decision.holdMinutes` = `tauRelease` for `DELAY`, `tauDigest` for `DIGEST` and `SILENT`, `null` for `ALLOW`.

### 4.5 Exploration and propensities (spec 9.4, 25.6, 25.7)
- **Eligible** iff all true: `explorationEnabled`, not strict session, not failed-open, mode not OFF, kill switch off, not never-hold, not VIP, `I < 0.4`, predicted `ΔL = E_show - E_supp > 0`, and cost-chosen action is a hold (`DELAY`/`DIGEST`/`SILENT`). (Spec-literal reading: "choose the other arm", S5-5.)
- If eligible: with probability `epsilon` return `ALLOW` instead, `explored = true`. Draw `u` from a deterministic stream seeded by `(settings.seed, keyHash, postedTsMs)` using splitmix64 mixing; the same notification always gets the same `u`.
- **Propensity** `propensity` = exact probability of the returned action **given the eligible set after safety filtering**: eligible and hold chosen → `1 - epsilon`; eligible and explored → `epsilon`; not eligible → `1.0`. `eligibleActions` lists what was actually possible (`{hold, ALLOW}` when eligible, otherwise just the chosen action).
- **Arm mapping (S5-6):** SHOW = `ALLOW`; SUPPRESS = any of `DELAY`, `DIGEST`, `SILENT`. `pShow = propensity` if the action is `ALLOW`, else `1 - propensity`.
- Never explore on never-hold, VIP, `I >= 0.4`, strict sessions or failed-open decisions. Never log a propensity that ignores safety filtering.

### 4.6 Safety (spec 9.5, 25.5)
- `NeverHoldRules.check(facts, settings)` returns a reason code or null. Rules: notification category in {call, alarm, navigation} (use the real values from `core-spec`; record the mapping), `isOtpLike`, `hasEmergencyKeyword`, `isVip`, package in `settings.allowListPackages`. Unknown `notifCat` values are **not** never-hold by themselves (S5-16) but are recorded in diagnostics.
- `FailOpen`: catches `Exception` (and `NotReady`), returns `ALLOW` with `failedOpen = true` and a fixed reason code (`exception:<simple class name>` only). Increments counters in `DecisionDiagnostics` (atomic, in memory).
- `recheck(facts, decision, settings, nowMs)` returns `Abort(reason)` if any: mode is not `LIVE`; kill switch on; decision is `ALLOW` or `failedOpen`; never-hold now matches (rules or settings may have changed); `decision.keyHash != facts.keyHash` or `decision.postedTsMs != facts.postedTsMs` (identity); `nowMs - decision.decidedAtMs > 30_000` (S5-7) or negative (clock went backwards). Otherwise `Proceed`.
- `ExpiryPolicy.expiresAtMs(facts, decidedAtMs)`: OTP-like 10 minutes; everything else 24 hours (S5-8). Expiry means removed from the digest and logged as `EXPIRED`, never silently dropped (Stage 6 logs it).
- `KillSwitchControl` (from 5.0): thread-safe state with `activate(source, nowMs)`, `deactivate(nowMs)`, `restore(active, changedAtMs)`, listeners. Pure.

### 4.7 Explainer (spec 9.3)
Template `Held: {factor1}; {factor2}. Tap to show now.` for holds; `Allowed: {reason}.` for allows. Candidate factors with a documented **ranking heuristic** (S5-9; it explains model terms, it is not causal attribution), score in minutes:

| Factor | Text template (numbers only) | Score |
|---|---|---|
| loss | `about {ΔL} min of focus lost (80% simulation interval {lo} to {hi})` | `ΔL` |
| low importance | `low importance` | `kappaD * (tau/60) * (1 - I^2)` |
| pickup pattern | `you open this about {k} of 10 times in this context` | `ΔL * pPickupMean` |
| fatigue | `{band} fatigue, so resuming takes longer` | `ΔL * (1 - 1/m_f)`, `m_f` = 1.0 / 1.2 / 1.5 |
| laptop state | `laptop is in {state} for {n} min` | `0` unless state is flowing/deep, then `0.25 * ΔL` |

Pick the top two by score (ties by the table order above). Skip factors with missing data. Round numbers deterministically. No app names, senders or content. Allow reasons: `a call, alarm or code`, `a contact you marked important`, `high importance`, `not enough evidence yet`, `Dhyan is off`, `kill switch is on`, `not enough information`.

### 4.8 Counterfactual estimators (spec 10.2, 25.7)
- **Estimand:** average per-notification effect `ΔL = E[Y(SHOW) - Y(SUPPRESS)]` in **eligible contexts where both arms had positive probability** (`0 < pShow < 1`), outcome `Y` = focus minutes lost within `W = 30` min, per notification. Not defined for never-hold, VIP, high-importance or strict-session contexts; never extrapolate there.
- **Record:** `ExplorationRecord(bucketKey, arm, pShow, yMin, censored, actuationFailed, predictedShowMin, predictedSuppressMin, eligible)`. `predicted*` are the twin's decision-time predictions (they use only data before the decision, so no cross-fitting is needed; S5-12).
- **Filters (counted and reported):** censored, actuation failed, not eligible, `pShow` in {0,1}, non-finite values.
- **IPW (Hájek, normalized, primary):** `Ŷ_show = Σ w_i 1[A_i=S] Y_i / Σ w_i 1[A_i=S]`, `w_i = 1/pShow_i`; `Ŷ_supp` likewise with `w_i = 1/(1-pShow_i)`. Report `ΔL̂_IPW = Ŷ_show - Ŷ_supp`. Also provide the plain Horvitz-Thompson form for comparison.
- **Doubly robust (AIPW):** `ψ_show,i = μ̂_show,i + 1[A_i=S](Y_i - μ̂_show,i)/pShow_i`; `ψ_supp,i = μ̂_supp,i + 1[A_i=U](Y_i - μ̂_supp,i)/(1 - pShow_i)`; `ΔL̂_DR = mean(ψ_show,i - ψ_supp,i)`. Valid if either the propensity or the twin's prediction is right.
- **Uncertainty:** seeded bootstrap over records (`B = 1000`), report the **80% bootstrap interval** (10th and 90th percentile). Do not call it a credible interval.
- **Diagnostics:** effective sample size per arm `(Σw)²/Σw²`, min and max `pShow`, share of weight in the top 5%, weight-truncation sensitivity at caps {none, 20, 10, 5}, counts per filter.
- **Threshold:** fewer than 20 usable exploratory records in a bucket → `status = MODEL_BASED_ONLY` and the report carries only the twin's model mean `ΔL`, labelled `simulated`. Note for the pitch: at `epsilon = 0.05` real-data buckets will rarely reach 20 quickly, while persona replays with the 4A `RandomizedHold(eps = 0.2)` policy will.
- **Labels:** `simulated` (model), `estimated` (exploration-supported IPW/DR), `observed` (raw realized outcomes). Never merge them.
- **Regret (S5-14):** `modelRegretMin = J(chosen) - min J` per decision (zero unless exploration or an override moved away from the argmin); `explorationCostMin = Σ over explored decisions of (J(ALLOW) - J(a*))`. **Saved minutes:** `modelSavedMin = Σ ΔL̂_model` over decisions whose actuation was **confirmed** (Stage 6 supplies the status), `delayCostMin = Σ kappaD * I² * heldHours` realized, and `estimatedSavedMin` from DR where available. Three separate labelled numbers, no silent netting.

### 4.9 Assumptions to flag (S5-1 to S5-17, list with final values in `STAGE5_NOTES.md`)
S5-1 tie order; S5-2 `tauRelease` table; S5-3 `tauDigest` from local-midnight-aligned boundaries; S5-4 mode OFF skips simulation; S5-5 spec-literal exploration (hold → ALLOW only); S5-6 arm mapping; S5-7 `recheck` max age 30 s; S5-8 expiry defaults; S5-9 explanation ranking heuristic; S5-10 risk-aversion rule uses evidence strength; S5-11 strict session halves `kappaD` and disables exploration; S5-12 DR uses decision-time predictions, Hájek primary, truncation caps, B=1000, 20-record threshold; S5-13 estimand; S5-14 regret and saved-minute definitions; S5-15 importance comes from the 4B provider; S5-16 unknown `notifCat` is not never-hold; S5-17 latency target under 10 ms median and p95 on the dev JVM.

---

## 5. The prompts

### 5.1 Prompt 5-PRE: pre-flight (read-only; run on branch contract-5-0)

```text
Read AGENTS.md, docs/STAGE5_6_COMMON.md, docs/STAGE5_GUIDE.md (this guide), DHYAN_PROJECT_SPEC_v2.md sections 8.5, 9, 10, 17.2 Stage 5, 25.5 to 25.7, docs/TWIN_MODEL_CONTRACT.md, core-spec/, and the notes from Stages 3, 4A and 4B. Do not edit anything except creating STAGE5_NOTES.md.

Report with file paths:
1. Package root (expected com.dhyan.app), Kotlin and JDK versions, whether kotlinx.coroutines is on the test classpath. Do not edit Gradle.
2. The real Stage 3 API: TwinEngine (simulate signature and result types SimOutcome Ready/NotReady with every field of the Ready result: per-arm stats, delta stats, p_pickup interval and mean if present, evidence strength, modelVersion, provenance, hierarchy levels), ContextSnapshot (fields and enums), NotificationInput, ContextBucketer, TwinConfig and ModelConfig defaults, staleness handling.
3. The real Stage 4B importance model: class, function signature, inputs, whether it is pure and synchronous, and whether it reads text. If it does not exist or is not merged: say so clearly (STOP condition).
4. core-spec values: notifCat and appCat enums (list every value and say which ones correspond to call, alarm, navigation, message, social, promo, email, transport, delivery), default-params.json entries for kappa_d, lambda_miss, c_digest, c_rel, I_max, I_silent, epsilon, conf_min, c_glance, digest interval, W.
5. Existing tools/check-twin-purity.mjs and tools/check-twin-contract.mjs: how they work so the new scripts follow the same style.
6. Whether twin/decision/, twin/counterfactual/ or safety/ already exist and what is in them.
7. The 4A TruthRecord and RandomizedHold fields (for the estimator tests).
8. Anything in the common prompt's contract sketch that does not fit the real code.

Write STAGE5_NOTES.md with the findings, the proposed file list per prompt (5.0, 5A to 5E), and the assumptions S5-1..S5-17 from the guide with any you would change. Mark STOP CONDITIONS (missing importance model, missing schema field) as DECISION NEEDED and do not work around them. End with "STOP: waiting for approval." Do not write Kotlin yet.
```

### 5.2 Prompt 5.0: the contract commit (types, interfaces, two tiny implementations)

```text
Implement Prompt 5.0 on branch contract-5-0. Follow AGENTS.md. Create ONLY the files listed. This commit freezes the contract that Stage 6 builds against, so be exact and conservative.

Under phone/app/src/main/java/com/dhyan/app/twin/decision/ (adapt names to the real code found in 5-PRE):
- DecisionContract.kt: const val DECISION_CONTRACT_VERSION = 1; enum Action { ALLOW, DELAY, DIGEST, SILENT }; enum ActuationStatus { HELD, SNOOZED, SCHEDULED, RELEASED, EXPIRED, FAILED_OPEN, ACTUATION_UNKNOWN }; enum PolicyMode { OFF, DRY_RUN, LIVE }; enum KillSource { USER_TILE, USER_NOTIFICATION, USER_SETTINGS, SYSTEM }.
- NotificationFacts.kt: keyHash, postedTsMs, pkg, appCat, notifCat, senderHash?, isVip, urgencyScore, hasReplyAction, isMessagingStyle, repeatCount5m, isOtpLike, hasEmergencyKeyword. NO content fields. Document that isOtpLike and hasEmergencyKeyword are computed in memory by Stage 6 from text and never persisted.
- PolicyDefaults.kt and PolicySettings.kt: defaults mirroring core-spec/default-params.json (kappaD 3.0 per hour, lambdaMiss 5.0, cDigest 0.8, cRel 0.25, iMax 0.85, epsilon 0.05, confMin 0.4, digestIntervalMin 120, maxDecisionAgeMs 30000). PolicySettings: mode (default OFF), killSwitch, kappaDPerHour, explorationEnabled, epsilon, strictSession, allowListPackages, seed. Immutable.
- Decision.kt: Decision with these fields: action, explanation, factors (List<Factor(kind, text, contribution)>), holdMinutes?, propensity, eligibleActions, explored, safetyReason?, failedOpen, predictedLossShowMin?, predictedLossSuppressMin?, deltaLMean?, deltaLP10?, deltaLP90?, evidenceStrength?, provenance, modelVersion, policyVersion, decidedAtMs, AND keyHash and postedTsMs (identity for recheck). Add a computed pShow.
- DecisionRecord.kt: the complete log record, hashes and numbers only: keyHash, postedTsMs, decidedAtMs, action, propensity, pShow, eligibleActions, explored, safetyReason, failedOpen, predicted and delta fields, evidenceStrength, factors (kind + contribution only), snapshotSummary (focusState, taskType, hourBand, fatigueBand, laptopState, confidence), importance, modelVersion, policyVersion, mode, actuationStatus (nullable, set later by Stage 6), plus DecisionRecordFactory.from(facts, decision, snapshot, settings, importance) as a pure function.
- DecisionProvider.kt: interface DecisionProvider { decide(facts, snapshot, settings, nowMs): Decision; recheck(facts, decision, settings, nowMs): Recheck }; sealed interface Recheck { Proceed, Abort(reason) }; interface DecisionLogStore { suspend fun append(record: DecisionRecord) }. KDoc: decide never throws, is synchronous, does no I/O; any problem is ALLOW with failedOpen = true.
- AlwaysAllowDecisionProvider.kt: a safe default implementation that returns ALLOW with failedOpen = true and reason "no_provider", and recheck returns Abort("no_provider").
- DigestSchedule.kt: REAL implementation: fun nextDigestTsMs(nowMs: Long, intervalMin: Int = 120, zone: ZoneId = ZoneId.systemDefault()): Long, boundaries at multiples of intervalMin minutes from local midnight, strictly greater than nowMs, DST-safe. Validate intervalMin in 1..1440 (throw IllegalArgumentException for invalid, since this is a programmer error, not runtime data).
Under safety/:
- KillSwitchControl.kt: interface KillSwitchControl { val active: Boolean; val changedAtMs: Long; fun activate(source: KillSource, nowMs: Long); fun deactivate(nowMs: Long); fun restore(active: Boolean, changedAtMs: Long); fun addListener(l: KillSwitchListener); fun removeListener(l) }, interface KillSwitchListener { onActivated(source), onDeactivated() }, and InMemoryKillSwitch (thread-safe, idempotent: activating twice notifies once, listener exceptions do not stop other listeners and are counted not propagated).
- ExpiryPolicy.kt: interface ExpiryPolicy { fun expiresAtMs(facts: NotificationFacts, decidedAtMs: Long): Long? } (implementation comes in 5B).
Docs and tools:
- docs/DECISION_CONTRACT.md: every type, every field with units, the semantics of decide/recheck, fail-open rules, the arm mapping (SHOW = ALLOW, SUPPRESS = DELAY|DIGEST|SILENT), pShow derivation, the PolicyMode semantics (OFF, DRY_RUN, LIVE), the change protocol, and the list of deltas from the common prompt sketch: Decision gained keyHash and postedTsMs; DigestSchedule gained a zone parameter with a default; KillSwitchControl, KillSwitchListener, ExpiryPolicy, KillSource and AlwaysAllowDecisionProvider were added.
- tools/check-decision-purity.mjs: fails on android.*, androidx.*, java.io.File, java.net, Room, Math.random, Random.Default, System.currentTimeMillis, System.nanoTime, UUID.randomUUID in twin/decision, twin/counterfactual, safety. Self-test with a planted violation.
- tools/decision-contract.lock and tools/check-decision-contract.mjs: the lock holds the SHA-256 of each contract file listed above plus DECISION_CONTRACT_VERSION; the script fails if any listed file changed without the version and lock being updated, and also asserts PolicyDefaults equal core-spec/default-params.json values. Self-test with a planted change.
- root package.json: add ONE key "check:decision" running both scripts.

Tests (JVM, several fixed inputs): DigestSchedule (exactly at a boundary returns the NEXT one, just before, across midnight, DST spring-forward and fall-back in a named zone, 60-minute and 120-minute intervals, invalid interval); InMemoryKillSwitch (idempotence, restore, listeners, listener exception isolation, concurrency smoke test); AlwaysAllowDecisionProvider never throws and never returns a hold; Decision.pShow for each action; PolicyDefaults match core-spec.
Run: cd phone; .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.twin.decision.*" --tests "com.dhyan.app.safety.*"; npm run check:decision; npm run check:twin; npm run check:twin-contract. Paste real summary lines. Commit "stage5: 5.0 decision contract". State anything not run. Then STOP and tell me to review and merge to main before any worktree is created.
```

After you review and merge 5.0, create the Stage 5 and Stage 6 worktrees from that commit and paste the contract-change protocol into your own notes.

### 5.3 Prompt 5A: cost function, action selector, hold times

```text
Implement Stage 5 step 5A in the stage5-decision worktree. Follow AGENTS.md and section 4.2 to 4.4 of docs/STAGE5_GUIDE.md. Do not edit the frozen contract files.

Create in twin/decision/:
- CostFunction.kt: pure function computing J for ALLOW, DELAY, DIGEST, SILENT from (eShowMin, eSuppMin, importance, kappaDPerHour, strictSession, tauReleaseMin, tauDigestMin, constants from PolicyDefaults). Strict session multiplies kappaD by 0.5. Explicit /60.0 for every tau. Returns a CostBreakdown with each J and each term (so tests and the Explainer can read them). NaN or infinite input throws IllegalArgumentException internally; the pipeline (5C) converts that into fail-open.
- ActionSelector.kt: argmin with the tie-break order ALLOW, DELAY, DIGEST, SILENT and a 1e-9 tie tolerance; returns the winner, all J values and the margin to the runner-up. Deterministic.
- HoldTimeEstimator.kt: tauRelease table and tauDigest per section 4.4, using DigestSchedule and the snapshot; caps and clamps; all constants in one config object.
- Decision-time helpers: holdMinutesFor(action, tauRelease, tauDigest).
- docs/DECISION_CONTRACT.md is frozen: put the cost-function write-up (equations, units, boundaries, tie order, what J does not include) in a NEW file docs/DECISION_POLICY.md.

Tests (write expectations from the formulas independently of the code under test; never copy numbers out of the implementation):
- Each action wins in a defined regime, with the inputs and the formula-derived J values in the test: ALLOW when I is high or deltaL tiny; SILENT for I below 0.16 with large deltaL; DIGEST for moderate I with a far digest; DELAY for moderate-high I, small E_show and a near release. Find the regimes by reasoning on the formulas, document them in the test names.
- Boundary tests at I = 0.16 (SILENT vs DIGEST flips exactly there with the defaults), I just below and above, ties and tie-break, tau/60 unit test (tau of 60 minutes with kappaD 3.0 and I = 1 adds exactly 3.0), strict session halves the delay term, E_supp = 0, E_show = E_supp, large values, NaN and infinity rejected.
- Property test over many fixed-seed random inputs: the selected action's J is the minimum; ALLOW is chosen whenever deltaL <= 0 and I >= 0.16 (derive and prove why in a comment, or drop the assertion if it does not hold).
- HoldTimeEstimator: each snapshot case, cap at tauDigest, tauDigest clamp when the digest is seconds away.
Run the decision tests and npm run check:decision. Paste real summary lines. Commit "stage5: 5A cost function and selector". State what is not verified.
```

### 5.4 Prompt 5B: safety module

```text
Implement Stage 5 step 5B. Follow AGENTS.md and section 4.6. Frozen contract files must not change.

Create in safety/ (pure Kotlin):
- NeverHoldRules.kt: check(facts, settings): String? returning a fixed reason code (call, alarm, navigation, otp, emergency, vip, allowlist) or null. Use the real core-spec notifCat values found in 5-PRE and document the mapping in docs/DECISION_POLICY.md. Unknown notifCat values return null but increment a diagnostics counter.
- FailOpen.kt: inline helper that runs a block and converts any Exception into a fail-open Decision with reason "exception:<SimpleClassName>" and NO message text. Also handles the NotReady case. DecisionDiagnostics.kt: atomic counters per reason (exception, invalid_input, not_ready, unknown_notif_cat, recheck_abort_*), snapshot() for the UI; counters hold numbers only.
- DefaultExpiryPolicy.kt implementing ExpiryPolicy: OTP-like 10 minutes, everything else 24 hours.
- RecheckLogic.kt: pure function implementing the recheck rules in section 4.6 (mode, kill switch, ALLOW or failedOpen decision, never-hold now, identity by keyHash and postedTsMs, age over maxDecisionAgeMs, negative age).
Do not build the DecisionPipeline yet.

Tests: each never-hold rule, each in combination with every PolicyMode and with kill switch on and off; every rule beats a notification whose predicted loss is huge (pass the rule check directly, independent of the pipeline); unknown category counter; FailOpen with a throwing block never leaks the exception message (assert the message string is absent from the Decision and diagnostics); each recheck abort path and the Proceed path; recheck with a different keyHash or postedTsMs aborts; a decision older than 30 s aborts; clock going backwards aborts; DefaultExpiryPolicy cases.
Safety matrix (spec 25.5), write as a test class SafetyMatrixTest with one test per row that Stage 5 can cover purely: known urgent examples, ambiguous facts, classifier/importance exception, stale context, invalid settings, duplicate or reposted notification (identity mismatch), kill-switch activation. For each row Stage 5 cannot cover (low battery or background restrictions, process restart, reboot, storage failure, scheduler failure, no network), add an explicit @Ignore-free list in STAGE5_NOTES.md under "Safety matrix rows handed to Stage 6" with the acceptance test Stage 6 must write.
Run the safety tests and npm run check:decision. Paste real summary lines. Commit "stage5: 5B safety". State what is not verified.
```

### 5.5 Prompt 5C: explainer, exploration, pipeline

```text
Implement Stage 5 step 5C. Follow AGENTS.md and sections 4.1, 4.5, 4.7. The frozen contract must not change.

Create in twin/decision/:
- LossPredictor.kt: interface LossPredictor { fun predict(snapshot, facts): LossPrediction } with sealed LossPrediction { Ready(eShowMin, eSuppMin, deltaP10, deltaP90, pPickupMean, evidenceStrength, modelVersion, provenance), NotReady(reasonCode) } and TwinEngineLossPredictor adapting the REAL Stage 3 TwinEngine (map facts to its NotificationInput via the real ContextBucketer; do not edit twin/). Map evidenceStrength, provenance and modelVersion from the engine result. A stub implementation for tests that returns controlled numbers.
- ImportanceProvider.kt: interface ImportanceProvider { fun score(facts): Double } with an adapter to the real Stage 4B importance model found in 5-PRE (read-only use). If the 4B API takes inputs not present in NotificationFacts, report it as a cross-stage request instead of inventing values.
- ExplorationPolicy.kt: eligibility and draw per section 4.5, seeded splitmix64 stream from (settings.seed, keyHash, postedTsMs), returns (action, explored, propensity, eligibleActions). Deterministic and idempotent per notification.
- Explainer.kt: section 4.7 templates and ranking; deterministic rounding; no app names or senders; allow-reason texts.
- DecisionPipeline.kt implementing DecisionProvider exactly per section 4.1, wrapped in FailOpen, with injected LossPredictor, ImportanceProvider, KillSwitchControl-independent settings (kill switch comes via settings.killSwitch), HoldTimeEstimator, ExplorationPolicy, Explainer, DecisionDiagnostics. recheck delegates to RecheckLogic. policyVersion is a constant string derived from DECISION_CONTRACT_VERSION plus a POLICY_REVISION constant you define and document. decide must not allocate large collections on the hot path (use preallocated arrays or small fixed objects where easy) and must not read the clock; use nowMs.

Tests (stub predictor unless noted): pipeline order (each override short-circuits and later stages are not called; use a predictor that fails the test if called); never-hold wins even with a huge predicted loss and with exploration enabled; every PolicyMode and the kill switch; NotReady, NaN settings, empty keyHash, throwing predictor, throwing importance provider all yield ALLOW with failedOpen = true and no exception text; I = 0.85 boundary; low-evidence rule at I = 0.5 boundary (> 0.5 triggers, = 0.5 does not); strict session lowers kappaD and disables exploration; exploration: eligibility matrix (each condition individually false makes it ineligible), idempotence (same notification, same result, 1000 calls), different notifications are independent, empirical explore rate over many fixed-seed notifications within 4 standard errors of epsilon, propensity values 1 - epsilon, epsilon, 1.0 exactly, eligibleActions contains only what was possible, pShow derivation; explainer outputs for each factor combination, missing data skipped, no digits-as-text leaks of keyHash or senderHash (assert the strings do not contain facts.keyHash or senderHash); determinism (same inputs twice give an equal Decision); a hold decision carries holdMinutes, an ALLOW does not.
Also add an integration-style test with the REAL TwinEngine and a cold ParamStore (priors only): a low-importance social notification during coding returns a valid Decision with evidence strength reported low, never throws, and a store that is not loaded returns ALLOW failedOpen.
Latency smoke test (prints; asserts only median and p95 under 10 ms on the dev JVM after warm-up; the number is NOT a Reno 14 result).
Run the decision tests and npm run check:decision. Paste real summary lines and the printed latency numbers. Commit "stage5: 5C pipeline". State what is not verified.
```

### 5.6 Prompt 5D: counterfactual estimators, regret, saved minutes

```text
Implement Stage 5 step 5D in twin/counterfactual/. Follow AGENTS.md and section 4.8. Pure Kotlin.

Create:
- ExplorationRecord.kt, Arm { SHOW, SUPPRESS }, EstimateStatus { MODEL_BASED_ONLY, ESTIMATED }, FilterCounts, CounterfactualReport (per bucket: status, nTotal, nUsed, counts per filter, ipwDelta, htDelta, drDelta, bootstrap p10 and p90 of each as "80% bootstrap interval", ess per arm, min and max pShow, top-5% weight share, truncation sensitivity for caps none/20/10/5, model-mean deltaL, label per number: simulated | estimated | observed).
- IpwEstimator.kt (Hajek normalized and plain Horvitz-Thompson), DoublyRobustEstimator.kt, Bootstrap.kt (seeded splitmix64, B = 1000 default, percentile method, deterministic), Diagnostics.kt (ESS, overlap), CounterfactualEngine.kt (filters, groups by a caller-supplied bucket key function, applies the 20-record threshold, produces the report).
- Regret.kt (modelRegretMin per decision, explorationCostMin) and SavedMinutes.kt (modelSavedMin over decisions with confirmed actuation status, delayCostMin, estimatedSavedMin from DR; three separate numbers).
- docs/DECISION_POLICY.md: add "Counterfactual estimation" with the estimand, filters, equations, assumptions (no hidden confounders beyond logged context; stationarity within a bucket; suppression does not change future behavior), what is NOT identified (never-hold, VIP, strict-session, high-importance contexts), and the label rules.

Tests (all seeded, several fixed seeds):
1. Algebra: tiny hand-computed examples for IPW, HT and DR (write the expected numbers in the test from hand arithmetic).
2. Unbiasedness on synthetic data built inside the test: known true effect, known propensity pShow = 0.2, outcome noise; IPW and DR within 4 standard errors of truth over many replications.
3. Double robustness: (a) correct propensity, deliberately biased model predictions (mu-hat = 1.3 * truth) → DR still near truth; (b) wrong propensity, correct model → DR near truth while IPW is visibly biased; both wrong → report is biased and the test documents it (do not assert closeness).
4. Positivity and filters: records with pShow of 0 or 1, censored, actuation failed, not eligible and NaN are excluded and counted; fewer than 20 usable records gives MODEL_BASED_ONLY with only the model mean; exactly 20 gives ESTIMATED.
5. Truncation sensitivity changes weights as expected and ESS decreases when weights are extreme.
6. Bootstrap determinism (same seed same interval) and coverage sanity (80% interval covers truth roughly 80% of replications within a wide tolerance; if flaky, loosen and write down why).
7. Ground truth comparison using Stage 4A (test-scope import of personas.*, read-only): generate Rahul and Kabir with RandomizedHold(eps = 0.2) and MATCHED world, convert truth records to ExplorationRecords (Y from the realized arm, pShow from the logged propensity, mu-hat from a deliberately imperfect predictor), and compare the DR and IPW estimates to TrueEffect for the same buckets. Print a table of estimate vs truth with intervals. Assert only that the truth lies inside a generous interval in at least most buckets; do not tune anything to pass.
8. Regret and saved minutes: hand-computed examples; unconfirmed actuations are not counted; the three numbers stay separate.
Run decision and counterfactual tests and npm run check:decision. Paste real summary lines and the printed truth-comparison table. Commit "stage5: 5D counterfactuals". State what is not verified.
```

### 5.7 Prompt 5E: verdict sheets, urgent suite, gate record

```text
Close Stage 5. Follow AGENTS.md. Only tests, STAGE5_NOTES.md, docs/DECISION_POLICY.md and fixes within the owned paths may change. The frozen contract must not change.

1. VerdictSheetTest (decision-table layer, stub LossPredictor so every number is controlled): ten named notifications, expected action written BEFORE running by reasoning from the formulas, and the test fails if the pipeline disagrees. Include at least: a call (never-hold), an OTP, a VIP message ("Mom, urgent"), a high-importance non-VIP (I >= 0.85), a low-importance social like during deep coding with large deltaL (expect DIGEST), a promo with I < 0.16 and large deltaL (expect SILENT), a moderate-importance message with a near release and small E_show (expect DELAY), a moderate-importance item for a self-distraction-dominated persona with tiny deltaL (expect ALLOW), a low-evidence mid-importance item (expect ALLOW by risk aversion), and a failed-open case (stale snapshot). Each of SILENT, DIGEST, DELAY and ALLOW appears at least once; no never-hold item is ever a hold. Print the sheet as a table: id, description, I, E_show, E_supp, deltaL, J values, action, reason, explanation.
2. EndToEndSheetTest (real TwinEngine): a cold store (priors only) and a store trained by replaying a 4A persona history through the Stage 4B learning path for Rahul and for Kabir. Print the decisions for the same set of notification facts under each. Assert only invariants (never-hold, fail-open, SUPPRESS predicted loss not above SHOW for the notification-prone persona, evidence strength higher after training); do NOT assert pitch numbers and do not tune anything.
3. UrgentSuiteTest: a named suite of at least 40 urgent facts (calls, alarms, OTPs, VIPs, navigation, emergency keywords, allow-list packages) in many variants, run across a grid of configurations: modes OFF/DRY_RUN/LIVE, kill switch on/off, strict session on/off, exploration on/off, extreme kappaD values, several seeds, with stub predictors that make holding very attractive. Assert zero holds and print: "urgent suite: <N> cases x <M> configs = <N*M> decisions, holds = 0". This is a measured result on this suite only; say so in the output and the notes.
4. DecisionLatencyBenchmark: warm-up, then print median and p95 of decide() with the real TwinEngine over at least 2000 calls. Assert both under 10 ms on the dev JVM. State in the notes that this is NOT a Reno 14 result and an on-device check is a Stage 6 task.
5. Run: cd phone; .\gradlew.bat :app:testDebugUnitTest (full suite); npm run check:decision; npm run check:twin; npm run check:twin-contract; npm run check:personas. Paste the real summary lines and every printed table into STAGE5_NOTES.md.
6. STAGE5_NOTES.md must contain: status ledger (not_started | in_progress | implemented | tested | blocked, tested only with pasted evidence); assumptions S5-1..S5-17 with final values and an approval column; commands run with real output; the printed verdict sheet, urgent suite line, truth-comparison table and latency numbers; "not verified on target"; "Safety matrix rows handed to Stage 6"; "Cross-stage requests" (for example, anything needed from NotifExtractor, the snapshot source or the learning feedback sink); "Contract change requests" (empty if none); and "Inputs for Stage 6": the final constructor of DecisionPipeline, how to build PolicySettings, how to build ExplorationRecords from decisions and episodes later, DecisionRecordFactory usage, and the list of fixed reason codes.
7. Do not edit governance docs. List what I must fold into TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md and DECISIONS_PENDING.md after the merge.
Commit "stage5: gate record".
```

---

## 6. Gate 5 procedure (you run it, not the agent)

1. From the Stage 5 worktree:
   ```
   cd phone
   .\gradlew.bat :app:testDebugUnitTest
   cd ..
   npm run check:decision
   npm run check:twin
   npm run check:twin-contract
   npm run check:personas
   ```
   Expect all green. Paste the real summary lines into `STAGE5_NOTES.md`.
2. Read the printed **verdict sheet**. Check each row against your own judgment: calls, OTPs and VIPs allowed; a social like during deep coding held; a promo silenced; a moderate-importance item delayed only when a break is near. If a verdict surprises you, read the J values before blaming the code. **Do not tune constants to make the sheet look nice.**
3. Read the **urgent suite line** and note the suite size. It is a measured result on that suite, nothing more.
4. Read the **truth-comparison table**: DR and IPW estimates against the 4A ground truth. Wide intervals and `MODEL_BASED_ONLY` buckets are honest results, not failures.
5. Read the **latency numbers**: median and p95 under 10 ms on your laptop JVM. The Reno 14 check happens in Stage 6.
6. Confirm `git diff --name-only <phase0-commit>...stage5-decision` shows nothing outside the owned paths and that the contract files and `tools/decision-contract.lock` are unchanged.

## 7. Review prompt (read-only, fresh session, after 5E)

```text
Read-only audit of branch stage5-decision against DHYAN_PROJECT_SPEC_v2.md sections 9, 10, 25.5, 25.6, 25.7, docs/STAGE5_6_COMMON.md and docs/STAGE5_GUIDE.md section 4. Do not edit files. Produce findings ordered by severity with file:line, the rule broken and a suggested fix. Check:
1. Any path where decide() can throw, return a hold on bad input, or leak an exception message or content into a Decision, factor text or diagnostics.
2. Never-hold: is it checked before cost, importance and exploration, and again in recheck? Can any setting or mode bypass it?
3. Cost function: every tau divided by 60, kappaD per hour, strict session handling, the I = 0.16 boundary, tie order, J uses means only.
4. Exploration: eligibility conditions, determinism and idempotence, exact propensities after safety filtering, no exploration on VIP, never-hold, high importance, strict sessions or failed-open.
5. Estimators: Hajek vs HT, DR formula, filters counted, positivity handled, ESS, truncation sensitivity, bootstrap labelled as an 80% bootstrap interval, no causal claim outside the identified contexts, labels simulated/estimated/observed kept apart.
6. Honest wording: "80% simulation interval", "evidence strength", no "confidence" as probability, "urgent delayed = 0" only as a measured suite result.
7. Determinism and purity: HashMap iteration, clocks, random sources, android imports.
8. Contract: files unchanged since 5.0, lock file consistent, no new public API that Stage 6 would depend on that is not in the contract.
9. Spec traceability: each Stage 5 bullet in 17.2 marked implemented / tested / missing with evidence.
10. Assumptions S5-1..S5-17 all listed in STAGE5_NOTES.md.
End with "Verified by running" vs "Verified by reading only", and state that nothing was verified on a device.
```

## 8. Resume prompt (new session, quota ran out, or drift)

```text
Resume STAGE 5 of Dhyan on branch stage5-decision. Read AGENTS.md, docs/STAGE5_6_COMMON.md, docs/STAGE5_GUIDE.md and STAGE5_NOTES.md, then run git log --oneline -15 and git status. Report which prompts (5A to 5E) are committed and what is uncommitted. Do not redo committed work. Re-read the Stage 5 rules in AGENTS.md (pure Kotlin, fail open, never-hold absolute, frozen contract, no edits outside owned paths). Correction from me: <ONE LINE>. Continue from the first unfinished prompt and report in the standard format with real command output.
```

## 9. Merge and handoff to Stage 6

- **Merge order:** contract (already on `main`) → `stage5-decision` → Stage 6. Run all checks and the full unit tests again after the merge.
- Root `package.json`: keep every earlier `check:*` key plus `check:decision`.
- Send the **"Inputs for Stage 6"** section of `STAGE5_NOTES.md` to the Stage 6 session as a `CROSS-STAGE NOTE`, with the final contract version.
- Fold `STAGE5_NOTES.md` into the governance docs yourself.
- **Integration TODOs that remain after Stage 5:** call the real `DecisionPipeline` from the notification path (Stage 6 M6); implement `DecisionLogStore` in Room (Stage 6 M1); supply `isOtpLike` and `hasEmergencyKeyword` (D56-1); phone-only snapshot source until Stage 8 fusion; map real decisions plus episodes into `ExplorationRecord` for Insights (Stage 10); on-device latency check on the Reno 14.

## 10. When things go wrong
| Problem | What to do |
|---|---|
| Codex edits the frozen contract | `git checkout -- <file>`, run `npm run check:decision` (it should fail), paste the resume prompt naming the violation |
| 4B importance model missing | Stop. Merge 4B first; do not let Codex invent an importance function |
| A verdict surprises you | Read the J values and the regime logic first; change the policy doc and tests together if you decide the policy is wrong, never just the constants |
| Estimator test is flaky | Check seeds, replication counts and weights first; loosen tolerance only with a written reason |
| Codex says all tests pass with no output | Do not accept it; run the command yourself |
| Latency over 10 ms | Look for allocation in `decide()`, repeated sorting, string building before the decision is final, and logging inside the pipeline |
| Contract is insufficient | Write a contract change request, then a 5.0b prompt on a new branch; bump the version; both stages rebase |