# Immediate conformance fixes

Branch: codex/readiness-conformance. Base: d06addb. Separate worktree; Antigravity's branch is unchanged. The delayed automation was paused when the owner requested immediate work. No main edits, push, deployment or device operations.

## Changes

- Reject missing/malformed envelope identity before any settlement or ACK. Require sender role and exact schema1.1.0. Reject collision with the phone installation identity.
- Remove the legacy kind/schema/privacy bypass and export fallback that generated new IDs. Export only canonical events matching their durable row id and sequence. Keep historical rows local pending reviewed mapping.
- Treat sequence0 as valid on an empty ledger, distinct from a real existing settlement.
- Restore section25.4 superseded-unresolved behavior, overriding the older7.1 instruction. Ordinary negative Beta counts are unchanged by supersession. The Stage4B recovery test, seed and0.08 threshold are untouched.
- Apply the existing RelayEndpoint policy to pairing configuration and session setup, using generated application BuildConfig.DEBUG instead of Android OS Build.TYPE. Add release/debug and endpoint-injection tests.

## Executed checks so far

- npm test:117 tests,117 pass,0 fail; duration_ms6380.7821.
- npm run checks: schema-sync1.1.0 PASS; twin-purity97 source files PASS; no-persona-imports386 production files PASS. Generated file counts can change after Gradle runs.
- npm run check:contracts:59 tests,59 pass,0 fail; duration_ms1327.6481.
- npm run check:decision: decision-purity PASS34 Kotlin files; decision-contract PASS version1,12 frozen files,11 core defaults.
- python phone/scripts/check-migration.py: PASS9 DDL statements, preserved existing row,10 table column layouts match schema3. Android Room upgrade remains not verified on target.
- check:twin-contract initially lacked its generated build/contract artifacts in this fresh worktree; rerun after the Gradle unit suite creates them.
- Full APK assembly and unit suite are running; results must be appended after completion. No success is claimed yet.

The root tests rewrote the tracked synthetic dataset sample; that known generated change was restored. No frozen core-spec, sync contract, vectors or root package changes were made.

## Readiness boundary

An all-green result obtained by counting superseded-unresolved episodes as ordinary ignores is not acceptance under25.4. See docs/CENSORING_MODEL_PLAN.md for the proposed model investigation. Keep any recovery failure visible; do not tune to a persona or weaken thresholds. Actual final results below supersede this progress section.

All device/cloud/real Gate3 checks remain not verified on target;8L acceptance remains subject to independent review.

## Final executed results

Command from phone/: `.\gradlew.bat --offline --no-daemon --max-workers=1 :app:assembleDebug :app:testDebugUnitTest` with ANDROID_HOME pointing to the installed SDK.

Exact terminal summary:

```text
> Task :app:assembleDebug
Stage4BParameterRecoveryTest > rahulFourteenDayReplayRecoversPickupProbWithinEightPercent FAILED
334 tests completed, 1 failed
BUILD FAILED in 13m 42s
48 actionable tasks: 48 executed
```

JUnit XML:78 suites,334 tests,1 failure,0 errors,0 skipped. Thus333 tests passed, including all added conformance tests. APK assembly completed before the failed test task; the combined Gradle invocation failed overall. Debug APK: phone/app/build/outputs/apk/debug/app-debug.apk,13703371 bytes. No installation or hardware test was performed.

The sole failing check reports estimate0.7247279352031226, ground truth0.62, absolute error0.10472793520312262 against unchanged0.08 limit. This is a model-validation blocker, not a successful full suite. Recovery test source is unchanged. See docs/CENSORING_MODEL_PLAN.md; do not restore unresolved-as-ignore counting to hide the failure.

After Gradle generated its artifacts, `npm run check:twin-contract` passed:7 snapshots validated and TwinConfig defaults matched Appendix A. The earlier missing-artifact result was a build-order prerequisite, not a source correction.

Sync/relay policy fixes committed as ea8f77e. No frozen contracts/core-spec/root package edits; no main changes, merge, push or deployment. Overall readiness is not accepted while model validation and owner target checks remain unresolved.
