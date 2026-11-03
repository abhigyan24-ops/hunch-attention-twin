# Dhyan VS Code Extension: Progress Tracker

## Status Overview
- Current Mode: **MODE A** (Existing `vscode-ext/` migrated and hardened)
- Current Step: **PHASE 6 COMPLETE — READY FOR FINAL REVIEW (PART C)**

---

## Phase Checklist

### [x] Phase 0: Scaffold, Tooling, Privacy Gate, No-Leak Test Harness
- [x] Update `vscode-ext/package.json` (contributes from Section 3 & Appendix A, scripts: `compile`, `watch`, `lint`, `test:unit`, `test:integration`, `package`)
- [x] Configure `tsconfig.json` (strict mode), `esbuild.js`, `.vscodeignore`, `.vscode-test.mjs`
- [x] Implement `src/clock.ts` (`Clock` interface, `RealClock`, `FakeClock`)
- [x] Implement `src/privacy/hash.ts` (HMAC-SHA256 with per-install secret salt, 16 hex chars)
- [x] Implement `src/privacy/exclude.ts` (glob, language, and workspace exclusions)
- [x] Implement `src/privacy/schema.ts` (allowlist schema per event type)
- [x] Implement `src/privacy/gate.ts` (`PrivacyGate.sanitize` drops unknown fields & strings > 64 chars)
- [x] Implement `src/capabilities.ts` (runtime feature detection of VS Code APIs)
- [x] Write no-leak unit test (`test/unit/no-leak.test.ts`) with property-style coverage over all schemas
- [x] Wire minimal `activate`/`deactivate` registering `dhyan.pause` and `dhyan.diagnostics`

### [x] Phase 1: Sensors, Edit Classifier, 10s Summaries, Rhythm Stats
- [x] Implement `src/aggregate/classify.ts` (pure 6-way change classifier: typing/paste/bulk/external/undo/redo)
- [x] Implement `src/aggregate/rhythm.ts` (200-gap ring buffer, p50, p90, longPauseRatio)
- [x] Implement `src/aggregate/window.ts` (`Aggregator` with 10s tick producing `ed_summary`)
- [x] Implement thin sensor adapters: `src/sensors/editor.ts`, `window.ts`, `diagnostics.ts`, `debug.ts`
- [x] 10-second timer wiring in `extension.ts` flushing through `PrivacyGate.sanitize`
- [x] Add performance test (10,000 rapid edits, average handler latency < 0.05ms)

### [x] Phase 2: Engine Link, Endpoint Discovery, Outbox, Mock Engine
- [x] Implement `src/link/protocol.ts` (TypeScript types + schema validation for engine messages)
- [x] Implement `src/link/outbox.ts` (in-memory ring buffer 5000, JSONL spill 5MB, ack replay)
- [x] Implement `src/link/endpoint.ts` (`~/.dhyan/endpoint.json` watcher + URI handler)
- [x] Implement `src/link/client.ts` (WebSocket client to `ws://127.0.0.1:<port>/ext` with Bearer auth)
- [x] Implement `test/mock-engine` (mock WebSocket server for protocol testing)
- [x] Implement status bar states with quiet offline handling

### [x] Phase 3: Task Context and Natural Break Detector
- [x] Implement `src/sensors/git.ts` (extract workspace root + branch name via `vscode.git` API)
- [x] Implement `src/sensors/tasks.ts` and `terminal.ts` (track tasks and command categories)
- [x] Implement `src/aggregate/taskContext.ts` (`taskKey` HMAC + 60s windowed `taskType`)
- [x] Implement `src/aggregate/breaks.ts` (`ed_break` detector with confidence scores)

### [x] Phase 4: Resume Assist, Snapshotting, and Censored Resumption Tracker
- [x] Implement `src/aggregate/resumeTracker.ts` (resumption tracking, 30-min censoring cutoff)
- [x] Implement randomized assist decision logic with propensity logging (`p = clamp(assistProbability, 0.05, 0.95)`)
- [x] Implement `src/features/resume.ts` (snapshots on blur >= 20s or idle >= 60s in `workspaceState`)
- [x] Implement `dhyan.resume` and `dhyan.park` commands (`ctrl+alt+p`, `ctrl+alt+r`)
- [x] Implement status bar resume card and "Where I Left Off" tree view

### [x] Phase 5: Sessions, Ratings, Views, Nudges, and State Leases
- [x] Implement `src/features/session.ts` (`dhyan.startSession`, `dhyan.endSession` with 1-5 ratings)
- [x] Implement "Today" tree view (`dhyan.today`)
- [x] Implement "Held for You" digest view (`dhyan.digest`)
- [x] Implement `src/features/nudge.ts` (rate limiting, typing suppression, status bar presentation)
- [x] Implement state lease handling with automatic rollback on expiration
- [x] Implement `dhyan.forgetToday` command

### [x] Phase 6: Hardening, Multi-Window, Packaging, CI
- [x] Multi-window support (random `windowId` in handshake, batches, and 2s focus debounce)
- [x] Remote window detection (`vscode.env.remoteName` quiet mode & `remote-unsupported` status)
- [x] Fault-injection tests (engine restart, corrupt endpoint, disk spill cap simulation)
- [x] Performance checks (< 50ms activation, < 0.05ms handler, bounded memory buffers)
- [x] Comprehensive `README.md` privacy disclosure matching `PrivacyGate` schema
- [x] GitHub Actions workflow (`.github/workflows/vscode-ext.yml`)
- [x] Extension bundling and `.vscodeignore` packaging configuration
