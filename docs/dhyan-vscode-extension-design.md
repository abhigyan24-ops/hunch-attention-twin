# DHYAN VS Code Extension Design Specification

## 1. Goal and Overview
The Dhyan VS Code extension is the in-editor telemetry sensor and quiet actuator for the Dhyan attention digital twin:
1. **Sensor:** Reports coding activity summaries (counts, categories, durations, and salted hashes only; zero code or document text) to the local desktop engine.
2. **Resume Assist:** Helps developers resume interrupted tasks after returning from a context switch, while measuring resumption lag with randomized propensity logging.
3. **Quiet Actuator:** Displays held items at natural breaks and accepts focus session ratings without interrupting flow.

---

## 2. Precedence and Hard Rules
### Privacy (P1 - P6)
- **P1:** Never send, log, store on disk or put in any event: document text, file paths, branch names, commit messages, repo URLs, diagnostic text, terminal command lines/output, variable values, or user intention/parked text.
- **P2:** Outbound data allowlist: counts, durations, booleans, categories, language IDs, task type, salted HMAC-SHA256 hashes (16 hex chars), diagnostic counts, terminal command category + exit code, 1-5 ratings, intention hash + length.
- **P3:** Single choke point: `PrivacyGate.sanitize(event)` validates against allowlist schemas and drops unknown fields.
- **P4:** Network restriction: Connects strictly to `127.0.0.1` (local Dhyan engine). Zero external telemetry.
- **P5:** Snapshots with paths live strictly in `context.workspaceState` and never enter events or outbound messages.
- **P6:** Respects workspace trust, user exclusions (globs/languages), and global pause.

### Performance (F1 - F5)
- **F1:** Hot path handlers (`onDidChangeTextDocument`, selection, scroll) only increment counters or push to bounded ring buffers. No JSON, no string building, no I/O, no awaits.
- **F2:** Activation event: `onStartupFinished`. No synchronous I/O or network during activation.
- **F3:** All timers and listeners disposed in `deactivate()`. Bounded buffers only.
- **F4:** Never read document text; use `contentChanges[].text.length` and `rangeLength` only.
- **F5:** Diagnostics polled at most once every 2 seconds.

### UX & Safety (U1 - U4, A1 - A3)
- **U1:** Quiet UI: No modals or toasts by default. Uses status bar, tree views, and output channels.
- **U2:** Silent when engine is offline. Quiet status bar state; never pop up connection errors.
- **U3:** Nudges are rate-limited, auto-dismiss, and suppressed if typing occurred in last 10s or debugger is paused.
- **U4:** Auto-restore and intrusive interventions are disabled by default.
- **A1 - A3:** Feature-detect vscode APIs, verify command existence, wrap external extensions (Git) in try/catch.

---

## 3. Tech Stack & Target Layout
- **Language & Runtime:** TypeScript (strict mode), Node.js built-ins (`node:crypto`, `node:fs`, `node:os`, `node:path`), WebSocket (`ws`).
- **Bundler:** `esbuild` emitting single `dist/extension.js` (CommonJS, external "vscode").
- **Testing:** Mocha for pure unit tests (`test/unit/`), `@vscode/test-cli` for integration (`test/integration/`).
- **Engine Version:** `engines.vscode`: `^1.93.0` (with feature detection fallback to `^1.80.0` for backwards compatibility).

### Target Directory Layout (`vscode-ext/`)
```
vscode-ext/
  src/
    extension.ts
    config.ts
    capabilities.ts
    clock.ts
    privacy/
      hash.ts
      exclude.ts
      gate.ts
      schema.ts
    sensors/
      editor.ts
      window.ts
      diagnostics.ts
      debug.ts
      tasks.ts
      terminal.ts
      git.ts
    aggregate/
      classify.ts
      window.ts
      rhythm.ts
      taskContext.ts
      breaks.ts
      resumeTracker.ts
    link/
      endpoint.ts
      client.ts
      outbox.ts
      protocol.ts
    features/
      resume.ts
      park.ts
      session.ts
      digest.ts
      nudge.ts
    ui/
      statusBar.ts
      trees.ts
    diagnostics/
      channel.ts
      perf.ts
  test/
    unit/
    integration/
    mock-engine/
```

---

## 4. Spec Digest

### 4.1 Edit Classification (`src/aggregate/classify.ts`)
Input: `{ reason?: 'undo'|'redo', changes: { textLen: number, rangeLen: number }[], isActiveDoc: boolean, windowActive: boolean }`
Rules in order:
1. `!isActiveDoc || !windowActive` -> `'external'`
2. `reason === 'undo' || reason === 'redo'` -> `'undo'` / `'redo'`
3. `changes.length === 0` -> `'external'`
4. `total = sum(max(textLen, rangeLen))`, `maxLen = max(max(textLen, rangeLen))`
5. `total > 2000 || (changes.length > 20 && maxLen > 3)` -> `'bulk'`
6. `changes.length === 1 && rangeLen === 0 && textLen > 40` -> `'paste'`
7. Otherwise -> `'typing'`
Only count documents with URI scheme `'file'` or `'untitled'`.

### 4.2 10-Second Summary (`ed_summary`)
Fields:
`type`, `windowStart`, `durMs`, `focusedMs`, `activeMs`, `taskKey`, `taskType`, `lang`, `typingChars`, `deletions`, `undo`, `redo`, `pasteLike`, `bulk`, `external`, `cursorMoves`, `scrolls`, `fileSwitches`, `saves`, `diagErr`, `diagWarn`, `dirtyDocs`, `openTabs`, `debugActive`, `debugPaused`, `uncommitted`, `gapP50Ms`, `gapP90Ms`, `longPauseRatio`.
Rhythm: Gaps between consecutive 'typing' edits. Ignore gaps > 60000ms. `longPauseRatio`: share of remaining gaps > 5000ms. 200-gap ring buffer.

### 4.3 Task Context & Natural Breaks
- `taskKey`: HMAC-SHA256(`workspaceFolder + "|" + branch`) truncated to 16 hex characters.
- `taskType`: Dominant mode over 60s (`'debugging'`, `'testing'`, `'coding'`, `'reading'`, `'idle'`).
- `ed_break`: Detected at natural transitions: `save_green` (0.7), `test_ok` / `build_ok` (0.8), `commit` (0.9), `branch_switch` (0.9), `debug_end` (0.7), `file_closed_clean` (0.6), `idle_after_save` (0.6).

### 4.4 Resume Assist & Censoring
- Snapshot stored strictly in `workspaceState` on blur >= 20s or idle >= 60s.
- Away time >= `minAwayMinutes` (default 3): randomized assist decision `p = clamp(assistProbability, 0.05, 0.95)`.
- Always measure resumption: 12 typing edits within 30s in same `taskKey`.
- Emit `ed_resume`: `{ awayMs, assist, propensity, experiment, used, lagMs, resumed, taskKey, load }`.
- Censoring: If no resumption within 30 min, emit `resumed: false, lagMs: 1800000`.

### 4.5 Engine Link & Outbox
- Discovery: `~/.dhyan/endpoint.json` watched via `fs.watch`, plus `vscode://dhyan.dhyan-vscode/connect` URI handler.
- Connection: WebSocket to `ws://127.0.0.1:<port>/ext` with `Authorization: Bearer <token>`.
- Client -> Engine: `hello`, `events` (batches of <= 200), `feedback`, `ping`.
- Engine -> Client: `welcome`, `ack`, `state`, `digest`, `nudge`, `cfg`, `cmd`, `pong`.
- Outbox: In-memory ring buffer (cap 5000) with JSONL disk spill (cap 5MB) in `globalStorageUri`. Sequence monotonic per epoch. Replay seq > `ackedUpTo` on reconnect.

---

## 15. Commands, UI & Status Bar
- Commands: `dhyan.pause`, `dhyan.startSession`, `dhyan.endSession`, `dhyan.park`, `dhyan.resume`, `dhyan.openDigest`, `dhyan.connect`, `dhyan.diagnostics`, `dhyan.forgetToday`.
- Keybindings: `ctrl+alt+p` (park), `ctrl+alt+r` (resume).
- Views: `dhyan.today`, `dhyan.resume`, `dhyan.digest`.
- Status bar states:
  - `$(eye) Dhyan · Deep focus {m}m · {n} held`
  - `$(history) Resume: {file}:{line}`
  - `$(debug-pause) Dhyan paused`
  - `$(plug) Dhyan: waiting for desktop app`

---

## 16. Section 16: Acceptance & Compliance Audit Checklist

| Item # | Verification Category | Criteria |
|---|---|---|
| 16.1 | Single Privacy Choke Point | Every outbound event is sanitized by `PrivacyGate.sanitize(event)` against an allowlist schema; unknown fields dropped. |
| 16.2 | Zero-Content Guarantee | No document text, file paths, URLs, branch names, commit text, diagnostic text, terminal commands, or notes in events/logs. |
| 16.3 | Local Network Boundary | Network connections strictly limited to `127.0.0.1`. No external network requests or telemetry. |
| 16.4 | Snapshot Isolation | Editor resumption snapshots containing paths/lines are stored solely in `context.workspaceState`. |
| 16.5 | Hot-Path Handler Efficiency | Hot event handlers (`docChange`, selection, scroll) only perform in-memory increments / ring-buffer pushes without async or I/O. |
| 16.6 | Activation Performance | Extension activates on `onStartupFinished` without blocking synchronous I/O or network calls. |
| 16.7 | Pure Module Decoupling | Core domain logic (classifier, rhythm, aggregator, outbox, break detector) is pure TypeScript decoupled from `"vscode"`. |
| 16.8 | Injected Clock Determinism | All time calculations in pure modules use an injected `Clock { now(), mono() }` interface. |
| 16.9 | Edit Event Classification | Implements pure 6-class edit classifier (`typing`, `paste`, `bulk`, `external`, `undo`, `redo`) with boundary handling. |
| 16.10 | Typing Rhythm Metrics | Computes `p50`, `p90`, and `longPauseRatio` across a 200-gap ring buffer, filtering out pauses > 60s. |
| 16.11 | 10-Second Periodic Summaries | Aggregates and flushes `ed_summary` containing active/focused duration, edit types, and context loads. |
| 16.12 | Task Context & Natural Breaks | Hashes workspace/branch for `taskKey`; infers 60s `taskType`; detects natural breakpoints (`ed_break`). |
| 16.13 | Resume Assist with Censoring | Tracks resumption delay with randomized propensity logging; caps censored unresumed tasks at 1800000ms. |
| 16.14 | Outbox Queue & Disk Spill | Bounded memory outbox (5000), JSONL spill (5MB), monotonic epoch seq, and watermark replay on `welcome`. |
| 16.15 | Quiet UX Policy | No modal dialogs or toasts by default; quiet status bar states; silent failure when offline; rate-limited nudges. |
| 16.16 | Extension Commands & Tree Views | Registers exact commands, keybindings, and tree views container (`today`, `resume`, `digest`). |

---

## Appendix A: Configuration Settings (`package.json`)
- `dhyan.endpointFile`: Path to endpoint JSON file (default `~/.dhyan/endpoint.json`)
- `dhyan.assistProbability`: Propensity for resume assist experiment (default `0.8`, range `0.0` - `1.0`)
- `dhyan.minAwayMinutes`: Minimum absence duration before triggering resume assist (default `3`)
- `dhyan.autoRestore`: Whether clicking resume automatically restores editor state (default `false`)
- `dhyan.nudges.maxPerHour`: Maximum nudges allowed per hour (default `4`)
- `dhyan.nudges.toast`: Allow toast notifications for nudges (default `false`)
- `dhyan.digest.toastAtBreak`: Allow toast notification for digest at natural breaks (default `false`)
- `dhyan.exclude.languages`: Array of excluded language IDs
- `dhyan.exclude.globs`: Array of excluded file glob patterns
- `dhyan.exclude.workspaces`: Array of excluded workspace paths/hashes
- `dhyan.diagnostics.verbose`: Enable verbose numeric logs in output channel (default `false`)
