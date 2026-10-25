# Stage 5 preflight and Phase 0 — 2026-10-25

The owner initially instructed starting Stage 5 while Stage 4B was in progress. Stage 4B is now committed and closed at cc41cce7f8d14a9188fa96982fe00106b7d39310. This isolated contract-5-0 branch is still based on main 4c944a4; the committed 4B source was read from the main checkout without merging or copying it into this branch. Only Phase 0 types/helpers and requested pre-freeze amendments are prepared. No pipeline, importance replacement, actuation, merge or push.

## Prerequisites and scope

Read AGENTS, HANDOFF_STATUS, DECISIONS_PENDING, STAGE3_NOTES, STAGE4A_NOTES, docs/SPEC sections 8.5,9,10,25.5–25.7 and read-only Stage 3 APIs. The two supplied guides are copied verbatim to docs in this branch. The common guide asks for these on main; to preserve active main work they are committed here for owner review/merge. No Stage 5 or Stage 6 implementation worktree is created before the contract merge; this is the short Phase 0 contract checkout.

Package com.dhyan.app. Kotlin2.3.10, AGP9.2.1; JVM compilation target17, installed JDK21.0.11 used previously (must verify in this session before build). Coroutines core/test explicitly declared in phone/app/build.gradle.kts. No new dependencies.

TwinEngine.simulate(snapshot: ContextSnapshot, notif: NotificationInput, seed: Long=42): SimOutcome. Ready(result: SimulationResult); NotReady reasons MODEL_NOT_LOADED, INVALID_SNAPSHOT, STALE_CONTEXT. The engine accepts an injected clock, default wall clock: Stage 5 must construct/use it with passed time, not let the default clock affect decisions. Future timestamps and NaNs require pipeline validation because existing engine validation is incomplete.

SimulationResult: show/suppress ArmStats(mean,p10,p50,p90,pLossGt1Min), deltaLMean, deltaL80SimIntervalLow/High, deltaLStdError, pickupPosterior80Low/High, nEff, evidenceStrength, modelVersion, seed, configVersion, n, pickupHierarchyLevel, hazardHierarchyLevel, ownerFractions, lShow/lSuppress/deltaL arrays, owner byte array, pPickupSample array. No direct provenance or pickup mean; derive simulated/low evidence and mean from available samples without changing twin API.

ContextSnapshot in twin/model/ContextSnapshot.kt: ts,focusState,taskType,taskSource,confidence,hourBand,minutesInState,fatigueBand,recentInterrupts30m,laptopState,phoneState,secondScreenOverlap,declaredSession. Enums follow canonical context schema. NotificationInput has appCat,nullable notifCat,isVip,urgencyScore,repeatCount5m. ContextBucketer is defined in twin/context package (locate actual file, not guessed filename). TwinConfig defaults N500,W30,kappa5,gamma.995,n03,sigmaMin.25,cGlance.4,version1.0.0. ModelConfig has latency ln1/.9, hazard1.5/1 perhour, urgency .33/.66, shrinkage5h, stale120000ms, truncationfalse,minNEff1.

## Committed Stage 4B Importance API for Stage 5 (read-only finding)

Source: phone/app/src/main/java/com/dhyan/app/twin/decision/ImportanceModel.kt at committed cc41cce7f8d14a9188fa96982fe00106b7d39310. STAGE4B_NOTES.md at that commit was read; it documents the model in Overview and results, but contains no section named "Importance API for Stage 5". This finding is source-derived, not an invented missing-section summary.

```kotlin
class ImportanceModel(defaultPriorAlpha: Double = 1.0, defaultPriorBeta: Double = 4.0)
fun score(
    senderHash: String?, appCat: String, notifCat: String?, isVip: Boolean,
    hasReplyAction: Boolean = false, repeatCount5m: Int = 0,
    rawTextHint: String? = null, urgencyScore: Double = 0.0
): Double
fun observeFeedback(senderHash: String?, appCat: String, isImportant: Boolean, gamma: Double = 0.995)
```

score is synchronous, nonblocking in-memory reads and local arithmetic; it does not update feedback, read a clock, perform I/O or require raw text. If rawTextHint is supplied it scans keywords; Stage 5 must leave it null and use existing categorized facts/urgency and separate never-hold booleans, never copy text into NotificationFacts. ObserveFeedback is a separate mutable operation and does not belong inside decide().

Thread-safety: ConcurrentHashMap publishes immutable BetaStat values; observeFeedback uses compute for atomic per-key replacement, and score uses get. Safe concurrent access avoids corruption and iteration races. However senderStats and appCatStats are separate maps: a score can see updates from different feedback instants, so it is not a transactional/versioned snapshot and identical facts need not yield identical scores if feedback changes concurrently. Calling score from decide() is memory-safe and bounded for validated inputs with the default priors, but deterministic evaluation requires external serialization or a stable feedback-state boundary. Do not promise snapshot consistency or idempotence across feedback updates. Do not invoke observeFeedback on the decision path. Future adapter must validate score finiteness/range and fail open on errors; malformed priors/gamma are not comprehensively rejected by this model. No model changes or pipeline were made here.

Cross-stage follow-up for 5C: document/agree a stable importance-state evaluation boundary if concurrent feedback is allowed. The raw-text argument is optional, so no text-related API change is needed just to call score. Add the missing API section in Stage 4B's notes through its owner, not this restricted amendment.

notifCat is an OPEN nonempty string, not a closed enum. No canonical registry for call/alarm/navigation/message/social/promo/email/transport/delivery exists; do not claim an enum. appCat exactly coding,docs,communication,social,video,short_video,games,shopping,news,music,learning,utility,system,unknown. Critical class mappings need explicit policy documentation before 5B.

Core defaults: kappa_d3.0min/hr,lambda_miss5min,c_digest.8min,c_rel.25,I_max.85,I_silent.16,epsilon.05 when I<.4,conf_min.4,c_glance.4min,digest120min,W30min. Recheck30s and expiry10min/24h are policy assumptions, not Appendix A numeric rows.

Existing twin purity recursively rejects Android imports under twin. Twin contract checker uses generated context-snapshots.json and twin-config.json under app/build/contract with Ajv plus core defaults; requires exported fixtures from tests. New checks follow exported function/direct CLI pattern and add planted-failure self-tests.

Directories decision/counterfactual/safety already contain .gitkeep in the committed base; Committed ImportanceModel belongs to Stage 4B and remains read-only; it is not present in this branch baseline. Stage 4A TruthRecord has persona/dayIndex/notificationIndex/ts/context/inSession/trialEligible/exclusionReason/trial,realizedArm,propensity,eligibleForExploration,world. TrialDraws holds existence flags,owner,lShowMin,lSuppressMin,deltaLMin. RandomizedHold(eps,maxImportance) draws from tag500, holds with eps if non-session,non-important,urgency proxy below max; SHOW propensity1-eps, SUPPRESS eps. These are test-only inputs to later estimators.

## Assumptions and approvals

All S5 assumptions are proposed by the supplied guide; owner authorized starting, not separately confirming each user-visible choice. S5-1 ALLOW/DELAY/DIGEST/SILENT tieorder1e-9; S5-2 release15/30/45min capped digest; S5-3 local-midnight aligned digest120min; S5-4 OFF skips simulation; S5-5 exploration hold->ALLOW only; S5-6 SHOW=ALLOW; S5-7 maxage30s; S5-8 OTP expiry10min,other24h; S5-9 explanation-ranking heuristic; S5-10 low evidence<.4 and I>.5 allows; S5-11 strict halves kappaD/disables exploration; S5-12 decision-time predictions,Hajek primary,caps none/20/10/5,B1000,threshold20; S5-13 eligible positive-overlap per-notification30min loss estimand; S5-14 separate regret/modelSaved/delayCost/DRsaved; S5-15 importance from4B only; S5-16 unknown notifCat not itself never-hold; S5-17 median/p95<10ms dev JVM only. Approval: pending review for all final policy choices.

D56-1 in-memory flags, D56-2 Keystore encryption, D56-3 OFF/DRY_RUN/live-own-test progression, D56-4 hold estimates, D56-5 target app order and D56-6 case-by-case contract changes remain owner decisions for Stage 6; none performed here. Contract exposes flags without authorizing text-reader edits.

## Proposed milestones

5.0 decision types/facts/defaults/settings/decision/record/provider/default-provider/digest schedule, safety KillSwitchControl/ExpiryPolicy, two checks plus lock and tests. 5A cost/selector/hold estimator. 5B neverhold/failopen/diagnostics/recheck/expiry. 5C importance/predictor adapters,exploration,explanation,pipeline. 5D exploration records,IPW/DR/bootstrap/diagnostics/regret/savings. 5E verdict,urgent,latency,full gate. See supplied guide for exact files. 5A–5E not_started; Phase0 in_progress.

## Cross-stage requests

Stage4B commit is cc41cce; its ImportanceModel and learning APIs are now available for subsequent reviewed integration. Keep ImportanceModel read-only to Stage5 even though its package lies under decision. See the committed-API section for concurrency and documentation follow-ups. Owner: review Phase0 contract and merge it plus the common/guide docs before creating either implementation worktree. Shared governance is unchanged. Nothing verified on target.

## Contract change requests

Initial sketch deltas required by guide: identity keyHash/postedTsMs in Decision; pShow computed; timezone argument on DigestSchedule; KillSource/KillSwitchControl/listener/ExpiryPolicy/default provider. Phase0 defaults for explorationEnabled=false,strictSession=false,empty allowlist,seed1 are conservative explicit assumptions; default modeOFF. Confidence in snapshotSummary is context classification confidence, not a probability that the twin prediction is correct.

## Phase 0 implemented artifacts and verification

Created the requested 11 Kotlin contract/helper files, 2 JVM test files, docs/DECISION_CONTRACT.md, both check scripts and tools/decision-contract.lock. Root package.json changed only by adding check:decision. Contract candidate version1 locks12 files (11 Kotlin+documentation), with LF-normalized SHA256. No ImportanceModel file is copied, modified or introduced. No Stage6 worktree, main modification, integration or push.

Actual commands and output so far:
```
npm ci --offline --ignore-scripts
added 6 packages, and audited 7 packages in 2s
found 0 vulnerabilities

node tools/check-decision-purity.mjs --self-test
decision-purity self-test: PASS (9 planted violations)
node tools/check-decision-contract.mjs --self-test
decision-contract self-test: PASS (planted mutation rejected)
npm run check:decision
decision-purity: PASS (11 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
```

JDK directly verified in this session: Java21.0.11 LTS (21.0.11+9-LTS-211). Build targets JVM17; no JDK17 runtime claim.

First Gradle invocation from isolated root:
```
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME='C:\Users\abhig\AppData\Local\Android\Sdk'
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest --tests 'com.dhyan.app.twin.decision.*' --tests 'com.dhyan.app.safety.*' --tests 'com.dhyan.app.twin.contract.*'
```
Production Kotlin compilation succeeded with existing deprecation warnings in collectors and a preexisting GenEvent copy-visibility warning. No out-of-scope fix attempted. Test compile could not resolve the existing declared kotlinx-coroutines-test1.10.2 in offline cache. Actual summary:
```
> Task :app:compileDebugUnitTestKotlin FAILED
No cached version of org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2 available for offline mode.
BUILD FAILED in 59s
23 actionable tasks: 23 executed
```
Retry runs the identical command without --offline; no Gradle/dependency change. Export tests are included only to produce the snapshots required by check:twin-contract. Full suite and device tests are not claimed. ContextBucketer actually lives in twin/context/ContextBucket.kt.

### Final Phase 0 results (this session)

Retry command: identical Gradle invocation above WITHOUT --offline. Actual exit0 summary:
```
BUILD SUCCESSFUL in 31s
25 actionable tasks: 3 executed, 22 up-to-date
```
Actual JUnit XML: KillSwitchContractTest2, DecisionContractTest6, ContractExportTest1; total9 tests, failures0, errors0. Eight new contract tests plus one existing twin export test. The full Kotlin test suite was not executed. Existing persona test compilation warnings about unnecessary !! were not changed.

```
npm run check:decision
decision-purity: PASS (11 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
npm run check:twin
twin-purity: PASS (164 twin file(s); absent modules are not target verification)
npm run check:twin-contract
twin-contract: PASS (7 snapshot(s) validated against schema; TwinConfig defaults match Appendix A)
```

Both self-tests passed earlier in this session. git diff --check passed. Root package diff adds exactly one key. Prepared guide/AGENTS changes are explicit Phase0 setup exceptions to the steady-state Stage5 owned-path list. No core-spec, other twin implementation, collector, Gradle, manifest, database, UI or governance edits. No push or merge. Main checkout remains on its active branch; the separate contract branch did not modify the other checkout (4B has since been committed).

Status ledger: 5-PRE implemented (findings documented); 5.0 tested within the exact scope above; 5A/5B/5C/5D/5E not_started. Contract is a review candidate, not yet an owner-frozen baseline. Runtime safety/pipeline/exploration/estimators have not been built. Stage4B is now committed at cc41cce; this branch has not yet been integrated with that commit. No substitute importance implementation created. Hardware/cloud/on-device latency: not verified on target.

Inputs for Stage6 now: the safe AlwaysAllowDecisionProvider can never hold. Only after owner review/merge, base both future implementation worktrees on this exact Phase0 commit and use the locked public types. Stage6's fake provider must stay in test/debug sources. No actual DecisionPipeline constructor or estimator exists yet. No claim of completed Stage5.

Safety matrix rows handed to Stage6: process restart/reboot persistence; low battery/background restrictions; listener loss; scheduler/storage failure; no-network operation; real actuation confirmation and restoration; identity race during platform actuation. Write acceptance tests and perform owner-run target checks as required by the shared guide. Final reason registry pending5B/C; only no_provider and identity_mismatch are defined by the Phase0 default/factory.

STOP: review and merge the Phase 0 contract before creating Stage5/Stage6 implementation worktrees. This stop comes from docs/STAGE5_GUIDE.md Prompt5.0, not a dependency on device access. Do not run 5A or Stage6 implementation against an unreviewed moving contract.

## Requested pre-freeze contract amendments — 2026-10-25

Owner explicitly requested these edits before review/merge, retaining DECISION_CONTRACT_VERSION=1. No pipeline or Stage5A work. Only existing contract files, their tests, DECISION_CONTRACT.md, decision-contract.lock and these notes are edited.

- Kill-switch activate/deactivate/restore now clamp actual state-change and restore timestamps with max(0,value), including Long.MIN_VALUE. Idempotent operations keep their previous timestamp. Listener failures including AssertionError are isolated/count-only, so listener throwables cannot escape a transition. Added negative-time/boot-restore/idempotence/listener-error tests.
- PolicySettings adds trailing digestIntervalMin=PolicyDefaults.DIGEST_INTERVAL_MIN. Existing positional calls remain source-compatible. Costing and Stage6 release scheduling must both pass this interval and the same explicit zone into DigestSchedule.
- Decision/DecisionRecord add trailing reasonCode="unspecified"; factory copies it. AlwaysAllowDecisionProvider emits no_provider with safetyReason=null. safetyReason remains reserved for specific never-hold rules. Reason codes are controlled policy tokens, never text or exception messages. Added old-constructor/default/custom-setting and factory-copy tests.
- Replaced stale untracked-4B findings above with the committed source signature and precise feedback-state concurrency/privacy limits. STAGE4B_NOTES at cc41cce lacks the requested named API section; its existing model discussion plus committed source were reviewed. No 4B files changed or copied. This branch remains based on 4c944a4; its full suite will not claim tests from later main-only 4B/UI commits.

Reviewed the Kotlin, test and documentation changes before regenerating the lock. Deliberately ran the old lock against the amendments first; actual expected output:
```
node tools/check-decision-contract.mjs
decision-contract: FAIL frozen_contract_changed
```
Then regenerated only listed-file hashes, kept version1 and the same12 locked paths. Actual output:
```
Reviewed amendment lock regenerated; version remains 1.
npm run check:decision
decision-purity: PASS (11 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
```
No unrelated files or tools were changed. No main merge, rebase or push. Not verified on target.

### Amendment verification — targeted tests

Executed from C:/Users/abhig/OneDrive/Desktop/dhyan-contract-5-0 with JAVA_HOME=C:/Program Files/Java/jdk-21.0.11 and ANDROID_HOME=C:/Users/abhig/AppData/Local/Android/Sdk:
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest --tests 'com.dhyan.app.twin.decision.*' --tests 'com.dhyan.app.safety.*'

BUILD SUCCESSFUL in 1m 5s
25 actionable tasks: 6 executed, 19 up-to-date
```
Actual XML: KillSwitchContractTest2; KillSwitchNegativeTimeTest4; DecisionContractTest6; DecisionContractAmendmentTest3. Total15 tests, failures0, errors0. Custom interval test additionally distinguishes an hourly boundary from the 120-minute default; the subsequent full suite recompiles any final test-source edits. No claim of Stage4B tests in this unmerged baseline.

### Amendment verification — full suite and final checks

Executed the full unit suite exactly once in this amendment session, from the isolated contract worktree, using the same explicit JDK/SDK/cache environment as the targeted command:
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 17s
25 actionable tasks: 2 executed, 23 up-to-date
```
The task testDebugUnitTest executed; the final interval test edit was compiled in this run. Fresh JUnit XML totals:
```
Full branch JUnit XML totals: {"tests":118,"failures":0,"errors":0,"skipped":0,"suites":27}
```
These are all tests available on contract-5-0's Stage4A baseline plus contract tests, not an integrated rerun of subsequent main-only Stage4B/UI changes. Stage4B source/API was inspected read-only as requested. The reviewed merge must still run integration checks on its resulting tree.

Requested final commands and exact summary lines, all exit0:
```
npm run check:decision
decision-purity: PASS (11 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)

npm run check:twin
twin-purity: PASS (166 twin file(s); absent modules are not target verification)

npm run check:twin-contract
twin-contract: PASS (7 snapshot(s) validated against schema; TwinConfig defaults match Appendix A)
```

Reviewed the regenerated lock diff: same12 paths and version1, only the5 changed Kotlin contract-file hashes plus contract documentation hash changed. DecisionContract.kt/DECISION_CONTRACT_VERSION itself is unchanged. git diff --check passed. No pipeline, new dependency, package script change, main/4B edit, merge or push in this amendment. The missing named Stage4B notes subsection and importance feedback snapshot-consistency caveat are documented above, not silently worked around. Not verified on target.

Amendment status: implemented and tested on contract-5-0, ready for owner review. Commit message: stage5: 5.0 contract amendments.

STOP: waiting for my review (owner request).

## Stage 5A start — reviewed Phase 0 merged

Owner's go-ahead approved the amended Phase0 contract. Merged contract-5-0 into main at 99eee1d, then created isolated C:/Users/abhig/OneDrive/Desktop/dhyan-s5 on stage5-decision from that merge. The existing uncommitted ImportanceModel.kt and ImportanceModelTest.kt in Antigravity's main checkout were SHA256-checked before/after merge and remained byte-identical; they were not staged, copied, reverted or committed. STAGE4B_NOTES.md also had live edits by the other tool; the contract merge does not alter that path. No push. The reviewed version1 contract and lock are now frozen. No further main edits by Stage5 implementation.

5A creates only CostFunction.kt, ActionSelector.kt, HoldTimeEstimator.kt, CostAndHoldTest.kt, docs/DECISION_POLICY.md and updates these notes. Default constants and DigestSchedule are consumed read-only. No pipeline, safety-rule implementation, exploration or estimator built in this milestone. Stage5B–5E remain not_started.

Policy details: near-ties use global-min tolerance1e-9 before least-intrusive selection; margin clamps at0 if the chosen least-intrusive action is fractionally above minimum. Mathematical SILENT/DIGEST crossover remains .16, with numerical tie tolerance applied. Hold release table15/30/45 has break/away precedence over deep/flowing on contradictory context. Negative/nonfinite input and arithmetic overflow are rejected internally for later fail-open wrapping. Digest interval comes from settings and zone is explicit, shared with the Stage6 scheduler. Assumptions/provenance documented in DECISION_POLICY.md. Not verified on target.

### Stage 5A actual verification

JDK/SDK environment and Gradle cache path are the same explicit values documented above. From C:/Users/abhig/OneDrive/Desktop/dhyan-s5:
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest --tests 'com.dhyan.app.twin.decision.*'
BUILD SUCCESSFUL in 1m 34s
25 actionable tasks: 25 executed
```
Actual JUnit XML: CostAndHoldTest9, DecisionContractAmendmentTest3, DecisionContractTest6, committed ImportanceModelTest7: total25 tests, failures0, errors0. Property test covers seeds1/42/2026, 3000cases each. Existing collector/persona/episode compiler warnings were observed and not edited.

```
npm ci --offline --ignore-scripts
added 6 packages, and audited 7 packages in 11s
found 0 vulnerabilities
npm run check:decision
decision-purity: PASS (15 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
npm run checks
schema-sync: PASS (1.0.0; 1 Kotlin version file(s))
twin-purity: PASS (72 twin file(s); absent modules are not target verification)
no-persona-imports: PASS (201 production file(s); absent modules are not target verification)
```

Additional root npm test was actually run after the Phase0 merge. It did not fully pass:
```
tests 55
pass 54
fail 1
cancelled 0
skipped 0
todo 0
duration_ms 2203.0602
```
Failure: tools/test/core-spec.test.mjs:141, root hygiene assertion compares exact LF .env.example contents, but Git autocrlf produced CRLF in this fresh Windows worktree. No functional difference or secret value involved. This is outside Stage5 owned files: neither .env.example nor core-spec.test.mjs changed. Cross-stage request to repository owner: make the fixture checkout/test portable across line endings (e.g. explicitly preserve LF or compare normalized newlines), with acceptance npm test55/55 on a fresh Windows worktree. Do not claim all post-merge checks are green until this is resolved. Stage5A's decision tests and contract checks are independently passing.

### Stage 5A full merged-baseline validation

Executed full unit suite after the reviewed Phase0 merge and 5A implementation, against committed Stage4B plus contract plus5A (no live Antigravity edits copied):
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest
BUILD SUCCESSFUL in 17s
25 actionable tasks: 1 executed, 24 up-to-date
Full merged-baseline JUnit totals: {"tests":143,"failures":0,"errors":0,"skipped":0,"suites":32}
```
The unit-test task executed (not merely UP-TO-DATE). Final required/additional checks all exit0:
```
npm run check:decision
decision-purity: PASS (15 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
npm run check:twin
twin-purity: PASS (217 twin file(s); absent modules are not target verification)
npm run check:twin-contract
twin-contract: PASS (7 snapshot(s) validated against schema; TwinConfig defaults match Appendix A)
npm run check:personas
check-personas-purity PASS -- 22 file(s) scanned
check:persona-data PASS -- 5 fixture(s), 12567 events
```
Persona fixtures: Anika MATCHED3035; Kabir MATCHED5335; Rahul MATCHED RandomizedHold779; Rahul MATCHED ShowAll2634; Rahul MILD_MISSPEC784. These are seeded synthetic validation artifacts, not real-user results. Twin guard counts generated files too, hence different counts before/after full build.

The root test's dry-run CLI rewrote the tracked synthetic sample as a side effect; restored only that generated sample to its exact baseline with git restore --worktree -- tools/dataset/out/notifications.sample.jsonl. It is not part of the5A diff. No out-of-scope functional change. Root55-test suite still has the separately recorded LF/CRLF hygiene failure54/55; not silently fixed or called green.

Status: reviewed contract frozen at merge99eee1d;5A implemented and tested;5B–5E not_started. Six5A owned files only (3implementation,1test,policy doc,these notes). Frozen contract and lock unchanged, verified by check:decision. No Stage5 implementation merge or push. No device/actuation/latency claim: not verified on target. Next prompt5B is safety module; root line-ending issue remains a repository-owner cross-stage request.

## Stage 5B — safety implementation

Owned additions: safety/NeverHoldRules.kt,FailOpen.kt,DecisionDiagnostics.kt,DefaultExpiryPolicy.kt,RecheckLogic.kt plus internal SafetyValidation.kt for numeric/metadata checks shared with future5C; tests SafetyFixtures.kt,SafetyRulesTest.kt,RecheckLogicTest.kt,SafetyMatrixTest.kt; updates only DECISION_POLICY.md and these notes. Frozen contract, version1 lock, defaults and kill-switch implementation untouched. No DecisionPipeline, adapters, Android, Room, clock, content reader or new dependency.

Cross-stage request (critical before LIVE): actual Stage2 ingest/Normalizer.kt private classifyAndroidCategory collapses alarms/reminders into alert and does not preserve call/navigation. There is no NotifExtractor.kt on this committed baseline (the initial lookup failed, then actual Normalizer was located). Core-spec notifCat is open, with message in its sample, not a fixed enum. 5B tests explicit call/alarm/navigation facts; it cannot recover erased metadata or claim end-to-end never-hold. Stage6 must preserve/forward critical category metadata or request an explicitly approved conservative fallback before live suppression. No out-of-scope normalizer/collector/schema edit here. Unknown category tokens alone remain non-never-hold per S5-16; malformed numeric facts fail open. Semantic ambiguity not represented in the frozen facts needs fail-open handling by the later integration/classification layer.

S5-8 expiry assumptions10min/24h implemented without effect: clamp negative timestamps0, saturate overflow. Recheck validates settings/facts/hold parameters and compares nonnegative epoch times without subtract-overflow. Extra internal validation helper is a coherent safety addition, not a contract change. FailOpen strips exception messages; diagnostics accept only enum counter keys and numbers. No reason text derived from arbitrary raw input. NeverHoldRules priority and category mapping documented in DECISION_POLICY.md.

### Safety matrix rows handed to Stage 6

These are explicit acceptance tasks, not ignored/skipped tests and not claimed executed here:

| Row | Stage6 acceptance test |
|---|---|
| Low battery/background restrictions | Simulate restricted listener/worker availability; no new suppression, visible degraded state and safe release/surface of already held items. Owner verifies on target. |
| Process restart | Persist safe policy/kill state, simulate process death with held records, restore once without duplicate effects; uncertain restoration surfaced. |
| Reboot | Restore kill state/held metadata and pending release work; no new holds during recovery, no silent loss or duplicate repost. Owner-run reboot check. |
| Storage failure | Inject failing DecisionLogStore/held-record writes; abort new actuation, surface failure and release/surface existing held items. |
| Scheduler failure | Reject scheduling and inject worker failure; no new hold if a release cannot be guaranteed, visible status and recovery. |
| No network | Disconnect relay/network; explicitly mark remote context stale/offline, never fabricate active laptop focus, keep local safety/release/Show-now working. |
| Category information loss | Inject platform call/alarm/navigation through real collector/normalizer/facts adapter; prove explicit critical facts reach both precheck/recheck before any LIVE actuation. |

Pure matrix tests are helper-level; they do not pretend the not-yet-built pipeline or platform paths are tested. Existing root LF/CRLF hygiene issue from5A remains open and is not rerun/changed as part of5B.

### Stage 5B verification (actual, this session)

From C:/Users/abhig/OneDrive/Desktop/dhyan-s5 with JAVA_HOME=C:/Program Files/Java/jdk-21.0.11 and ANDROID_HOME=C:/Users/abhig/AppData/Local/Android/Sdk:
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest --tests 'com.dhyan.app.safety.*'
```
Initial run:
```
BUILD SUCCESSFUL in 27s
25 actionable tasks: 6 executed, 19 up-to-date
```
After adding the malformed eligibleActions-collection exception regression, final run:
```
BUILD SUCCESSFUL in 36s
25 actionable tasks: 2 executed, 23 up-to-date
```
The final test task executed and recompiled the updated tests; no UP-TO-DATE-only success claim. A subsequent source-comment clarification directs Stage6 to the frozen DecisionProvider.recheck interface, not the internal implementation helper; no functional changes after tests.

```
npm run check:decision
decision-purity: PASS (21 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)
```

Final safety JUnit XML totals: {"tests":27,"failures":0,"errors":0,"skipped":0,"suites":5}

Coverage: 27 safety tests across5 suites (existing kill-switch6, new rule/diagnostic/expiry6, recheck8, matrix7). Atomic diagnostic test verifies20000 increments; rule matrix covers8 critical fact variants x3 modes x2kill states. These are bounded synthetic helper tests, not a universal urgent-hold guarantee or platform test. Full Kotlin/root suites were not rerun in5B;5A's full143-test result is historical, and its root54/55 LF/CRLF issue remains open. No new exception messages or arbitrary category values are persisted or logged by the helpers.

Status ledger:5A tested (prior milestone);5B implemented/tested within the scope above;5C–5E not_started. Frozen contract/defaults/version1 lock unchanged. No pipeline/Stage6 integration, device check, Stage5 implementation merge or push. Not verified on target. Commit message: stage5: 5B safety.

## Stage 5C — pipeline

Implemented LossPredictor/TwinEngineLossPredictor, ImportanceProvider/ModelImportanceProvider, ExplorationPolicy/PolicyRng, Explainer, DecisionPipeline and controlled tests. The new non-frozen LossPredictor API passes (snapshot,facts,nowMs,seed), rather than only the first two proposed parameters: necessary to inject explicit time/seed into the real engine without touching its ambient-clock default or introducing shared mutable clocks. ContextBucketer is used inside the real TwinEngine. Stage6 must depend on frozen DecisionProvider, not this implementation helper interface.

Importance adapter calls the committed model with rawTextHint=null; it does not update feedback. External learning must provide a stable feedback boundary for repeatability (the existing 4B model has no atomic versioned importance snapshot). No model/4B edits. Caller must serialize inference with feedback updates when evaluating an identical policy/model state; this remains an integration requirement, not a claim of determinism during concurrent learning.

Pipeline order: OFF/kill, validation, never-hold, importance/high importance, stale-context check/predictor, prediction validation, low evidence, costs, exploration, explanation. Unknown appCat or explicit other classification fails open after never-hold. All exceptions are guarded, reasons are controlled, no content is used. Risk/evidence boundary .5 and .85 are tested. OFF does not simulate; DRY_RUN computes identical decisions to LIVE without actuator effects. Strict halves cost kappa and disables exploration. Exploration epsilon0/1 reports only actually possible actions; intermediate support has ALLOW and the selected hold. Draw is idempotent by seed/keyHash/posted time.

Actual 5C final decision-test command: same isolated offline Gradle invocation, --tests com.dhyan.app.twin.decision.*.
```
BUILD SUCCESSFUL in 35s
25 actionable tasks: 6 executed, 19 up-to-date
```
Tests include real loaded-prior/unloaded-store adapters, override short circuits, exception canaries, deterministic exploration rate over10000facts, and a real-engine dev-JVM smoke benchmark (not Reno14). Detailed outputs are appended from XML below. Earlier baseline run without the final benchmark/finite-factor guard also passed in46s. 5D source preparation may exist uncommitted in the worktree during this check; only5C paths are committed in this milestone. Frozen contract/lock unchanged. No push or integration with Android. Not verified on target.

5C dev JVM real-twin latency: median=0.645 ms; p95=1.0932 ms; n=300; NOT a Reno 14 result

5C decision suite: 33 tests,0 failures (actual successful run).

## Stage 5D — estimators

Implemented ExplorationRecord/labels/reports, Hajek IPW, HT, DR, exclusive filters, bootstrapB1000, overlap/ESS/top-weight share, caps none20/10/5, threshold20 with both arms, regret and separately labelled savings. Seeded validation covers hand arithmetic, correct-p/biased-model DR, wrong-p/correct-model DR, both-wrong documented bias, coverage, filters and finite persona histories. No real-data causal claim or user-day benefit. Input/output semantics and window caveats are in DECISION_POLICY.md.

Cross-stage limitation: actual4A TrueEffect is a constant-hazard reference; generator has hourly hazard modulation. Test prints both and validates estimates against actual finite generated counterfactuals, not an assumed identical reference. No4A/twin edits or tuning to pitch. Production episode Y must be consistently clipped-at-W or complete-onset-in-W across labels/predictions before real estimates are meaningful. Stage4B importance mutation/snapshot requirement remains as documented; pipeline uses stable feedback state for deterministic tests.

### Stage 5D executed results

Command: isolated offline Gradle :app:testDebugUnitTest --tests com.dhyan.app.twin.counterfactual.* --tests com.dhyan.app.twin.decision.* (explicit JDK21.0.11/SDK/cache as above).
Initial run: BUILD SUCCESSFUL in28s;25 actionable tasks:8executed,17up-to-date. After numerical-overflow guards/ESS scaling and preparing the5E tests, final run:
```
BUILD SUCCESSFUL in 27s
25 actionable tasks: 6 executed, 19 up-to-date
```
The prepared5E decision classes are also exercised by this package filter if present; their final gate record follows separately. Frozen contract unchanged. Actual estimator prints follow (copied from JUnit stdout, not invented):

```text
DR validation wrongP=false wrongModel=false constantP=true: IPWbias=0.009113365125426398, DRbias=0.009669389334824707
DR validation wrongP=false wrongModel=true constantP=false: IPWbias=-0.010764320484086376, DRbias=0.0037529025261132296
DR validation wrongP=true wrongModel=false constantP=false: IPWbias=1.4941541258003121, DRbias=0.002321856090278048
DR validation wrongP=true wrongModel=true constantP=false: IPWbias=1.4941541258003121, DRbias=-0.4344314772430563
80% bootstrap interval coverage sanity: 18/20 fixed-seed replications
```

```text
persona | n | history true delta | IPW | DR | 80% bootstrap interval | TrueEffect constant-hazard reference
Rahul | 211 | 6.378229804582898 | 6.1449500558614165 | 5.439526778495669 | 1.9668149134771418..9.444465093700288 | 7.692803885230261
Kabir | 169 | 0.44971958585486166 | 2.709975220875732 | 2.7569760509542722 | 0.0937616362374171..5.302278892398519 | 0.6229584915876868
```

Final combined package run: 44 tests,0 failures; estimator-specific7 tests passed. No target hardware validation.

## Stage 5E final gate — scope and implementation

5E adds the ten-row VerdictSheetTest with formula-derived expected actions; UrgentSuiteTest42cases across216configurations; EndToEndSheetTest using actual4A histories, Stage4B EventStreamAdapter/EpisodeBuilder/EpisodeCloser and real TwinEngine; DecisionLatencyBenchmark with500warmups+2000measuredcalls. Evaluation timestamps are strictly after the end of both training histories, not inside the training period. Fixed test seed choices were declared before results; no pitch-value tuning.

Final edge-case fixes within owned implementation paths: optional missing pickup mean and missing explanation metadata are omitted; direct exploration helper checks shared never-hold rules; pipeline unknown-category classification fails open (while unknown remains not a never-hold rule); savings rejects duplicate lifecycle rows. SavedMinutes.fromReports computes DR-supported totals only for confirmed holds matching ESTIMATED buckets and open-overlap action support; any unsupported confirmed row yields null estimated total, never a silently partial causal-savings claim. The lower-level summarize helper can accept a caller-computed trusted total; fromReports is the preferred identified-report adapter.

No frozen contract/source hashes changed. No root package, core-spec, Gradle, collector, data, UI, actuator, persona or other twin implementation edits. No Stage5 merge or push. Scope is local pure-Kotlin decision/estimation implementation, not Android live integration.

### Assumptions S5-1 through S5-17 — final values and review status

| ID | Final value/meaning | Owner approval/review |
|---|---|---|
| S5-1 | ALLOW,DELAY,DIGEST,SILENT within1e-9 of global minimum; margin clamps0 on numeric tie | Build authorized per guide; final policy review pending |
| S5-2 | Release15min break/recovering/away/idle;45deep/flow;otherwise30, capped digest | Assumed table per guide; not learned or target verified |
| S5-3 | Local-midnight digest grid, settings.digestIntervalMin120default; same explicit zone in costing/scheduling | Frozen contractv1 approved |
| S5-4 | OFF skips simulation; DRY_RUN computes but recheck blocks actuation | Frozen semantics approved |
| S5-5 | Explore hold->ALLOW only in documented low-importance positive-delta region | Build authorized per guide; measured eligibility/rate tests |
| S5-6 | SHOW=ALLOW; SUPPRESS=DELAY/DIGEST/SILENT; exact after-filter support/propensity | Frozen contractv1 approved |
| S5-7 | Recheck age <=30000ms inclusive; identity, mode, kill and safety rechecked | Frozen default approved; tests cover edges |
| S5-8 | OTP10min;other24h; clamp negative start and saturate overflow | Guide assumption implemented; Stage6 expiry effects pending |
| S5-9 | Top2 ranked heuristic factors; fixed templates; missing metadata skipped | Not causal attribution; policy review pending |
| S5-10 | Evidence strength<.4 AND I>.5 ->ALLOW | Guide policy implemented/tested |
| S5-11 | Strict session halves kappaD and disables exploration | Guide policy implemented/tested |
| S5-12 | Decision-time predictions; Hajek primary,HT+DR;caps none/20/10/5;B1000;20usable+botharms threshold | Estimator assumptions documented; tests passed |
| S5-13 | Eligible positive-overlap per-notification effect; opportunity W30min, consistent supplied Y required | Production clipped vs complete-onset outcome mapping pending |
| S5-14 | Regret from computed J; signed model saved, estimated delay cost and supported DR total kept separate | No real-user saved-minute claim |
| S5-15 | Read-only committed4B model, no rawTextHint; mutable feedback state outside decide | Stable feedback/version boundary is an integration requirement |
| S5-16 | Unknown notifCat is not never-hold; unknown pipeline classification fails open separately | Conservative fail-open implementation; new mappings require review |
| S5-17 | Warm real-engine dev-JVM median/p95<10ms, at least2000calls in final benchmark | Dev tested; Reno14 not verified on target |

### Inputs for Stage 6

Use the frozen DecisionProvider type. Real implementation constructor:
```kotlin
DecisionPipeline(
    predictor = TwinEngineLossPredictor(store),
    importanceProvider = ModelImportanceProvider(importanceModel),
    holdTimeEstimator = HoldTimeEstimator(),
    exploration = ExplorationPolicy(),
    explainer = Explainer(),
    diagnostics = DecisionDiagnostics(),
    zone = userZone,
    staleSnapshotMs = ModelConfig().staleSnapshotMs
)
```
Other constructor parameters are defaulted. Time and seed are passed from decide(nowMs,settings.seed), never read from a system clock by production decision code. Store must finish asynchronous load; unloaded store yields ALLOW/failedOpen. Stage6 must allow without fabricating a focus snapshot when its snapshot source is absent. Stage8 will later supply full cross-device fusion; phone-only source must mark laptop offline/unknown honestly.

PolicySettings defaults OFF; Stage6 supplies current immutable snapshots, allow-list, seed and digestIntervalMin. Costing and ReleaseScheduler must use the same zone/interval. Before every hold/snooze/cancel call provider.recheck on fresh NotificationFacts/settings; abort means no actuation. Never act on failedOpen or ALLOW; DRY_RUN's computed hold is not a real held status. Confirmed HELD/SNOOZED requires actual platform confirmation. DecisionLogStore remains Stage6 Room work, not called by the synchronous pipeline.

DecisionRecordFactory.from(facts,decision,snapshot,settings,importance) strips explanations/factor text and copies reasonCode/safetyReason, identity, exact propensity,pShow, support, predictions and snapshot summary. BLOCKED production logging detail: its importance must be the original decision-time score, but the frozen DecisionProvider/Decision does not expose that score. Do not recompute after feedback or invent0 for an override that never scored. See CR5-1 below. Mode/actuation status are separate from the calculated action.

For ExplorationRecord: bucketKey is a controlled context identifier; arm=SHOW for ALLOW else SUPPRESS; pShow=decision.pShow; Y is a deduplicated observed episode outcome with agreed window semantics; censored/actuationFailed/eligible must be mapped honestly from episode and actuation state, and predictions captured at decision time. Safety-bypassed/high-importance/strict/deterministic-no-overlap rows must not be relabelled eligible. Real Room mapping is Stage6/10 work. CounterfactualEngine.estimate filters and counts invalid/unsupported rows. SavedMinutes.fromReports requires matching identified bucket reports, confirmed unique lifecycle-final decisions and valid open overlap; unsupported total is null, not0.

Fixed reason codes: mode_off,kill_switch,invalid_input,never_hold,high_importance,not_ready,low_evidence,cost_selected,explored,no_provider; exception:<SimpleClassName> is a code-only namespace (no messages). Specific safetyReason:call,alarm,navigation,otp,emergency,vip,allowlist. Recheck aborts:mode_not_live,kill_switch,allow,failed_open,never_hold,identity_mismatch,decision_expired,clock_backwards,invalid_input,exception. Diagnostics keys are fixed numeric counters defined in DecisionDiagnostics.kt. policyVersion=decision-1-policy-1, POLICY_REVISION1, DECISION_CONTRACT_VERSION1.

### Cross-stage requests remaining (not silently fixed)

1. Stage6 must preserve actual call/alarm/navigation metadata and supply OTP/emergency in-memory flags before LIVE suppression; Stage2 normalizer currently loses some distinctions. Never infer that5B/5E synthetic safety tests validate platform classification.
2. ImportanceModel feedback and decision evaluation need a stable serialized/versioned state boundary. The real4B API uses separate concurrent maps, not an atomic all-feedback snapshot. Determinism is tested for stable model/feedback state, not during concurrent learning.
3. Real episode-to-record integration must agree clipped-in-W vs complete-cost-for-onset-in-W outcomes and matching model predictions; default twin/persona uses the latter. No real-data causal savings claim until this is resolved.
4. TrueEffect constant-hazard helper differs from the hourly-modulated generator. Tables show the difference openly and tests use actual per-notification hidden truth for finite-fixture validation. Reconciling that reference helper belongs to4A owner; no persona edits/tuning here.
5. Root npm test54/55 LF/CRLF hygiene failure from5A remains open; not fixed outside ownership and not rerun in5C–5E. Required Stage5 gate commands are separately verified below. Do not call the whole repository globally green.
6. Stage6 supplies scheduling/storage-failure handling, process/reboot recovery, platform timeout/confirmation, end-to-end canary privacy and Reno14 compatibility/latency checks. All are not verified on target here. No real notifications or cloud services were touched.

### Contract change requests

CR5-1 — faithful importance logging (additive Decision field, nullable record field). The frozen DecisionProvider returns Decision without importance, while DecisionRecordFactory requires a non-null importance argument. Production Stage6 cannot obtain the score actually used through the frozen interface, and never-hold/OFF/kill overrides intentionally do not score at all. Recomputing later can change after feedback; supplying0 would invent data. This blocks that part of production record wiring, not numerical pipeline/estimator implementation. No frozen source or lock was changed.

Proposed reviewable change for a separate approved Phase0b commit on main: append Decision.importance:Double?=null; allow DecisionRecord.importance:Double?; default the factory argument to decision.importance while preserving old source calls with explicit scores, and reject disagreement when both are supplied. Pipeline returns the exact computed score, or null when not evaluated. Bump contract version to2 and update lock, then both Stage5/Stage6 rebase. Room nullability/migration implications are Stage6 owner work. Acceptance: updated score is recorded exactly; feedback after decide cannot alter the record; unscored safety/OFF/kill cases persist null; no recomputation and no raw text. This is a proposal only and needs owner review under the frozen-contract change protocol.

Implementation helper APIs remain outside the frozen boundary; Stage6 must depend on DecisionProvider and reviewed types, not a hidden mutable score side channel. All other cross-stage requests are listed above.

### Governance fold-in for owner after merge

TRACEABILITY.md: add exact new decision/safety/counterfactual code and test paths, with the bounded test claims below; do not mark platform classification or causal real-user benefits tested. HANDOFF_STATUS.md: record branch/commit, constructor/settings/record integration, real commands, scope and open cross-stage requests. STAGE_GATES.md: retain the printed verdict/urgent/truth/E2E/latency results and Stage6 target gates. DECISIONS_PENDING.md: fold S5assumptions, category preservation, importance-state consistency, outcome-window/reference limitations and root newline test issue. These shared documents were deliberately not edited on Stage5.

### Stage 5E executed final verification

Final full command, isolated stage5-decision worktree, explicit installed JDK21.0.11/AndroidSDK and existing cache:
```
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest
BUILD SUCCESSFUL in 32s
25 actionable tasks: 6 executed, 19 up-to-date
```
This final run includes all gate tests and the DR-savings support/duplicate safeguards. Two earlier full validation runs also succeeded (1m41s and37s) before final review additions; they are not substituted for this final run. No tolerance/seed changes were used to obtain passing results. Final printed tables below are copied directly from fresh JUnit stdout.

#### com.dhyan.app.twin.counterfactual.CounterfactualTest

```text
DR validation wrongP=false wrongModel=false constantP=true: IPWbias=0.009113365125426398, DRbias=0.009669389334824707
DR validation wrongP=false wrongModel=true constantP=false: IPWbias=-0.010764320484086376, DRbias=0.0037529025261132296
DR validation wrongP=true wrongModel=false constantP=false: IPWbias=1.4941541258003121, DRbias=0.002321856090278048
DR validation wrongP=true wrongModel=true constantP=false: IPWbias=1.4941541258003121, DRbias=-0.4344314772430563
80% bootstrap interval coverage sanity: 18/20 fixed-seed replications
```

#### com.dhyan.app.twin.counterfactual.PersonaCounterfactualTest

```text
persona | n | history true delta | IPW | DR | 80% bootstrap interval | TrueEffect constant-hazard reference
Rahul | 211 | 6.378229804582898 | 6.1449500558614165 | 5.439526778495669 | 1.9668149134771418..9.444465093700288 | 7.692803885230261
Kabir | 169 | 0.44971958585486166 | 2.709975220875732 | 2.7569760509542722 | 0.0937616362374171..5.302278892398519 | 0.6229584915876868
```

#### com.dhyan.app.twin.decision.DecisionLatencyBenchmark

```text
5E dev JVM real-twin decide latency: median=0.5492 ms; p95=1.3589 ms; n=2000; NOT a Reno 14 result
```

#### com.dhyan.app.twin.decision.EndToEndSheetTest

```text
E2E: persona | action | Eshow | Esupp | evidence strength | modelVersion
cold | DIGEST | 14.834935490947055 | 8.810863728932748 | 0.0 | 0
Rahul | DIGEST | 14.846083716983225 | 11.493166689741502 | 0.7794642408223429 | 1041
Kabir | DIGEST | 14.063118643359779 | 10.859557216836249 | 0.7518323453596796 | 1674
```

#### com.dhyan.app.twin.decision.PipelineLatencySmokeTest

```text
5C dev JVM real-twin latency: median=0.4154 ms; p95=0.6168 ms; n=300; NOT a Reno 14 result
```

#### com.dhyan.app.twin.decision.UrgentSuiteTest

```text
urgent suite: 42 cases x 216 configs = 9072 decisions, holds = 0; measured synthetic suite only
```

#### com.dhyan.app.twin.decision.VerdictSheetTest

```text
VERDICT: id | I | Eshow | Esupp | deltaL | J(ALLOW,DELAY,DIGEST,SILENT) diagnostic controls | action | reason | explanation
call | 0.2 | 1000.0 | 1.0 | 999.0 | [1000.0, 251.09, 2.04, 2.24] | ALLOW | never_hold | Allowed: a call, alarm, code or configured safety rule.
otp | 0.2 | 1000.0 | 1.0 | 999.0 | [1000.0, 251.09, 2.04, 2.24] | ALLOW | never_hold | Allowed: a call, alarm, code or configured safety rule.
vip_message | 0.2 | 1000.0 | 1.0 | 999.0 | [1000.0, 251.09, 2.04, 2.24] | ALLOW | never_hold | Allowed: a contact you marked important.
high_importance | 0.85 | 20.0 | 1.0 | 19.0 | [20.0, 7.625624999999999, 6.135, 9.585] | ALLOW | high_importance | Allowed: high importance.
social_like | 0.3 | 20.0 | 1.0 | 19.0 | [20.0, 6.2025, 2.34, 3.04] | DIGEST | cost_selected | Held: about 19.0 min of focus lost (80% simulation interval -1.0 to 40.0); you open this about 6.0 of 10 times in this context. Tap to show now.
promo | 0.1 | 20.0 | 1.0 | 19.0 | [20.0, 6.0225, 1.86, 1.56] | SILENT | cost_selected | Held: about 19.0 min of focus lost (80% simulation interval -1.0 to 40.0); you open this about 6.0 of 10 times in this context. Tap to show now.
near_break | 0.6 | 4.0 | 0.5 | 3.5 | [4.0, 1.77, 3.46, 5.66] | DELAY | cost_selected | Held: about 3.5 min of focus lost (80% simulation interval -1.0 to 40.0); you open this about 6.0 of 10 times in this context. Tap to show now.
baseline_dominated | 0.5 | 1.0 | 0.9 | 0.09999999999999998 | [1.0, 1.7125, 3.2, 4.9] | ALLOW | cost_selected | Allowed: showing has the lower expected cost.
low_evidence | 0.6 | 20.0 | 1.0 | 19.0 | [20.0, 6.81, 3.96, 6.16] | ALLOW | low_evidence | Allowed: not enough evidence yet.
stale | 0.3 | 20.0 | 1.0 | 19.0 | [20.0, 6.2025, 2.34, 3.04] | ALLOW | not_ready | Allowed: not enough information.
```

STAGE 5 FULL JUNIT: {"tests":188,"failures":0,"errors":0,"skipped":0,"suites":45}

Final repository checks (actual output):
~~~text

> check:decision
> node tools/check-decision-purity.mjs && node tools/check-decision-contract.mjs

decision-purity: PASS (34 Kotlin files)
decision-contract: PASS (version 1; 12 frozen files; 11 core defaults)

> check:twin
> node tools/check-twin-purity.mjs

twin-purity: PASS (292 twin file(s); absent modules are not target verification)

> check:twin-contract
> node tools/check-twin-contract.mjs

twin-contract: PASS (7 snapshot(s) validated against schema; TwinConfig defaults match Appendix A)

> check:personas
> node tools/check-personas-purity.mjs && node tools/check-persona-data.mjs

check-personas-purity PASS -- 22 file(s) scanned
OK  anika_MATCHED_ShowAll_seed20261025.events.jsonl (3035 events)
OK  kabir_MATCHED_ShowAll_seed20261025.events.jsonl (5335 events)
OK  rahul_MATCHED_RandomizedHold_seed20261025.events.jsonl (779 events)
OK  rahul_MATCHED_ShowAll_seed20261025.events.jsonl (2634 events)
OK  rahul_MILD_MISSPEC_ShowAll_seed20261025.events.jsonl (784 events)

check:persona-data PASS -- 5 fixture(s), 12567 events
~~~

CR5-2 — reproducible record metadata (review with CR5-1). Frozen DecisionRecord also omits settings.seed and original caution/strict/digest/zone inputs, while the common guide requires logging the exploration seed and later regret/delay-cost accounting needs original policy inputs. Numerical helpers are implemented, but real historical reconstruction must not substitute current settings. Proposed separate contractv2 additions: nullable captured policySeed, kappaDPerHour, strictSession,digestIntervalMin,zoneId and relevant tau/cost terms (or one typed optional evaluation snapshot) in the decision-time result/record, copied atomically by the factory. Bypassed/uncomputed costs stay null. Owner may approve a smaller shape if an equally explicit immutable decision-time record path exists. Acceptance: changing settings after decide cannot change the persisted seed, importance or cost inputs; historical regret/delay cost can be reproduced without re-running a newer model. No hidden side channel or mutable global score/settings read is proposed. No frozen changes made here.

### Final status ledger and practical limits

| Milestone | Status | Evidence/scope |
|---|---|---|
| 5.0 | tested/frozen v1 | Reviewed merge99eee1d; hash checks unchanged |
| 5A | tested | Formula, boundary, selector and hold-time tests; f951db7 |
| 5B | tested | Safety, fail-open, recheck, expiry and matrix tests; e7e08aa |
| 5C | tested | Real adapters/pipeline/exploration/explanations;9b8f1fa plus final owned fixes |
| 5D | tested | Numerical estimators, finite synthetic truth, diagnostics/regret/savings;3f035a4 plus final owned fixes |
| 5E local gate | tested | Final full188tests,0failures/errors/skips; all4required npm gate checks pass; printed tables above |
| Production decision-record wiring | blocked | CR5-1/CR5-2 require owner-reviewed contract change; no fabricated importance/settings |
| Stage6 platform safety/actuation | not_started here | Category preservation, flags, scheduler/storage/recovery/compatibility and device tests belong to Stage6 |

Warm dev-JVM benchmark is not a Reno14 result. Urgent42case x216config suite reports9072decisions/0holds for that synthetic suite only, not a universal guarantee. Ten-row controlled sheet triggers every action; real-engine cold/trained sheet is model output, not observed benefit. TrueEffect reference and full-cost/window caveats remain explicit. Existing root55-test hygiene issue is still not globally resolved; no claim that every repository check passed. No hardware, cloud, native notification or real-data causal validation. Not verified on target.

The requested5C/5D/5E implementation and local test work is ready for review. No merge/push. Stage6 real logging/production integration must respect the documented contract requests and remaining gates rather than treating a local pass as authorization for LIVE suppression.
