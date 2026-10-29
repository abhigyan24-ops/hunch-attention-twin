# DHYAN: Stages 5 and 6, common prompt for Codex and Antigravity

Commit this file to `main` as `docs/STAGE5_6_COMMON.md` **before** you create either worktree. Every Stage 5 and Stage 6 session, in either tool, starts with: *"Read `AGENTS.md`, then `docs/STAGE5_6_COMMON.md`, then the stage prompt below."*

Spec sources: 9 (decision engine and safety), 10 (counterfactuals), 11.5 (actuator), 11.6 (UI), 17.2 Stages 5 and 6, 17.3 (gates), 25.5, 25.6, 25.7, 25.10, 25.13, 25.14.

**Two agents, two tools, one codebase, no shared memory.** Codex builds Stage 5 and Antigravity builds Stage 6. They cannot see each other's worktrees or chat. The human (you) is the only link between them. This document is what keeps them from colliding, so it is binding on both.

---

## 1. Project in one paragraph (both agents)

Dhyan is a cross-device Attention Digital Twin. The phone app (Kotlin and Compose, tested on an Oppo Reno 14) watches notifications, app usage, screen events and scroll counts as categorized events, never content. A pure-Kotlin twin engine simulates two futures for each incoming notification (SHOW vs SUPPRESS), then a decision layer chooses `ALLOW`, `DELAY`, `DIGEST` or `SILENT`, with a one-line explanation. An actuator then holds or snoozes the notification and shows held items in a digest. Stages 1 to 4 are merged: schema, relay skeleton, collectors and Room, twin core (`ParamStore`, `PairedMonteCarlo`, `TwinEngine`), personas, replay, episodes and learning. **Stage 5 = the decision brain (pure Kotlin). Stage 6 = the hands and face (Android actuator and the 3-tab UI).**

---

## 2. Division of work

### Stage 5 (Codex): "Decision, safety and counterfactuals", pure Kotlin, JVM tests only
| Block | Contents | Spec |
|---|---|---|
| 5.0 | **The contract commit** (section 4 below): types and interfaces only | 25.9 |
| 5A | `CostFunction`, `ActionSelector`, `DigestSchedule`, hold-time estimates | 9.2, 25.6 |
| 5B | Safety: `NeverHoldRules`, `KillSwitch` state machine, fail-open wrapper, expiry policy, pre-actuation `recheck` | 9.5, 25.5 |
| 5C | `Explainer`, `ExplorationPolicy` with exact propensities, `DecisionPipeline` implementing `DecisionProvider` | 9.1, 9.3, 9.4 |
| 5D | IPW, doubly robust, regret, saved minutes, overlap diagnostics (`counterfactual/`) | 10.2, 25.7 |
| 5E | 10-notification verdict sheet, safety test matrix, gate record | 17.2 Stage 5 |

Stage 5 **decides**. It never touches a notification, a screen, a database or the clock.

### Stage 6 (Antigravity): "Actuator and 3-tab UI", Android
| Block | Contents | Spec |
|---|---|---|
| M0 | Pre-flight and plan (stop) | 25.12 |
| M1 | Room migrations (`held_notifs`, `decisions`, `feedback`), Room `DecisionLogStore`, held-text encryption | 11.7 |
| M2 | Actuator: snooze-first and cancel+digest, `DigestBuilder`, `ReleaseScheduler`, Undo / Show-now, status tracking, kill-switch effects (stop) | 11.5, 25.10 |
| M3 | Tab 2 Live & Digest | 11.6 |
| M4 | Tab 1 Simulate & Lab (uses real `TwinEngine`, no dependency on Stage 5 output) | 11.6 |
| M5 | Tab 3 Insights scaffold, Persona & Settings sheet (stop) | 11.6 |
| M6 | **Integration** with the real Stage 5 pipeline, compatibility matrix, Gate 2 record | 17.2 Stage 6 |

Stage 6 **acts and shows**. It never computes costs, importance, safety verdicts, exploration or estimators. It calls Stage 5 for every one of those.

### Where the lines are fuzzy, and who owns them
| Thing | Owner | Why |
|---|---|---|
| Digest schedule (when the next digest is due) | **Stage 5** (`DigestSchedule`) | Decision cost uses it; the `ReleaseScheduler` must call the same function so they never disagree |
| Never-hold rules and `recheck()` | **Stage 5** | One source of safety truth |
| Kill-switch **state** and rules | **Stage 5** | Pure state machine |
| Kill-switch **effects** (release held items, cancel jobs, persist after reboot) | **Stage 6** | Needs Android |
| Mechanism choice (snooze vs cancel+digest) | **Stage 6** | Device/app specific (11.5); Stage 5 only supplies the action and hold minutes |
| Importance score `I` | **Stage 4B code, consumed by Stage 5** | Stage 5 does not rewrite it. If missing, report it, do not invent it |
| Policy settings values (caution slider, exploration toggle, strict session) | Stage 6 holds the UI and storage; Stage 5 defines `PolicySettings` and defaults | Defaults come from `core-spec/default-params.json` |
| Provenance labels in the UI (`simulated`, `estimated`, `observed`, `low evidence`) | **Stage 6** renders, **Stage 5** supplies the fields | 25.13 |

---

## 3. File ownership (hard boundaries)

| Path (under `phone/app/src/.../com/dhyan/app/` unless noted) | Stage 5 (Codex) | Stage 6 (Antigravity) |
|---|---|---|
| `twin/decision/**`, `twin/counterfactual/**` and their tests | **owns** | read-only |
| `safety/**` and tests | **owns** | read-only |
| `twin/` everything else (params, simulator, engine, context) | read-only | read-only |
| `actuator/**` and tests | forbidden | **owns** |
| `ui/**` (except onboarding internals) and tests | forbidden | **owns** |
| `data/**` (new entities, DAOs, migrations, Room `DecisionLogStore`) | forbidden | **owns** (additive only, real Migrations) |
| `collectors/NotifCollector` (call into decision + actuator) | forbidden | **owns, M6 only** |
| `ingest/NotifExtractor.kt` (add text-derived boolean flags, decision D56-1) | forbidden | **owns, M6 only, after approval** |
| DI modules, `AndroidManifest.xml`, Gradle files | forbidden | **owns** (minimum edits, listed in notes) |
| `core-spec/` | forbidden (read-only) | forbidden (read-only) |
| `personas/`, `sync/`, `fusion/`, `inference/`, `relay/`, `laptop/` | forbidden | forbidden |
| `tools/` | `check-decision-purity.mjs`, `check-decision-contract.mjs` | `check-actuator-contract.mjs`; **and** the narrow update to `check-phone-contract.mjs` (section 6, rule 3) |
| root `package.json` | one key: `check:decision` | one key: `check:actuator` |
| Docs | `docs/DECISION_CONTRACT.md`, `STAGE5_NOTES.md` | `docs/ACTUATOR_CONTRACT.md`, `STAGE6_NOTES.md` |
| Governance docs (`TRACEABILITY.md`, `HANDOFF_STATUS.md`, `STAGE_GATES.md`, `DECISIONS_PENDING.md`) | **forbidden, report in notes** | **forbidden, report in notes** |

If you need to change something you do not own: **stop, write it under "Cross-stage requests" in your notes file, and tell the human.** Never "just fix it".

---

## 4. The decision contract (frozen by Prompt 5.0)

Prompt 5.0 (Codex, pure Kotlin, types and interfaces plus trivial stubs only) creates these under `twin/decision/` and `safety/`, and commits to `main` **before either stage worktree exists**. After that commit the contract is **frozen**. Names below are the intended shape; 5.0 may adjust spelling, but nothing after 5.0 may.

```kotlin
const val DECISION_CONTRACT_VERSION = 1

enum class Action { ALLOW, DELAY, DIGEST, SILENT }

enum class ActuationStatus {            // spec 25.10; UI must distinguish all of these
    HELD, SNOOZED, SCHEDULED, RELEASED, EXPIRED, FAILED_OPEN, ACTUATION_UNKNOWN
}

enum class PolicyMode { OFF, DRY_RUN, LIVE }   // default OFF; DRY_RUN decides and logs but never actuates

// Pure facts about one notification. NEVER contains title, text or any content.
data class NotificationFacts(
    val keyHash: String,                // HMAC'd key from Stage 2 (S2-2)
    val postedTsMs: Long,
    val pkg: String, val appCat: String, val notifCat: String,
    val senderHash: String?, val isVip: Boolean,
    val urgencyScore: Double,           // [0,1]
    val hasReplyAction: Boolean, val isMessagingStyle: Boolean,
    val repeatCount5m: Int,
    val isOtpLike: Boolean, val hasEmergencyKeyword: Boolean   // booleans computed in memory by NotifExtractor; see D56-1
)

data class PolicySettings(              // immutable snapshot; Stage 6 supplies it, Stage 5 defines defaults
    val mode: PolicyMode = PolicyMode.OFF,
    val killSwitch: Boolean = false,
    val kappaDPerHour: Double,          // caution slider; default from core-spec/default-params.json
    val explorationEnabled: Boolean, val epsilon: Double,
    val strictSession: Boolean, val allowListPackages: Set<String>,
    val seed: Long
)

data class Factor(val kind: String, val text: String, val contribution: Double)   // text is template output, no user content

data class Decision(
    val action: Action,
    val explanation: String,
    val factors: List<Factor>,
    val holdMinutes: Double?,           // τ the actuator should honor (DELAY/DIGEST/SILENT)
    val propensity: Double,             // exact π over the eligible set AFTER safety filtering
    val eligibleActions: Set<Action>,
    val explored: Boolean,
    val safetyReason: String?,          // non-null when a safety rule forced ALLOW
    val failedOpen: Boolean,            // true when anything was missing, stale or threw
    val predictedLossShowMin: Double?, val predictedLossSuppressMin: Double?,
    val deltaLMean: Double?, val deltaLP10: Double?, val deltaLP90: Double?,
    val evidenceStrength: Double?, val provenance: String,   // "simulated" | "low evidence" | ...
    val modelVersion: Long, val policyVersion: String, val decidedAtMs: Long
)

interface DecisionProvider {
    /** Synchronous, allocation-light, NEVER throws, never blocks on I/O. Any problem => ALLOW with failedOpen = true. */
    fun decide(facts: NotificationFacts, snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long): Decision

    /** Call immediately before actuating. Re-checks never-hold rules, kill switch, mode, age and identity. */
    fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck
}
sealed interface Recheck { object Proceed : Recheck; data class Abort(val reason: String) : Recheck }

interface DecisionLogStore {            // Stage 5 calls nothing here; Stage 6 implements it with Room
    suspend fun append(record: DecisionRecord)
}
data class DecisionRecord(/* facts hashes + Decision + snapshot summary + actuation status + exploration flag */)

object DigestSchedule {                 // shared by cost function (Stage 5) and ReleaseScheduler (Stage 6)
    fun nextDigestTsMs(nowMs: Long, intervalMin: Int = 120): Long
}
```

**Contract rules**
1. After 5.0, neither agent edits these files. Both build against them.
2. Stage 5 supplies the real `DecisionProvider`. Until it is merged, Stage 6 uses a `FakeDecisionProvider` that lives in **Stage 6's test or debug source set**, deterministic and obviously labelled fake, returning every `Action`, an explored decision, a failed-open decision and a safety override, so every UI and actuator path can be built and tried.
3. **Change protocol:** if either agent finds the contract wrong or insufficient, it stops that part, writes a "Contract change request" (what, why, which tests break, whether it is additive) in its notes, and the human decides. Approved changes land as a new contract commit on `main` (Prompt 5.0b, Codex), bump `DECISION_CONTRACT_VERSION`, and **both agents rebase before continuing**. Prefer additive changes (new field with a default).
4. `DecisionRecord` and `Decision` carry hashes and numbers only. Never text.

---

## 5. Phases, branches and merge order

| Phase | Who | What |
|---|---|---|
| **0** | You + Codex | Commit this file as `docs/STAGE5_6_COMMON.md`, then run Prompt 5.0 on a short branch, review it, merge to `main` |
| **1** | Codex (Stage 5) and Antigravity (Stage 6 M0 to M5) in parallel | Worktrees `..\dhyan-s5` (branch `stage5-decision`) and `..\dhyan-s6` (branch `stage6-actuator`), both created **from the Phase 0 commit** |
| **2** | You | Merge `stage5-decision` to `main`; rerun all checks and the full tests |
| **3** | Antigravity (Stage 6 M6) | Rebase `stage6-actuator` onto `main`; swap `FakeDecisionProvider` for the real pipeline; integration; device Gate 2 |

- **Merge order is fixed: contract, then Stage 5, then Stage 6.** Stage 6 never merges before Stage 5.
- Neither agent pushes. Neither touches `main`. Commits are per milestone or prompt (`stage5: 5A cost function`, `stage6: M2 actuator`).
- **If you are short on time, run the phases back to back, not in parallel.** You are the review bottleneck.
- Stage 6 M0 may start immediately after Phase 0. Stage 6 must not start M6 until Phase 2 is complete.
- Worktree extras git does not carry: copy `phone\local.properties`, run `npm install`, use JDK 17, run `.\gradlew.bat :app:testDebugUnitTest` once before the agent starts.

---

## 6. Rules binding on both agents

### 6.1 Safety invariants (spec 9.5, 25.5, 25.10)
1. **Fail open.** Missing, stale, malformed or throwing anything (classifier, context, model, settings, scheduler, storage) means the notification is **not newly suppressed**. `decide()` never throws; the actuator never holds on a `failedOpen` decision.
2. **Never-hold is absolute.** Calls, alarms, OTP/transaction codes, VIP contacts, navigation, emergency keywords and the allow-list bypass the cost function **and** exploration. Stage 5 checks them in `decide()` and again in `recheck()`.
3. **Stage 6 calls `recheck()` immediately before every hold, snooze or cancel**, and aborts on `Abort`. A decision is also void if the notification key hash or posted time does not match the live notification (age limit in `recheck`).
4. **Kill switch:** stops new holds immediately, releases or restores held items where technically possible, cancels pending jobs, shows a verifiable state, and survives process death and reboot. State logic is Stage 5, effects are Stage 6.
5. **Status honesty (25.10):** the UI shows `HELD` or `SNOOZED` only after the actuator has a **confirmed result** (for example the platform call returned and the original notification was observed removed). A calculated decision is not a hold. Unknown outcomes are `ACTUATION_UNKNOWN`, and if restoration is uncertain the notification is surfaced, not silently kept.
6. **Never silently discard a held notification.** If the scheduler, database or process fails, prefer releasing or surfacing it.
7. **No promise of identical behaviour across apps.** Stage 6 keeps a device/app compatibility matrix (device, ColorOS/Android version, app and version, mechanism, observed result). Anything not tested on the Reno 14 is labelled **not verified on target**.

### 6.2 Policy mode (new, important)
`PolicyMode` defaults to **`OFF`**. `DRY_RUN` runs the whole pipeline, writes decision logs and shows "would have held" in the UI, but calls **no** cancel or snooze. `LIVE` actuates. The first real-device test of Stage 6 is `DRY_RUN` on your real notifications, then `LIVE` on Dhyan's **own test notifications only** (the cancel+digest path), and only after that on third-party apps, one app at a time.

### 6.3 Relaxation of Stage 2's observe-only rule, narrowly
Stage 2's `check-phone-contract.mjs` forbids `cancelNotification` and `snoozeNotification` everywhere. Stage 6 may update that script so those calls are allowed **only in `actuator/**`**, and only reachable when `mode == LIVE`. Stage 6 must add a self-test showing the script still fails on a planted violation outside `actuator/`. Every other Stage 2 privacy rule stays exactly as it is: no text in logs, events, exceptions or payloads; `NotifExtractor` as the only reader of text; accessibility reads no content.

### 6.4 Privacy (spec 6.5, 25.8)
- Held notifications are the **one** place text may be stored (`held_notifs`). It must be app-private, short-lived, deleted after release or expiry, and **encrypted at rest** (decision D56-2). Nothing else stores text. Decision logs, events, UI debug views and logs carry hashes, enums and numbers only.
- After Stage 6, repeat the **canary test** (Stage 2 Part 6): the canary must appear **only** in `held_notifs` (encrypted, so not even there as plain bytes) and nowhere else.
- Never send real notification text to any cloud tool. Never paste it into prompts.

### 6.5 Determinism, units, honesty
- Decisions are deterministic for the same facts, snapshot, settings, model version and seed. Randomness (exploration) comes from an injected seeded RNG, and the seed is logged. No `Math.random`, `kotlin.random.Random.Default`, wall-clock or `System.nanoTime` for anything that affects a decision. Time is passed in as `nowMs`.
- Units: durations in minutes, hold times in minutes, `κ_d` per hour. Name variables with units. `τ/60` conversions are explicit and tested.
- Constants have one home: `core-spec/default-params.json` and `PolicySettings` defaults. Stage 6 sliders write settings; nobody duplicates numbers.
- **Never hard-code a result to match the pitch** (24 min, 0.62, 0.09, "urgent delayed = 0"). Headline numbers come from a named, seeded fixture visible in the UI or notes.
- **Honest labels:** "80% simulation interval" (not credible interval), "evidence strength" (not confidence or probability), and provenance labels `observed | learned | simulated | estimated | low evidence` (25.13). Model simulation, exploration-supported estimate and observed outcome are three different things.
- "Urgent delayed = 0" is a **measured result on a defined suite** (state suite size and pass/fail), never a universal guarantee.
- Never claim a test passed without running it and pasting the real summary line. Never claim anything about the Reno 14, ColorOS, WhatsApp, Instagram or the relay without a pasted result from that target. Otherwise write "not verified on target".

### 6.6 Real data stays
Never uninstall the app, clear its data, change `applicationId` or signing, or use `fallbackToDestructiveMigration`. New tables arrive only through real Room Migrations with exported schema JSON committed. (Applies to Stage 6; Stage 5 has no database.)

### 6.7 Performance
`decide()` runs on the notification path and must stay inside the spec's 10 ms warm budget **including** the simulation, using the in-memory store. No Room, disk, network or blocking calls on that path. Stage 6's collector calls `decide()` on a worker, never the binder thread, and applies a hard timeout that **fails open**.

---

## 7. Decisions needed from you (the agents will ask; answer once, here)

| ID | Question | Recommended default |
|---|---|---|
| D56-1 | OTP and emergency detection needs notification text. May Stage 6 add **two in-memory booleans** (`isOtpLike`, `hasEmergencyKeyword`) to `NotifExtractor`, never stored in events or logs, only passed into `NotificationFacts`? | Yes. Without it, the OTP never-hold rule cannot work. The Stage 2 canary test must still pass |
| D56-2 | Encrypt held-notification text with an Android Keystore AES-GCM key (no new dependency)? | Yes |
| D56-3 | Policy default `OFF`, first run `DRY_RUN`, then `LIVE` on Dhyan's own test notifications only? | Yes |
| D56-4 | Hold-time estimates (`τ_release`, `τ_digest`): `τ_digest` from `DigestSchedule` (every 2 h); `τ_release` a context-based estimate with a documented default (15 to 45 min) because no break predictor exists yet | Yes, flagged as an assumption |
| D56-5 | First `LIVE` target apps after the test notifications: Gmail or Instagram (cancel+digest path) before WhatsApp (snooze path) | Yes |
| D56-6 | Contract change approvals (section 4, rule 3) | Decide case by case |

---

## 8. Communication protocol (agents cannot talk to each other)

- Each agent keeps a notes file: `STAGE5_NOTES.md` or `STAGE6_NOTES.md` with: status ledger (`not_started | in_progress | implemented | tested | blocked`; `tested` only with pasted evidence), commands run with real summary lines, assumptions with approval column, **Cross-stage requests**, **Contract change requests**, "not verified on target", and inputs for the other stage.
- When a note matters to the other agent, **you** paste a block like this into the other session:
  ```text
  CROSS-STAGE NOTE (from Stage <5|6>, <date/time>)
  What changed or was found: ...
  What you must do or must not assume: ...
  Contract version: ...
  ```
- **Stop and ask the human** (do not guess) when: the contract is insufficient; a schema field is missing (that is a schema delta applied by the human on `main`); a requirement is ambiguous in a way that changes user-visible behavior, the data contract or scope; code on `main` conflicts with the spec; or you need to touch a file you do not own.
- **Never reclassify a feature as out of scope.** If time runs short, report the gap in the notes with an owner and an acceptance test (spec 25.1, 25.11, 25.15).
- Pre-flight first: both agents begin by reading the real repo (`git log`, `HANDOFF_STATUS.md`, `DECISIONS_PENDING.md`, `STAGE4A_NOTES.md`, 4B notes, `docs/TWIN_MODEL_CONTRACT.md`) and reporting what exists. Class and file names in this prompt are intended shapes; the repo is the truth.

### Report format at every stop (both agents)
Milestone or prompt status; files created and changed; exact commands run with the real summary line; anything not run; anything not verified on target; deviations from the prompt; assumptions added; cross-stage and contract requests; questions for the human.

---

## 9. Priorities when time is tight (spec 17.3)

**Never trade away:** the Simulate tab, paired SHOW/SUPPRESS with uncertainty, safety rules, explanations, persona demo, and one real hold-and-digest on the phone. Everything else may be re-ordered but stays on the ledger. Stage 5 target is about 60 minutes of agent time plus review; Stage 6 about 100. The **Gate 2** exit (a real notification held or snoozed, shown in the digest, Show-now works, Simulate tab shows both timelines for a chosen persona) is **run by you on the Reno 14**. Neither agent can do it.

---

## 10. Tool-specific notes

**Codex (Stage 5)**
- One prompt per task, in order, small diffs. Run the commands yourself and paste results. Each prompt ends with a commit.
- Pure Kotlin only in `twin/decision/`, `twin/counterfactual/`, `safety/`: no `android.*`, `androidx.*`, Room. `kotlinx.coroutines` is allowed but `decide()` is synchronous. Add `tools/check-decision-purity.mjs` with a planted-violation self-test.
- Every test uses several fixed seeds. Never choose a seed to make a test pass.

**Antigravity (Stage 6)**
- Planning mode, strongest model, terminal policy **Request review**, browser off, **one agent only**. Stops after M0, M2, M5 and at the end of M6.
- The rules-file location is inconsistent across versions, so the stage prompt repeats the key rules at the top. Check the Rules panel to see what your build loaded.
- No `adb install`, `adb uninstall`, `pm clear` or any device command from the agent. You do device steps. Builds: `:app:assembleDebug`; tests: `:app:testDebugUnitTest`. Instrumented tests may be written but are listed "written, not run" unless you run them.
- Until M6, develop against `FakeDecisionProvider` and label fake data in the UI so it can never be mistaken for a real decision.

---

## 11. Quick conflict checklist (before every merge)

- [ ] No file edited outside the owner column in section 3 (`git diff --name-only main...branch`).
- [ ] `DECISION_CONTRACT_VERSION` unchanged, contract files untouched since Phase 0 (or a signed-off 5.0b).
- [ ] `core-spec/` untouched.
- [ ] Root `package.json` has all earlier keys plus `check:decision` and `check:actuator`.
- [ ] Gradle and manifest changes are listed in `STAGE6_NOTES.md`.
- [ ] All checks and the full unit suite rerun **after** merging, with real output.
- [ ] Governance docs updated by **you**, from the two notes files.