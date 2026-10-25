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
