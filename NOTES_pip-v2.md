# Pip v2 — M0 review packet

Status: M0 only. Production Pip is unchanged. M1–M4 have not started. The adopted prompt is docs/prompts/DHYAN_PIP_V2_DESIGN_AND_PROMPT.md:121–126, including “STOP after M0.”

## Ground truth and claims ledger

| Claim | Evidence | Status |
|---|---|---|
| Branch pip-v2 starts from local main 9a45a96; local rebase was already current | git rebase main output; s7-laptop is an ancestor of main (merge-base exit 0) | VERIFIED locally |
| Remote freshness is unknown | Noninteractive git fetch origin failed authentication; cached origin/main was 7cde26a, local main 32 ahead | UNKNOWN; no push/merge |
| Current pet is 200×230, transparent, frameless, non-focusable, click-through | laptop/src/main/index.ts:30; ignoreMouse forwarding and showInactive | VERIFIED source; actual desktop behavior not verified |
| No explicit initial x/y is supplied | laptop/src/main/index.ts:30 | VERIFIED source; claimed centered placement NOT reproduced |
| Current UI captures hover hitbox and otherwise ignores mouse | laptop/src/panel/index.tsx:249–261; style.css:70–77 | VERIFIED source; physical pass-through not verified |
| Existing pet has seven behavior mappings and a held-count badge | laptop/src/pet/PipCompanion.tsx:5–55; petStateMachine.ts:1–35 | VERIFIED source |
| Existing bubbles have a 600000ms per-type cooldown, no graded ladder | laptop/src/pet/bubbles.ts:2–10 | VERIFIED source |
| Bus lacks categories, surfaces and declared break | laptop/src/pet/petInputBus.ts:2–20 | VERIFIED source |
| Sanitized category/surface evidence exists elsewhere | LaptopPipeline.ts:35–36,75–84; ingest/FeedRouter.ts:4; ingest/types.ts | VERIFIED source; proposed adapter not implemented |
| Focus confidence is uncalibrated | laptop/src/state/focusState.ts:18; owner DD-13 | VERIFIED source; never propose a percentage |
| Real local declaration exists, but unpaired panel button only alerts | main/declaredSession.ts:6–14; main/index.ts:31,38; panel/index.tsx:425–430 | VERIFIED source; no fix at M0 |
| No canonical break task exists | core-spec/context-snapshot.schema.json:21–31 | VERIFIED source; decision pending |
| No movement/position persistence/panic controls exist in inspected pet paths | Focused search of main/index.ts, main/settings.ts and pet for setBounds/setPosition/displayId/relX/relY/globalShortcut/watchdog/snooze/calm/dnd returned no matches | VERIFIED bounded source search |

Installed Electron is **45.0.0**. Exact installed declaration signatures, line numbers and Windows caveats are in laptop/test/pip-v2-spike/ELECTRON_API_AUDIT.md. Declaration availability is not runtime acceptance. Forwarding may fail with elevated foreground windows due to UIPI; no elevation workaround is proposed.

## Measurements

One sequential A/B trial, one detected display, scale factor 1.25. Both isolated processes use a static 96-DIP dummy sprite, not CloudSpirit or production collectors. CPU is Electron app.getAppMetrics interval sampling; primed before collection, 10 samples per phase. Drag means a scripted 200-DIP sinusoidal trajectory for approximately five seconds, not a physical pointer drag. rAF measures callback intervals, not displayed-frame latency. The callbacks were not throttled to the proposed product 30/60 FPS.

| Option | Idle renderer mean CPU | Idle all-app mean CPU | Drag renderer mean CPU | Drag all-app mean CPU | Drag rAF mean / p95 / max ms |
|---|---:|---:|---:|---:|---:|
| A: moved small window | 0.5213% | 0.9069% | 1.2184% | 7.2418% | 6.9724 / 7.1 / 20.9 |
| B: display overlay | 0.4847% | 0.8440% | 0.9570% | 2.6324% | 6.9529 / 7.1 / 14.1 |

Raw numeric records: laptop/test/pip-v2-spike/results/option-A.json and option-B.json (idle, drag and ownerProbe fields). Other desktop workload was uncontrolled. The dummy has less work than the actual character. Both dummy idle means are below 2%; **product idle CPU budget is not verified on target**. Prefer B provisionally because this trial shows lower scripted movement overhead. Do not accept B until owner input/display checks pass.

Hover counters were A=0/B=2, underlay clicks A=0/B=0, interactive=false. These uncontrolled counters prove neither hover forwarding nor click-through. Both reports deliberately retain unknown_owner_check_required. B creates overlays for detected displays but only shows the primary sprite; cross-display handoff is not implemented in this prototype.

## Findings (G13)

| ID | Status | Evidence | Fix / test |
|---|---|---|---|
| F1: fixed-position limitation | NOT REPRODUCED interactively; source absence verified | No movement/persistence APIs in inspected production paths | Plan M2; owner drag test pending |
| F2: missing policy evidence fields | REPRODUCED by source inspection | Bus interface lacks category/surface | Request thin adapter; M1 fake-clock tests after approval |
| F3: missing break action | REPRODUCED by source inspection | Canonical task enum excludes break | Local-only gate proposal; no invented protocol |
| F4: automatic acceptance could overclaim Windows safety | REPRODUCED by intentional reporting-guard mutation | New test fails if pointerStatus becomes verified | Mutation restored; 4/4 harness tests pass |
| F5: B lower scripted movement overhead in this trial | REPRODUCED once | Committed numeric results | Not a product performance guarantee; repeat on target |

## Proposed implementation and disjoint scopes

Review-only interfaces: laptop/test/pip-v2-spike/contract.proposed.ts. Review-only config: pip-config.proposed.json. Neither is imported by production.

| Stream | Owned future scope | Boundary |
|---|---|---|
| Lead | Freeze shared pet types/config after owner decisions; review integration | No pipeline/bus semantic changes |
| S1 | pet pure DistractionAssessor, EscalationLadder, BubblePolicy, localized copy | Explicit clock and seeded picker; no Electron/I/O |
| S2 | main/pet window controller, evidence adapter, persistence/IPC; pure DragPhysics and PositionStore codec | Thin Electron adapters; separate pip-settings.json; no core settings rewrite |
| S3 | Pet renderer, animation/bubbles, pet-only menu/panel settings | Uses frozen types/actions; no inference logic |
| S4 | Separate test files and MANUAL_CHECKS; check:pip only at M4 | No shared source edits with S1–S3 |

No delegation occurred. At most three active streams under DD-15: M1 S1/S4; M2 S2/S3/S4; M3 same, S1 idle. Missing section L must be resolved before delegated implementation. M1 tests cover thresholds, exclusions, continuity, adaptation, budgets, seeded copy and recovery. M2 tests cover physics/position corruption/display changes with fake screen adapters, plus owner input review and mandatory stop. M3 integrates approved actions and level rendering. M4 adds privacy canaries and product performance/manual evidence.

Respect gates precede evidence/ladder evaluation. Pure policy receives only enums/counts/times, never whole collector events. Persist numeric day/level shown/accepted/dismissed counters, not bubble copy or app/site/file identifiers. Position is normalized with display fallback and clamp. Sender/frame/payload validation belongs in thin IPC adapters. No continuous rendering when hidden; heartbeat watchdog forces ignore-all on renderer failure. These are plans, not implemented claims.

## Assumptions table

Every key/value in the single proposed JSON object is ASSUMED unless explicitly identified as an existing freshness constant. No value was tuned to make tests pass.

| Config group | Proposed values |
|---|---|
| evidence categories/surfaces | work coding/docs/learning; distraction social/short_video/games/shopping/news; surfaces feed/shorts |
| evidence timing | recentWorkMs=900000; bridgeGapMs=20000; localFreshMs=15000 and remoteFreshMs=30000 mirror existing state-machine limits; watchMultiplier=2; recoveryMs=60000; leisureCheckInMs=3600000; leisureCheckInEnabled=false pending definition |
| ladder | maximumLevel=4; thresholdsMs=[0,60000,180000,480000,1200000]; cooldownsMs=[0,0,600000,1200000,0]; overlapBubbleMs=120000; bubbleMs=6000; cardMs=12000 |
| budgets/adaptation | hourCap=4; dayCap=20; budgetWindowMs=3600000; categorySuppressionMs=3600000; dismissMultiplier=1.5; consecutiveDismissals=3; dismissPauseMs=3600000; level4OncePerEpisode=true |
| copy/quiet | quietStartMinute=1320; quietEndMinute=420; reducedText=false; copyPoolMinimum=8 |
| movement size/opacity | sizesDip small=80/medium=96/large=128; size=medium; idleOpacity=.6; activeOpacity=1; opacityIdleMs=20000 |
| movement physics | velocityWindowMs=80; frictionPerSecond=6; settleSpeedDipPerSecond=20; maximumSpeedDipPerSecond=2000; snapRadiusDip=32; springOmegaPerSecond=12; springArrivalDip=.5; maximumStepSeconds=.033; clickSlopDip=4; safeMarginDip=8; outerBandFraction=.25 |
| movement controls | idleFps=30; dragFps=60; watchdogMs=2000; heartbeatMs=500; calm=false; dnd=false; dndCategories=[games]; panicShortcut=Control+Alt+P; snoozeMinutes=[15,60]; breakMinutes=15; focusOfferMinutes=25 |
| spike timing/sampling | warmupMs=2000; phaseMs=5000; sampleMs=500; ownerProbeMs=60000; safetyExitMs=90000; loadTimeoutMs=15000; frameSampleCap=2048 |
| spike geometry | spriteDip=96; smallWindowWidthDip=200; smallWindowHeightDip=230; paddingDip=24; travelDip=200; pathCycles=1; underlayWidthDip=320; underlayHeightDip=190 |
| spike other | breathSeconds=4 (proposal only, dummy is static); idleCpuTargetPercent=2 (acceptance target, not product result) |

## Files changed

This report; DECISIONS_pip-v2.md; verbatim adopted docs/prompts/DHYAN_PIP_V2_DESIGN_AND_PROMPT.md; appended laptop/MANUAL_CHECKS.md. Under laptop/test/pip-v2-spike/: README.md, ELECTRON_API_AUDIT.md, main.cjs, preload.cjs, renderer.html, renderer.cjs, underlay.html, underlay.cjs, run-spike.ps1, analyze.cjs, report.cjs, spike.test.mjs, contract.proposed.ts, pip-config.proposed.json, results/option-A.json, results/option-B.json. No production code, dependency manifests, phone, relay or contract files changed.

## Commands run and real summaries

Commands below were executed from repository root in this session unless noted. The output directory for the numeric run was the existing external CX6-phone-review evidence directory's pip-v2-m0 subdirectory; results were copied into the committed results folder. Exact repeatable local commands are in the harness README.

| Command | Exit / actual summary |
|---|---|
| git rebase main | 0: Current branch pip-v2 is up to date. |
| git merge-base --is-ancestor s7-laptop main | 0 |
| git -c credential.interactive=never -c http.lowSpeedLimit=1 -c http.lowSpeedTime=15 fetch origin (GIT_TERMINAL_PROMPT=0) | Failed authentication; remote freshness UNKNOWN |
| powershell -NoProfile -File laptop/test/pip-v2-spike/run-spike.ps1 -Option Both -OutputDirectory 'C:\Users\abhig\.codex\visualizations\2026\10\25\01a17242-41b4-7ad1-9939-146f993dce6d\CX6-phone-review\pip-v2-m0' | 0; A and B numeric records produced (see raw results). |
| node --test laptop/test/pip-v2-spike/spike.test.mjs | 0: tests 4, pass 4, fail 0 |
| Same test command with temporary acceptance-guard mutation | 1: pass 3, fail 1; mutation restored, rerun pass 4 fail 0 |
| node --check laptop/test/pip-v2-spike/main.cjs (also renderer.cjs and preload.cjs) | Each exit 0 |
| npm run typecheck --prefix laptop | 0: tsc --noEmit |
| .\\laptop\\node_modules\\.bin\\tsc.cmd --noEmit --strict --skipLibCheck --target ES2022 --module ESNext --moduleResolution Bundler laptop/test/pip-v2-spike/contract.proposed.ts | 0 |
| node laptop/test/pip-v2-spike/report.cjs laptop/test/pip-v2-spike/results/option-A.json (also option-B.json) | Each 0: complete=true, pointerStatus=unknown_owner_check_required, productBudgetStatus=not_verified_on_target |
| npm run build --prefix laptop | 0: main 45 modules built in 632ms; preload 1 module in 15ms; renderer 30 modules in 3.44s |
| npm test --prefix laptop | 0: tests 86; suites 0; pass 86; fail 0; cancelled 0; skipped 0; todo 0; duration_ms 4361.8769 |
| npm run checks | 0: schema-sync PASS 1.1.0; twin-purity PASS 453; no-persona-imports PASS 455 |
| git diff --check | 0; no whitespace errors |

Final self-review inspected the complete prototype main/renderer/preload/reporting code and the appended manual checklist. A focused TODO/FIXME/skipped-test search returned no matches (rg exit 1 means absence). The final harness rerun again reported tests 4, pass 4, fail 0, skipped 0; duration_ms 204.9558. Both committed result reports were rechecked and retain unknown owner acceptance. The prototype's interactive drag intentionally does not implement the full future movement feature, display handoff, tray or global shortcut; those remain M2 work.

## Not verified on target

Physical hover/click-through, actual pointer drag, production CPU, cross-display handoff, mixed DPI, taskbars, fullscreen video/games, sleep/wake, RDP, shortcut collisions and actual user outcomes. No phone, relay/cloud or hardware acceptance is inferred. Current production animation workload was not measured.

## Questions for owner / next step

Review DECISIONS_pip-v2.md: P-1–P-7, pet-only preload/build-config scope, local break gate, declared-focus precedence, watch qualification and gap behavior. Use appended MANUAL_CHECKS for optional M0 A/B input checks. **STOP after M0** pending owner review; do not start M1 or merge/push this branch.

## M1 progress — owner approval and ActivityWatch verification

Owner approved P-1–P-7 defaults in chat, including declared-focus suppression, watch only in approved distraction categories, and short gaps preserving continuity without contributing dwell. This supersedes those pending entries in DECISIONS_pip-v2.md; preload scope and local break integration remain pending before M2/M3.

Files added: laptop/src/pet/pipPolicy.ts and laptop/test/pip-policy.test.mjs. Pure DistractionAssessor, EscalationLadder and seeded BubblePolicy are implemented independently of runtime wiring. pipConfig is the single implemented policy parameter object; M0 JSON remains historical proposal material. Copy currently uses a shared eight-line pool available to each bubble level; level-specific copy refinement remains before visual integration. Numeric counters remain in memory; persistence is future adapter work.

Assumption requiring review: recent work qualifies entry into a continuous episode and remains attached until reset/recovery. Requiring work in the last 15 minutes on every tick would make the requested 20-minute L4 unreachable. This interpretation is explicit in source and tested; do not treat it as an owner-signed rule yet. Optional leisure check-in remains disabled as documented at M0.

Commands actually run:
- npm run check:aw --prefix laptop: exit 0; required window and AFK bucket IDs found.
- node laptop/scripts/live-aw-smoke.mjs: exit 0; categoryCounts={unknown:12}, stateChanges=0. This proves fresh adapter observations during the minute, not a known focus category or currently rendered app state.
- npm run typecheck --prefix laptop: exit 0, tsc --noEmit.
- node --test laptop/test/pip-policy.test.mjs: tests 8, pass 8, fail 0.
- npm test --prefix laptop: tests 94, pass 94, fail 0, cancelled 0, skipped 0, todo 0; duration_ms 6249.2842.
- npm run build --prefix laptop: exit 0; main 45 modules, preload 1, renderer 30; built in 810ms / 40ms / 2.32s.
- G8 mutation: changed L1 threshold by 1ms; tests 8, pass 6, fail 2 (exit 1). Restored the original threshold immediately. Final rerun recorded below.

Findings: ActivityWatch integration VERIFIED in main/laptopCore.ts and adapters/AwAdapter.ts; available live window/AFK feeds REPRODUCED. Unknown categorization REPRODUCED in the live smoke; no fabricated focus claim. Threshold regression REPRODUCED by mutation and restored. Runtime visual integration, product performance, real pointer interactions and multi-monitor behavior remain not verified on target.

Next: review the episode-entry interpretation, complete M1 review, then M2 movement with its mandatory owner stop. No visible movement or reaction behavior was changed by this M1 foundation.
Final restored rerun: tests 8, pass 8, fail 0, skipped 0; duration_ms 188.5393. git diff --check exit 0.

## Pip v2 smooth intervention update

Owner requested a faster four-stage loop and smoother movement on 2026-10-30. This explicit direction supersedes M0's assumed 60 s / 3 min / 8 min intervention defaults. Qualifying distraction still requires recent work, healthy fresh input, and the existing quiet-hours, break, snooze, DND, gaming and declared-focus suppressors. L1 is immediate gaze, L2 starts at 5 s with an edge peek and micro-actions, L3 starts at 12 s with a spring walk-in, and the existing long L4 remains for planned feature continuity. Animation uses a 16 ms window spring loop and renderer knock/landing animations; drag polling is 16 ms and setIgnoreMouseEvents is called only on transitions. Accept/focus return to the edge and briefly celebrate. Numeric shown/accepted/dismissed counters are logged without category or content. Dragging a nudge away within 4 s dismisses it. After at least five nudges, dismissed > accepted and dismissed/shown > 0.60 multiplies future thresholds and cooldowns by 1.5. This is an adaptive heuristic; no focus-improvement claim is made.

The dev-only DHYAN_DEV_PET_LEVEL=1..4 switch renders a visibly marked synthetic reaction without changing real counters. It is disabled in packaged builds. Desktop window layering, fullscreen behavior, mixed DPI and user-perceived smoothness still need owner observation on target. Existing single-screen pointer drag was observed earlier; the new spring loop is validated by pure spring tests and build, not by a measured final Windows frame-cadence run.

Verification in this session after the fast intervention update:
- `npm run typecheck --prefix laptop`: exit 0 (`tsc --noEmit`).
- `npm test --prefix laptop`: tests 102, pass 102, fail 0, skipped 0, duration_ms 3675.8783.
- `npm run build --prefix laptop`: exit 0; main/preload/renderer compiled, renderer built in 973ms.
- `npm run checks`: schema-sync PASS 1.1.0, twin-purity PASS 453, no-persona-imports PASS 465.
- `git diff --check`: exit 0 (line-ending warnings only).
- Windows UI: a dev-only Stage 3 simulation was launched and visually showed the pink surprised pet, the pill bubble, Refocus Now/Break/Dismiss actions, and the DEV SIMULATION marker. This confirms visible rendering, not a measured animation frame cadence or real-data trigger. Physical mixed-DPI/fullscreen/click-through remains pending owner verification.
