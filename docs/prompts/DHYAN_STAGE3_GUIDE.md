# DHYAN: Stage 3 (Twin core) detailed guide

Companion file: `DHYAN_STAGE3_ANTIGRAVITY_PROMPT.md` (the prompts you paste into Antigravity).
Spec sources: section 17.2 Stage 3, sections 8.1 to 8.9, 9.1, 18.1, 25.2, 25.3, 25.5 (fail-open), 25.6 (determinism), 25.9 (staleness), 25.14 (simulation tests).

Stage 3 is the heart of the project: the maths that answers *"if I show this notification now, what is likely to happen, and what would have happened if I had not?"* It is **pure Kotlin** (no `android.*`), runs in memory, and must return a paired SHOW vs SUPPRESS result in under 10 ms once warm.

**Gate 1 (spec):** a hard-coded snapshot returns paired SHOW/SUPPRESS distributions in under 10 ms (warm), and the tests are green.
**Time budget in the spec:** 85 minutes. Allow more for your first Antigravity session.

---

## 1. What Stage 3 delivers (and what it does not)

| In scope | Not in scope (later stages) |
|---|---|
| Seeded RNG with independent streams, samplers (normal, gamma, beta, dirichlet, categorical) | Episodes and outcome attribution (Stage 4) |
| `BetaStat`, `LogNormalStat` (Welford), Dirichlet counts, Gamma hazard | Persona generator and replay (Stage 4) |
| Hierarchical backoff, recency decay, population priors | Importance model, compulsive score (Stage 4) |
| Fatigue index, hour band, context bucketing, notification class | Cost function, action selector, safety, explanations (Stage 5) |
| In-memory parameter store with async write-through via an interface | Room implementation of the interface (Stage 2 branch) |
| `ContextSnapshot` type and a `StateFlow` holder | The fusion code that fills the snapshot (Stage 8 / integration) |
| `PairedMonteCarlo` with common random numbers, competing-risk event order | IPW / doubly robust counterfactual estimators (Stage 5) |
| Fail-open results (`NotReady`) for unloaded model or stale context | On-device latency benchmark button (integration) |
| Warm-up routine and a latency benchmark | |
| Unit and property tests, Gate 1 demo, model contract document | |

Stage 3 never touches notifications, the phone UI or the network.

---

## 2. Prerequisites checklist

Do these before starting the agent.

1. **Stage 1 done.** `core-spec/` is committed (v1.0.0 or 1.1.0). `tools/check-twin-purity.mjs` exists from Prompt 1D.
2. **Contract commit on `main` (Prompt 3.0 in the prompt file).** It adds only `ParamPersistence`, `ParamRecord`, `ParamKind` to `twin/params/`, so Stage 2 and Stage 3 agree on one interface. If you are running Stage 2 in parallel, also apply the one-paragraph edit to Prompt 2A that is in the prompt file (Part 2).
3. **Worktree (if Stage 2 runs at the same time):**
   ```
   git worktree add ..\dhyan-s3 -b stage3
   ```
   Open `..\dhyan-s3` as the Antigravity workspace. Stage 2 uses its own worktree.
4. **Worktree extras that git does not carry:**
   - Copy `phone\local.properties` (contains `sdk.dir`, gitignored) into the worktree's `phone\`.
   - Run `npm install` at the worktree root.
   - Confirm the JDK: Gradle needs JDK 17. In a terminal run `java -version`; if it is wrong, point `JAVA_HOME` at Android Studio's bundled JDK (`<Android Studio>\jbr`).
   - Sanity check: `cd phone` then `.\gradlew.bat :app:testDebugUnitTest` should run (even with few tests). Fix the toolchain *before* the agent starts, otherwise it will burn quota fighting Gradle.
5. **Commit `AGENTS.md`** at the worktree root with the Stage 3 addendum from section 4 below.
6. **Copy this guide into the repo** as `docs/STAGE3_GUIDE.md` and commit it. The main prompt tells the agent to treat it as the authoritative design, so it must be in the workspace.

---

## 3. Antigravity for the first time

Antigravity is Google's agent-first IDE. What I could confirm from public guides (names may differ slightly in your version, so check the UI):

- **Two surfaces:** *Agent Manager* (start and watch agents) and *Editor View* (normal code editing). Start tasks in the Agent Manager.
- **Planning vs Fast mode.** Use **Planning** for this stage. It makes the agent write an implementation plan first, which you can read and comment on before it codes. Fast mode skips planning.
- **Artifacts.** The agent produces artifacts such as a plan, a task list and a walkthrough. Review the plan artifact before approving work. You can comment on an artifact to steer the agent.
- **Policies** (Settings > Agent / Advanced): *Terminal execution policy* and *Review policy*. For your first session choose **Request review** for terminal commands, then approve the safe ones (`gradlew`, `node`, `npm`, `git status`, `git diff`, `git add`, `git commit`). Once you trust the flow you can loosen it. Never use "always proceed" in a folder you care about without git commits as your safety net.
- **Rules.** Several guides say Antigravity reads a root `AGENTS.md`. Others describe workspace rules in `.agent/rules/` (some say `.agents/rules/`). I could not confirm one canonical location, so do both: keep `AGENTS.md` at the root, and also paste the key rules at the top of the first prompt (the prompt file already does this). Check the Rules/Customizations panel to see what your build picked up.
- **Models.** Pick the strongest reasoning model available in your plan. This stage is maths-heavy; avoid the cheapest or "fast" models for the simulator milestone.
- **Quota.** Free tiers have usage limits. The prompt is split into milestones with a git commit after each, so work survives if quota runs out. A resume prompt is included.
- **Parallel agents.** Antigravity can run several agents. Do **not** run more than one on this stage. The milestones depend on each other.
- **Browser / JavaScript policy.** Not needed. Leave the browser off for this task.

How to steer: read the plan artifact, leave comments where it deviates from the prompt, then approve. If the agent drifts later, stop it and paste the resume prompt with a one-line correction.

---

## 4. AGENTS.md addendum (paste at the end of your existing AGENTS.md)

```text
## Stage 3 rules (twin core)
- Scope: only phone/app/src/main/java/com/dhyan/app/twin/**, phone/app/src/test/java/com/dhyan/app/twin/**, docs/TWIN_MODEL_CONTRACT.md, STAGE3_NOTES.md, tools/check-twin-contract.mjs, and two script keys in root package.json (check:twin, check:twin-contract).
- FORBIDDEN in Stage 3: core-spec/, any Gradle or manifest file, Koin modules, collectors/, ingest/, data/, ui/, sync/, and the shared governance docs (TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md, DECISIONS_PENDING.md). Report needed changes in STAGE3_NOTES.md.
- twin/ is pure Kotlin: no android.*, androidx.*, no Room. kotlinx.coroutines is allowed. No new dependencies.
- Determinism: all randomness comes from the seeded RNG classes. Never use Math.random, kotlin.random.Random.Default, or System.nanoTime for sampling.
- Units: durations in minutes, hazard exposure in hours. Name variables with the unit (e.g. windowMin, exposureHours).
- Never hard-code results to match the pitch numbers in the spec (24 min, 0.62, 0.09). Print what the model produces.
- Never claim a test passed unless you ran it and pasted the summary line. Never claim anything about the Reno 14.
- Commit after each milestone on the stage3 branch. Never push. Never touch main.
```

---

## 5. Package map

Under `phone/app/src/main/java/com/dhyan/app/twin/` (verify the real package name from Stage 1):

```
twin/
├── rng/          SeededRng (splitmix64 + xoshiro256**), RngStreams, Samplers
├── params/       ParamPersistence, ParamRecord, ParamKind   <- from the contract commit
│                 BetaStat, LogNormalStat, DirichletStat, GammaHazardStat
│                 Hierarchy, PriorTable, InMemoryParamStore, ParamKeys, ModelSnapshot
├── model/        ContextSnapshot (+ enums), NotificationInput, TwinConfig, ModelConfig
├── context/      HourBand, FatigueIndex, ContextBucket, NotifClass, ContextBucketer,
│                 SnapshotHolder (StateFlow)
├── simulator/    PairedMonteCarlo, SimulationResult, ArmStats, UncertaintyReport,
│                 UncertaintyDiagnostics (nested sampling)
├── TwinEngine.kt SimOutcome (Ready | NotReady), staleness policy, facade
└── bench/        TwinWarmup, LatencyBenchmark
```

Tests mirror this under `phone/app/src/test/java/.../twin/`.

---

## 6. The model contract (what the code must implement)

This section resolves the gap between spec 8.7 (independent `picked_i` draw) and spec 25.2 (competing risks, single owner). It is the smallest change that satisfies 25.2. The agent must also write it up in `docs/TWIN_MODEL_CONTRACT.md`.

### 6.1 Units and window
- Time in **minutes**. Hazard `λ` is per **hour**; `rate_per_min = λ / 60`.
- Decision instant `t = 0` is when the notification would be shown. Outcome window `[0, W)`, `W = 30`.
- **One distraction owner per arm per window.** Multiple distractions in one window are not modelled in v1 (documented limitation).
- **Costs are not truncated at `W` by default.** Onset must fall inside the window; the full `D + R` is charged. A config flag `truncateCostsAtWindow` (default `false`) charges only `min(D + R, W − t_onset)`. Both modes are tested.

### 6.2 Random streams (this is what makes CRN correct)
For run `i` and master `seed`, derive three independent streams from `(seed, i, tag)` via a splitmix64 mix:
- `EPISTEMIC`: parameter uncertainty. **Shared by both arms** (it is the same world).
- `SELF`: baseline self-initiated behaviour. **Shared by both arms** (common random numbers).
- `NOTIF`: notification-specific behaviour. **SHOW arm only.**

In the `SELF` and `NOTIF` streams, draw the same values in the same order on every run regardless of branch (always draw `u_time, u_dest, z_D, z_R`, even if unused). That keeps results comparable when a parameter changes under the same seed. Rejection samplers (gamma, beta) in the `EPISTEMIC` stream may consume a variable number of values; that stream is separate, so it cannot misalign the other two.

### 6.3 One run, step by step
1. **Epistemic draws (EPISTEMIC stream):** `p_i ~ Beta(posterior p_pickup(x))`, `λ_i ~ Gamma(posterior λ_self)`, destination probabilities `θ_self ~ Dirichlet`, `θ_notif ~ Dirichlet`, plus three standard normals `ε_Dself, ε_Dnotif, ε_R` used to shift the lognormal means: `m_i = m_post + ε · s / sqrt(n0 + n)`.
2. **Baseline (SELF stream):** `T_self = −ln(1−u_time) / (λ_i/60)` (∞ if `λ_i = 0`). Destination from `θ_self` via `u_dest`. `D_self = exp(m_D + s_D·z_D)`, `R_self = exp(m_R + s_R·z_R)`. The baseline event exists iff `T_self < W`.
3. **Notification (NOTIF stream):** `picked = u_pick < p_i`. Pickup latency `T_notif ~ LogNormal(m_pl, s_pl)`. Destination from `θ_notif`. `D_notif`, `R_notif` as above. The notification event exists iff `picked && T_notif < W`.
4. **SUPPRESS arm:** `L_suppress = D_self + R_self` if the baseline event exists, else `0`.
5. **SHOW arm:** the **earliest** existing event owns the window (tie goes to the notification event):
   - notification event owns: `L_show = c_glance + D_notif + R_notif`
   - baseline event owns (user was already distracted before opening it, the notification is an in-session arrival): `L_show = c_glance + D_self + R_self`
   - neither: `L_show = c_glance`
6. **Pair:** `ΔL_i = L_show − L_suppress`. It can be negative (the notification pulled the user in sooner than their own habit would have; the baseline cost is still charged in SUPPRESS). That is expected and documented.

### 6.4 Identities the tests must hold
- `p_pickup = 0` (force `p_i = 0`): every run has `ΔL_i = c_glance` exactly. With `c_glance = 0`: `ΔL_i = 0` exactly.
- `λ = 0` (no baseline): `E[ΔL] = c_glance + p · (E[D] + E[R])` with `E[X] = exp(m + s²/2)`, minus the probability mass lost to the window cut (`T_notif ≥ W`). Compare against the analytic value within 4 standard errors at large `N`.
- Very high `λ`: the baseline event nearly always precedes the notification event, so `E[ΔL] → c_glance` (no double counting).
- CRN variance: in a high-`λ` scenario, `Var(ΔL)` with shared streams is lower than with independent streams.
- Per run: `L_show = c_glance + cost(owner)`. No run charges both a baseline cost and a notification cost in the SHOW arm.

### 6.5 Outputs (per spec 8.7, 25.2, 25.3)
- Per arm: `mean, p10, p50, p90, P(L > 1.0 min)` (strict `>`). Percentiles use linear interpolation at index `q·(N−1)`.
- `ΔL`: mean, `p10`, `p90` labelled **"80% simulation interval"** (not a credible interval), and the Monte Carlo standard error of the mean.
- `p_pickup`: the 80% **posterior** interval from the `p_i` draws.
- Evidence: `nEff` and `evidenceStrength = nEff / (nEff + 10)`, documented as a **heuristic evidence-strength score, not a probability of correctness**.
- Provenance and reproducibility: `modelVersion` (increments on every parameter update), `seed`, `configVersion`, `N`, `hierarchyLevelUsed` per parameter family, per-run owner array (for tests).
- **Uncertainty is never collapsed into one number.** The hot path returns the Monte Carlo error. A separate `diagnoseUncertainty()` (nested sampling, not on the hot path) returns epistemic variance, process variance, Monte Carlo error and a list of model-uncertainty flags, as separate fields.

---

## 7. Parameter store details

### 7.1 Context buckets (spec 8.3)
`x_full = (taskType, hourBand, fatigueBand, notifClass)` with `notifClass = {appCat, isVip, urgencyBand, repeatBand}`.
- `hourBand` (local hour, half-open): night `[0,6)`, morning `[6,12)`, afternoon `[12,17)`, evening `[17,22)`, late `[22,24)`.
- `fatigueBand`: `f = clip(0.5·(hoursWorked/6) + 0.3·switchRateZ + 0.2·stallRatio, 0, 1)`; low `< 0.33`, med `< 0.66`, else high.
- `urgencyBand` (assumption): low `< 0.33`, med `< 0.66`, high otherwise.
- `repeatBand` (aligned to 8.5): `R0` for 0 or 1 recent messages, `R2` for 2, `R3` for 3 or more.
- Baseline bucket (no notification): `(taskType, hourBand, fatigueBand)`.

**Hierarchy:** `FULL → (taskType, notifClass) → (notifClass) → PRIOR`. Baseline hierarchy: `(taskType, hourBand, fatigueBand) → (taskType) → PRIOR`.
Every observation updates **all levels** of its chain, so parents aggregate their children.

### 7.2 Estimators
- **Beta:** keep evidence counts `s, f` (decayed by `γ = 0.995` per episode **before** adding the new observation) plus a fixed prior `(α0, β0)`. `α = α0 + s`, `β = β0 + f`. Shrinkage with parent mean `μ_p` and `κ = 5`: sample from `Beta(α + κμ_p, β + κ(1−μ_p))`; point estimate `(α + κμ_p) / (α + β + κ)`.
- **LogNormal:** Welford on `ln d` (count, mean, `M2`). Prior pseudo-observations `n0 = 3` with `(m0, s0)` = the parent's posterior (or the population prior). `m = (n0·m0 + Σ ln d)/(n0 + n)`; `s² = (n0·s0² + M2)/(n0 + n)`, floored so `s ≥ 0.25`.
- **Dirichlet:** per-destination counts; shrinkage toward the parent's normalised mean with strength `κ`.
- **Gamma hazard:** shape `α = α0 + events`, rate `β = β0 + exposureHours`; parent shrinkage uses `κ` as pseudo-exposure hours. `λ_i ~ Gamma(α, β)` (per hour).
- **Population priors (8.9):** `p_pickup` social `Beta(3,4)`, known-contact message `Beta(4,3)`, promo `Beta(1,6)`, otherwise `Beta(1,1)`. Dwell: social `LogNormal(ln 9, 0.8)`, short_video `LogNormal(ln 14, 0.9)`, messaging `LogNormal(ln 3, 0.6)`. Resume: coding `LogNormal(ln 6, 0.6)`, studying `LogNormal(ln 4, 0.6)`. Fatigue multiplier on **prior pickup odds**: ×1.0 / ×1.2 / ×1.5.

### 7.3 Persistence mapping (matches the Room `params` table in spec 11.7)
`ParamRecord(key, kind, a, b, n, mean, m2, updatedTs)`, all numeric fields `Double`:

| kind | a | b | n | mean | m2 |
|---|---|---|---|---|---|
| BETA | decayed successes | decayed failures | decayed observation count | unused | unused |
| LOGNORM | unused | unused | count | mean of ln d | Welford M2 |
| DIRICHLET | decayed count for **one destination** (one row per destination, key suffix `|dest=<id>`) | unused | unused | unused | unused |
| GAMMA | events | exposure hours | observation count | unused | unused |

Priors are **not** persisted. They come from `PriorTable` (code), so retuning a prior later does not corrupt stored evidence. The Stage 2 Room entity must use `REAL` for these columns (decayed counts are fractional).

### 7.4 Concurrency and fail-open
- Stats are **immutable values** replaced atomically in a `ConcurrentHashMap` (no torn reads while learning updates). The simulator works on a `ModelSnapshot` resolved once per call, so a simulation sees one consistent model.
- Updates mark keys dirty; a single background flusher writes batches through `ParamPersistence.upsert`. A persistence failure increments a counter and never throws into the caller.
- **Fail-open (25.5):** `TwinEngine.simulate` returns `SimOutcome.NotReady(reason)` when the store has not finished `load()`, the snapshot is invalid, or the snapshot is older than the staleness threshold (default 120 s, configurable). Callers must treat `NotReady` as ALLOW. A cold store with *no data yet* is **Ready** (priors apply) and reports low evidence strength.

---

## 8. Test matrix

| Area | Test | Spec |
|---|---|---|
| RNG | same seed gives same sequence; different streams are independent; stream draw counts fixed | 18.1, 25.14 |
| Samplers | empirical mean/variance of normal, gamma (shape < 1 and > 1), beta, dirichlet match analytic values, several seeds | 18.1 |
| Beta | update, decay order, shrinkage formula, hierarchy backoff | 18.1 |
| LogNormal | Welford vs naive computation, prior blending, sigma floor 0.25 | 18.1 |
| Gamma hazard | posterior mean converges to the true rate | 18.1 |
| Context | hour band boundaries, fatigue clip and bands, bucket keys, hierarchy chain | 8.3 |
| Store | load, update, version bump, dirty flush, persistence failure does not throw, not-loaded gives NotReady | D15, 25.5 |
| Simulator | fixed-seed determinism | 25.14 |
| Simulator | `p_pickup = 0` gives `ΔL = c_glance` (and 0 when `c_glance = 0`) | 18.1 |
| Simulator | high baseline hazard approaches `c_glance` | 25.2, 25.14 |
| Simulator | analytic check in the no-baseline regime | 18.1 |
| Simulator | CRN reduces `Var(ΔL)` versus independent streams | 25.14 |
| Simulator | no double-counted dwell (per-run owner property) | 25.14 |
| Simulator | window truncation on and off | 25.14 |
| Simulator | uncertainty components reported separately | 25.2, 25.14 |
| Engine | stale snapshot, invalid snapshot, not loaded give NotReady | 25.5, 25.9 |
| Learning | after training the store, `p_pickup` interval narrows and the verdict moves toward the generating value | 15.5 |
| Latency | warm median and p95 under 10 ms on the dev JVM; numbers printed | Gate 1 |
| Purity | `check-twin-purity.mjs` passes | 5.3 |
| Contract | exported `ContextSnapshot` JSON validates against `core-spec/context-snapshot.schema.json`; `TwinConfig` defaults equal `core-spec/default-params.json` | 25.9 |

Use several fixed seeds in statistical tests. Do not pick seeds to make a test pass.

---

## 9. Gate 1 procedure (you run this, not the agent)

1. Run from the worktree:
   ```
   cd phone
   .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.twin.*"
   cd ..
   npm run check:twin
   npm run check:twin-contract
   ```
   Expect all tests green and both npm checks exit 0. Paste the real summary lines into `STAGE3_NOTES.md`.
2. Run the Gate 1 demo test and read its printed table. It simulates a hard-coded snapshot (coding, evening, medium fatigue, a low-importance social notification) on a cold store and on a trained store. Check:
   - Both arms have mean, p10, p50, p90 and `P(L > 1 min)`.
   - SUPPRESS is lower than SHOW for the notification-prone fixture, and the gap shrinks toward `c_glance` for a self-distraction-dominated fixture.
   - The trained store has a narrower `p_pickup` interval and higher evidence strength than the cold store.
   - **Do not tune anything to reproduce the spec's example numbers.** They are illustrative (25.3).
3. Read the printed latency line. Warm median and p95 must be under 10 ms. This is a desktop JVM number; the Reno 14 runs ART, so an on-device check (a debug button that calls `LatencyBenchmark.run()`) is an integration task and stays on the checklist as **not verified on target**.
4. Record the result in `STAGE3_NOTES.md`. Do not edit `STAGE_GATES.md` from this branch; fold it in after the merge.

---

## 10. What to review in the generated code (the usual bugs)

- **Gamma sampling with shape < 1.** Needs the boost `Gamma(a+1) · U^(1/a)`. A naive Marsaglia-Tsang breaks for small shapes, which is exactly the regime of low-evidence hazards and Dirichlet counts.
- **Beta from two gammas** and its edge cases (`α` or `β` very small).
- **Percentile off-by-one** (`q·(N−1)` with interpolation, not `q·N`).
- **Branch-dependent draws.** Any `if` that skips a random draw changes the stream alignment between arms and silently breaks CRN.
- **Minutes vs hours.** `λ` per hour, `W` in minutes, `κ_d` per hour (Stage 5). Search for `/ 60`.
- **Decay order:** decay old evidence first, then add the new observation.
- **Sigma floor** applied to the sampled `s`, not just the stored value.
- **NaN/Infinity paths:** `λ = 0` (infinite event time), `n = 0`, empty parent.
- **Allocation in the hot loop:** boxed `Double`, `List<Double>`, `map {}`. Use `DoubleArray`.
- **Nondeterminism:** `HashMap` iteration order, `Math.random`, `System.nanoTime` used for sampling, `ConcurrentHashMap` iteration used in a numeric sum.
- **Thread safety:** the simulator must read an immutable snapshot, not live mutable stats.
- **Honest labels:** "80% simulation interval", "evidence strength", never "80% confidence".

---

## 11. Merge and handoff

- **Merge `stage3` into `main` first** (it only touches `twin/**`, docs and two `package.json` script keys). Then merge Stage 2, resolve the `package.json` scripts by hand, and rerun `npm run checks`, `npm run check:phone-contract`, `npm run check:twin`, `npm run check:twin-contract` and the full unit tests.
- Fold `STAGE3_NOTES.md` into `TRACEABILITY.md`, `HANDOFF_STATUS.md`, `STAGE_GATES.md` and `DECISIONS_PENDING.md` after the merge.
- **Integration TODOs that remain after Stage 3 (keep them on the list, do not drop them):** call `TwinWarmup.run()` at app start; wire `SnapshotHolder` to the fusion layer; implement the Room-backed `ParamPersistence`; on-device latency check on the Reno 14.
- **Stage 4 needs from Stage 3:** the store's learning API (`observePickup`, `observeDwell`, `observeResume`, `observeSelfPickups`, `observeDestination`), `ContextBucketer`, and the `Event` model from Stage 2A. This is why Stage 2A must be merged before Stage 4.

---

## 12. Assumptions the agent must flag for your approval (S3-x)

The spec does not give these values. They live in `ModelConfig` (not in `core-spec`) and must be listed in `STAGE3_NOTES.md`. Approve, change, or promote them to `core-spec/default-params.json` at the next version bump.

| ID | Assumption | Default |
|---|---|---|
| S3-1 | Notification pickup latency | `LogNormal(m = ln 1.0 min, s = 0.9)` |
| S3-2 | Baseline hazard prior | `Gamma(shape 1.5, rate 1.0 per hour)` (mean 1.5 per hour) |
| S3-3 | Fallback dwell prior for categories without a spec value | `LogNormal(ln 8, 0.8)` |
| S3-4 | Fallback resume prior for task types without a spec value | `LogNormal(ln 5, 0.6)` |
| S3-5 | Destination (category, surface) list and Dirichlet prior | social/feed, short_video/shorts, video/watch, communication/chat, news/feed, shopping/other, games/other; base count 1, plus 3 on the notifying app's own destination |
| S3-6 | `urgencyBand` thresholds | 0.33 / 0.66 |
| S3-7 | `repeatBand` | R0 (0 to 1), R2 (2), R3 (3 or more) |
| S3-8 | Hazard shrinkage uses `κ` as pseudo-exposure hours | `κ = 5 h` |
| S3-9 | `nEff` definition | `n_full + 0.5 · max(0, n_parent − n_full)` (decayed observation counts of `p_pickup`) |
| S3-10 | Staleness threshold | 120 s |
| S3-11 | Recency decay applies to **evidence counts only**, not the prior | spec 8.4 says "multiply both by γ"; decaying the prior too would erode population knowledge. Literal alternative available on request |
| S3-12 | Fatigue multiplier applies to the **population prior odds only** | applies when no learned level is available |
| S3-13 | `communication` appCat uses the known-contact prior `Beta(4,3)`; `notifCat = promo` uses `Beta(1,6)` | |
| S3-14 | Dwell keyed by (category, surface) with fallback to category, then prior; resume keyed by taskType only | `stuck` and `dwellBand` extension points left as hooks |
| S3-15 | Cost truncation at the window | off by default |
| S3-16 | Single distraction owner per arm per window | documented v1 limitation |

---

## 13. When things go wrong

| Problem | What to do |
|---|---|
| Gradle will not run in the worktree | `local.properties` missing, wrong JDK, or no network for dependencies. Fix manually first |
| Agent edits Gradle files or `core-spec/` | Revert with `git checkout -- <file>`, paste the resume prompt with the violation named |
| Statistical test flaky | Check stream alignment and draw counts first; only then loosen tolerance, and write down why |
| Latency over 10 ms | Look for allocation in the loop, repeated percentile sorts, sampling in boxed collections, resolving parameters inside the run loop |
| Quota runs out mid-stage | Commit what exists, then use the resume prompt in a later session |
| Agent says "all tests pass" with no output | Do not accept it. Run the command yourself |
| You disagree with a model choice | Change the contract section 6 and tests together; never let code and `TWIN_MODEL_CONTRACT.md` drift |

---

## 14. Done means

- All Stage 3 tests green with real output pasted into `STAGE3_NOTES.md`.
- `check:twin` and `check:twin-contract` pass.
- `docs/TWIN_MODEL_CONTRACT.md` matches the code (window, single owner, truncation flag, units, streams, outputs).
- `STAGE3_NOTES.md` lists assumptions S3-1..S3-16 with your approvals, open questions, and everything **not verified on target**.
- Gate 1 recorded; stage3 branch ready to merge; Stage 4 inputs listed.
