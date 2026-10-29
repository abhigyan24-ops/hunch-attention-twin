# Stage 7 laptop working notes

Branch s7-laptop, isolated worktree ../dhyan-s7, base 23ef503. Read AGENTS.md, HANDOFF_STATUS.md, Stage 7A prompt, spec 12.1–12.4/25.9, coordination board, Stage 5 notes and sync contract. C0 is merged per owner board; Stage 6/Gate 2 remain pending. No phone, relay, contract or shared-governance changes. No push.

## Milestones
M1 in progress: laptop persistence, privacy boundary and recovery tests. Subsequent 7A collection/inference/integration and 7B/7C remain planned.

## Decisions and assumptions
CA-9 supplies 10 MiB rotation and 14-day retention. Sequence numbers reserve blocks of 100 on disk before allocation; crash holes are intentional. Failure health is visible through a store health API; the integration pipeline must turn the one-shot failure callback into a schema-valid gap event without recursively writing an unbounded chain. Pending memory is bounded to 2000 events; overflow increments dropped count. Failed appends may have written partially: truncate to the last known committed byte offset before retry, without losing the pending event. Read/repair failures keep persistence paused.

Contract concerns previously recorded on contracts branch were replaced by Antigravity's completion report; the underlying ACK and videoState questions are not assumed resolved by a passing mock test. Neither is required for local persistence. Real ActivityWatch API, Electron desktop, phone, TLS and cloud are not verified on target.

## M1 verification (executed this session)
- Added JsonlStore, privacy guard, settings, AW and fallback adapters, debounce/label-once, focus rule runner and local Electron core wiring. No relay, phone, contract or shared governance edits; root delta only check:s7.
- Added allowed pure-JS dependencies ajv ^8.20.0 and ajv-formats ^3.0.1; npm install --ignore-scripts: 87 packages audited, 0 vulnerabilities. Electron download/install scripts intentionally not executed; desktop launch not verified on target.
- `npm run typecheck --prefix laptop`: exit 0.
- `npm run build --prefix laptop`: exit 0, main/preload/renderer artifacts built.
- `npm run check:s7 --prefix laptop`: exit 0; typecheck/build; laptop tests 21/pass21/fail0/duration_ms1251.377; targeted privacy canary tests2/pass2/fail0/duration_ms246.3811. Exact output in laptop/VERIFICATION_7A_M1.txt.
- `npm run checks`: schema-sync PASS1.0.0, twin-purity PASS96, no-persona-imports PASS245.
- Native .node scan of laptop/node_modules: Count0.
- Initial new tests exposed a missing context-snapshot schema registration (fixed), then an incorrect rotation test byte threshold (fixed). No unresolved failing tests in the final executed commands. Root full npm test was not run in this milestone.
- Live AW request failed: not verified on target. No Electron GUI, PowerShell foreground collector, TLS/cloud, browser extension or phone hardware validation.

## Integration hooks and remaining work
`startLaptopCore(directory,schemaVersion,idleSeconds,now)` returns settings/store/focus/labels/diagnostics/setStuckEvidence/stop. FocusRunner.onState(callback) exposes typed laptop snapshots; JsonlStore.readRecentEvents(limit) returns isolated copies; labels.labelOnce(process,category) persists category labels; health IPC exposes counters only, process-label IPC is restricted to the trusted window frame.

M1 is locally tested, not the final Stage7 exit gate. Remaining 7A work: bounded tail recovery optimization and stronger lifecycle/integration/fallback switching tests; live AW shape validation; open inference decisions in laptop/DECISIONS_7A.md. 7B replaces the explicitly labelled scaffold UI sample state; 7C wires ingest and extension evidence. No Stage7 merge/push, no Stage8 start, no Gate2 claim.

# W2 Step 0 — fresh audit, 2026-10-29

Baseline is commit9c53137 on s7-laptop, clean working tree at start. Read AGENTS, HANDOFF, SPEC12.1–12.4/25.1/25.8/25.9/25.11/25.12, original7A attachment and supplied W2 queue. W1 report committed separately on contracts; no contract change is authorized by this step.

| Original7A item | Baseline status | File/test evidence and gaps |
|---|---|---|
| 1 Main/settings/diagnostics | partial | main/index.ts single-instance/userData wiring, settings.ts and core.test label/settings test. Counters split across store and ad-hoc closure; adapter errors absent; quit does not await stop. |
| 2 AW/fallback adapters | partial | AwAdapter.ts, ActiveWinFallback.ts; core.test fake HTTP and injected fallback. No pipeline switching test; failed optional bucket can abort later buckets; no live API evidence or60s smoke. |
| 3 Normalizer/envelope/privacy | partial | windowNormalizer.ts, privacyGuard.ts; debounce/label/forbidden tests. Canonical imports present. Whole-pipeline canary test absent; closure Promise chain unbounded. Steam/Epic path-based game detection not possible with process-only input; category maps/label-once supported, never persist paths. |
| 4 JSONL/recovery | partial | JsonlStore/store.test rotation/retention/truncation/ENOSPC/seq reservation. Recovery reads whole retained files. No pipeline restart/disk-full test; memory bound does not bound upstream Promise closures. |
| 5 Inference/emitter | partial | focusState.ts/core.test every rule pure fixture +heartbeat. No initial-offline gap, consumer callback failures can escape, actual60s evidence window is closure-specific. |
| 6 Tests | partial |21 baseline tests pass, but end-to-end privacy/failure isolation/backpressure/lifecycle tests absent. |
| 7 Scripts/verification | partial | check:s7 builds/types/tests +2privacy tests. Missing live smoke/manual7A checks. Real GUI/AW/Windows behavior unverified. |

Executed baseline: npm run typecheck --prefix laptop exit0; npm run build --prefix laptop exit0 (main41.58kB, preload0.57kB, renderer668.68kB); npm run check:s7 --prefix laptop exit0: tests21/pass21/fail0/duration_ms3093.8046, targetedprivacy tests2/pass2/fail0/duration_ms414.9764. npm run checks exit0: schema-syncPASS1.0.0, twin-purityPASS96, no-persona-importsPASS246. These are fresh baseline results, not the old M1 report.

# W2 Step 2 — M2 completion for owner review

W2's scoped laptop implementation and local verification are complete. This does not assert a real Windows/AW/Electron or Stage7/8 integration gate. STOP before7B as requested in W2; W3 waits for owner marks in the W1 report. No new dependency, root manifest, phone/F1, relay, core, contract, pitch or governance edit in W2. Nothing pushed or merged.

| Original7A item | Final status | New/final evidence |
|---|---|---|
| 1 Main/settings/diagnostics | done locally | Existing settings/identity regression retained; main/index.ts now awaits stop and warns if storage remains paused; LaptopPipeline.diagnostics consolidates written/rejected/dropped/writeFailures/adapterErrors/truncatedLinesRecovered and bounded queue/subscriber counters. Actual single-instance/window behavior not verified on target. |
| 2 AW/fallback | done with target pending | AwAdapter tolerates optional bucket failures, discovers window/afk/web/vscode prefixes, exposes IDs only. Pipeline tests verify15s failover, primary recovery and both-silent behavior. ActiveWinFallback retains process-only PowerShell and injected idle. Live AW unavailable;60s smoke code present, not a successful live run. |
| 3 Normalizer/privacy | done locally | Existing canonical category imports/debounce/labels/guard retained. LaptopPipeline validates raw key allowlist and canonical event before store; pipeline canary test covers AW through rotated JSONL, diagnostics and actual intercepted console calls. |
| 4 JSONL/recovery | done locally | Bounded tail repair +streaming retained-file scan, ring across rotations, persisted100-sequence reservations, immediate final retry. Pipeline tests simulate partial crash/restart, blocked writes/queue overflow, ENOSPC recovery and single gap. store.test covers fake-clock rotation/retention and30KB partial tail. |
| 5 Rule inference/emitter | done locally; policy ambiguities documented | Every table row has a fixture. Pipeline test exercises real60s switch expiry and5s timer, offline never flowing, and one gap per collector outage including initial silence. Stuck evidence hook/uncalibrated confidence remain explicit decisions, no fabricated thresholds or probabilities. Outbound relay state wiring belongs8L. |
| 6 Test gaps | done locally | pipeline.test.mjs adds9 integration/failure/privacy tests; store.test adds retained-history/large-tail regression. Existing21 retained;31 total now pass. |
| 7 Scripts/manual checks | done; owner live checks pending | check:s7 includes whole-pipeline canary; scripts/live-aw-smoke.mjs uses real AwAdapter for60s only when reachable; MANUAL_CHECKS.md has7A PowerShell steps and expected safe outputs. |

## Exact final command summaries (this session)

`npm run typecheck --prefix laptop`: exit0, tsc --noEmit.
`npm run build --prefix laptop`: exit0, main/preload/renderer built. Final check:s7 repeats both successfully.
`npm run check:s7 --prefix laptop`: exit0; final full laptop suite:
```
ℹ tests 31
ℹ suites 0
ℹ pass 31
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 1879.6032
```
Targeted privacy rerun:
```
ℹ tests 3
ℹ suites 0
ℹ pass 3
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 687.8666
```
Complete stdout: laptop/VERIFICATION_W2.txt. `npm run checks`: exit0; schema-sync PASS(1.0.0;1Kotlinversionfile), twin-purity PASS(96twinfiles), no-persona-imports PASS(253productionfiles). `Get-ChildItem laptop/node_modules -Recurse -File -Filter *.node | Measure-Object`: Count0. git diff --check: no whitespace errors after cleanup.

`node laptop/scripts/live-aw-smoke.mjs`: exit1, exact message `not verified on target: ActivityWatch not running`. Preflight only;60s collection did not run. Owner command from laptop/: `node scripts/live-aw-smoke.mjs`; metadata check `npm run check:aw`. See MANUAL_CHECKS.md before Electron install/dev. No root full suite, Android build, F1 test or hardware verification was run in W2.

An initial whole-pipeline canary test failed because the test's fake AW clock was2026 while its pipeline clock was1000ms; the intended future-time validation correctly rejected that input. Aligned the test clocks; final test passes. One intermediate typecheck found a duplicate diagnostics property; removed it. No unresolved failed local check remains.

## Exported 7B/7C hooks

From `laptop/src/main/LaptopPipeline.ts`:
- `new LaptopPipeline(options: PipelineOptions)` with injected primary/fallback LaptopAdapter, JsonlStore, labels.category, deviceId, schemaVersion, optional now/queueLimit/automaticTicks/normalizer.
- `start(): void`, `stop(): Promise<void>`, `flush(force?: boolean): Promise<void>`, `tick(): FocusSnapshot | undefined` (manual clock tests).
- `onEvent(callback: (event: CategorizedEvent) => void): () => void` and `onState(callback: (state: FocusSnapshot) => void): () => void`; unsubscribe closures, isolated callback failures, independent copies. onEvent is validated memory acceptance, never durable sync ACK.
- `injectFeedEvent(event: FeedEvent): boolean`; FeedEvent is categorized RawLaptopEvent union from adapters/LaptopAdapter.ts (process/idle/domainCategory/edit counts), no wire keys, title, URL, id or seq. 7C owns authentication and its exact wire-to-feed conversion; event envelopes remain laptop-owned.
- `setStuckEvidence(value: boolean): void`, expires after15seconds; input-to-stuck thresholds still need reviewed extension semantics.
- `diagnostics` getter returns a category/count/health snapshot; `adapterError(): void` and `storageFailed(): void` are controlled internal adapters for failure counters/gap signals.

`startLaptopCore(directory, schemaVersion, idleSeconds, now?)` now returns settings/store/labels/pipeline/focus, diagnostics getter, setStuckEvidence and async stop; existing trusted IPC remains compatible. Ingest server is not started by W2. Queue is2000 by default; oldest waiting change is dropped and counted, in-flight change remains. Store pending/ring memory remains independently2000 bounded. Shutdown stops intake/adapters/timers, drains accepted work and attempts one immediate retry; a full disk remains paused/visible, without claiming durable success. Monotonic100-seq reservations permit crash holes (1 then101 after restart). Unknown-state/continuity/stuck/confidence limits stay in laptop/DECISIONS_7A.md.

W1 audit artifact: ../dhyan-contracts/docs/contracts/RECONCILIATION_REPORT.md, commit a59c208; owner approval/rejection still pending. F3 standalone source file and F1 NOTES_F1.md were absent when checked. No F1 M0 approval or W5 verdict is implied; W5 waits for its reportedM1–M4 completion. W6 waits for F1 merge.
