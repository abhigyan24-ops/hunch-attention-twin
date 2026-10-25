# Stage 5 preflight and Phase 0 — 2026-10-25

The owner instructed starting Stage 5 while Antigravity finishes Stage 4B. Isolated contract-5-0 worktree based on committed main 4c944a4. Only independent Phase 0 types and helpers are prepared. No Stage 5 pipeline, importance replacement, Stage 6 actuation or merge/push is authorized by this work. Stage 4B ImportanceModel remains untracked in the original checkout; its mutable API cannot be treated as merged. No work copied from Antigravity.

## Prerequisites and scope

Read AGENTS, HANDOFF_STATUS, DECISIONS_PENDING, STAGE3_NOTES, STAGE4A_NOTES, docs/SPEC sections 8.5,9,10,25.5–25.7 and read-only Stage 3 APIs. The two supplied guides are copied verbatim to docs in this branch. The common guide asks for these on main; to preserve active main work they are committed here for owner review/merge. No Stage 5 or Stage 6 implementation worktree is created before the contract merge; this is the short Phase 0 contract checkout.

Package com.dhyan.app. Kotlin2.3.10, AGP9.2.1; JVM compilation target17, installed JDK21.0.11 used previously (must verify in this session before build). Coroutines core/test explicitly declared in phone/app/build.gradle.kts. No new dependencies.

TwinEngine.simulate(snapshot: ContextSnapshot, notif: NotificationInput, seed: Long=42): SimOutcome. Ready(result: SimulationResult); NotReady reasons MODEL_NOT_LOADED, INVALID_SNAPSHOT, STALE_CONTEXT. The engine accepts an injected clock, default wall clock: Stage 5 must construct/use it with passed time, not let the default clock affect decisions. Future timestamps and NaNs require pipeline validation because existing engine validation is incomplete.

SimulationResult: show/suppress ArmStats(mean,p10,p50,p90,pLossGt1Min), deltaLMean, deltaL80SimIntervalLow/High, deltaLStdError, pickupPosterior80Low/High, nEff, evidenceStrength, modelVersion, seed, configVersion, n, pickupHierarchyLevel, hazardHierarchyLevel, ownerFractions, lShow/lSuppress/deltaL arrays, owner byte array, pPickupSample array. No direct provenance or pickup mean; derive simulated/low evidence and mean from available samples without changing twin API.

ContextSnapshot in twin/model/ContextSnapshot.kt: ts,focusState,taskType,taskSource,confidence,hourBand,minutesInState,fatigueBand,recentInterrupts30m,laptopState,phoneState,secondScreenOverlap,declaredSession. Enums follow canonical context schema. NotificationInput has appCat,nullable notifCat,isVip,urgencyScore,repeatCount5m. ContextBucketer is defined in twin/context package (locate actual file, not guessed filename). TwinConfig defaults N500,W30,kappa5,gamma.995,n03,sigmaMin.25,cGlance.4,version1.0.0. ModelConfig has latency ln1/.9, hazard1.5/1 perhour, urgency .33/.66, shrinkage5h, stale120000ms, truncationfalse,minNEff1.

ImportanceModel is present ONLY as untracked Stage 4B work in the active checkout. score(senderHash?,appCat,notifCat?,isVip,hasReplyAction=false,repeatCount5m=0,rawTextHint=null,urgencyScore=0):Double; synchronous in-memory noisy-OR, mutable feedback state, optional raw text reading. Stage 5 facts deliberately carry no text. The future adapter can omit optional rawTextHint but must recheck finalized 4B API and classifier completeness. DECISION NEEDED: no pipeline integration until committed 4B. No STAGE4B_NOTES file currently exists.

notifCat is an OPEN nonempty string, not a closed enum. No canonical registry for call/alarm/navigation/message/social/promo/email/transport/delivery exists; do not claim an enum. appCat exactly coding,docs,communication,social,video,short_video,games,shopping,news,music,learning,utility,system,unknown. Critical class mappings need explicit policy documentation before 5B.

Core defaults: kappa_d3.0min/hr,lambda_miss5min,c_digest.8min,c_rel.25,I_max.85,I_silent.16,epsilon.05 when I<.4,conf_min.4,c_glance.4min,digest120min,W30min. Recheck30s and expiry10min/24h are policy assumptions, not Appendix A numeric rows.

Existing twin purity recursively rejects Android imports under twin. Twin contract checker uses generated context-snapshots.json and twin-config.json under app/build/contract with Ajv plus core defaults; requires exported fixtures from tests. New checks follow exported function/direct CLI pattern and add planted-failure self-tests.

Directories decision/counterfactual/safety already contain .gitkeep in the committed base; ImportanceModel in the other checkout is not owned by Phase 0. Stage 4A TruthRecord has persona/dayIndex/notificationIndex/ts/context/inSession/trialEligible/exclusionReason/trial,realizedArm,propensity,eligibleForExploration,world. TrialDraws holds existence flags,owner,lShowMin,lSuppressMin,deltaLMin. RandomizedHold(eps,maxImportance) draws from tag500, holds with eps if non-session,non-important,urgency proxy below max; SHOW propensity1-eps, SUPPRESS eps. These are test-only inputs to later estimators.

## Assumptions and approvals

All S5 assumptions are proposed by the supplied guide; owner authorized starting, not separately confirming each user-visible choice. S5-1 ALLOW/DELAY/DIGEST/SILENT tieorder1e-9; S5-2 release15/30/45min capped digest; S5-3 local-midnight aligned digest120min; S5-4 OFF skips simulation; S5-5 exploration hold->ALLOW only; S5-6 SHOW=ALLOW; S5-7 maxage30s; S5-8 OTP expiry10min,other24h; S5-9 explanation-ranking heuristic; S5-10 low evidence<.4 and I>.5 allows; S5-11 strict halves kappaD/disables exploration; S5-12 decision-time predictions,Hajek primary,caps none/20/10/5,B1000,threshold20; S5-13 eligible positive-overlap per-notification30min loss estimand; S5-14 separate regret/modelSaved/delayCost/DRsaved; S5-15 importance from4B only; S5-16 unknown notifCat not itself never-hold; S5-17 median/p95<10ms dev JVM only. Approval: pending review for all final policy choices.

D56-1 in-memory flags, D56-2 Keystore encryption, D56-3 OFF/DRY_RUN/live-own-test progression, D56-4 hold estimates, D56-5 target app order and D56-6 case-by-case contract changes remain owner decisions for Stage 6; none performed here. Contract exposes flags without authorizing text-reader edits.

## Proposed milestones

5.0 decision types/facts/defaults/settings/decision/record/provider/default-provider/digest schedule, safety KillSwitchControl/ExpiryPolicy, two checks plus lock and tests. 5A cost/selector/hold estimator. 5B neverhold/failopen/diagnostics/recheck/expiry. 5C importance/predictor adapters,exploration,explanation,pipeline. 5D exploration records,IPW/DR/bootstrap/diagnostics/regret/savings. 5E verdict,urgent,latency,full gate. See supplied guide for exact files. 5A–5E not_started; Phase0 in_progress.

## Cross-stage requests

Antigravity: commit/finalize Stage4B ImportanceModel and learning APIs before 5C. Keep its existing ImportanceModel read-only to Stage5 even though its package lies under decision. Owner: review Phase0 contract and merge it plus the common/guide docs before creating either implementation worktree. Shared governance is unchanged. Nothing verified on target.

## Contract change requests

Initial sketch deltas required by guide: identity keyHash/postedTsMs in Decision; pShow computed; timezone argument on DigestSchedule; KillSource/KillSwitchControl/listener/ExpiryPolicy/default provider. Phase0 defaults for explorationEnabled=false,strictSession=false,empty allowlist,seed1 are conservative explicit assumptions; default modeOFF. Confidence in snapshotSummary is context classification confidence, not a probability that the twin prediction is correct.
