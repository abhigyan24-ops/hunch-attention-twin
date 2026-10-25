# Stage 9 Extensions Implementation Report (9X1 & 9X2)

Date: 2026-10-25  
Author: Antigravity  
Branch: `s9-ext` in worktree `C:\Users\abhig\OneDrive\Desktop\dhyan-ext`

---

## 1. Overview
In accordance with the Stages 7, 8, 9 coordination guide (`docs/contracts/SYNC_CONTRACT.md` and `COORDINATION.md` approved under Overlap mode), Prompts 9X1 and 9X2 deliver the two client extensions that feed real-time telemetry to the laptop app's local ingest server (`ws://127.0.0.1:<port>/ingest`).

Both extensions strictly enforce the contract privacy guarantee: **counts and categories only. Zero document text, file paths, URLs, page titles, domains, search queries, or keystrokes.**

---

## 2. Deliverables

### 2.1 Prompt 9X1: VS Code Extension (`vscode-ext/`)
- `package.json`: VS Code extension configuration declaring `dhyan.ingestPort` (default 47821) and `dhyan.token`, contributing commands `dhyan.reconnect` and `dhyan.showStatus`.
- `src/extension.js` (`DhyanVsCodeSensor`):
  - Connects to `ws://127.0.0.1:<port>/ingest` without an Origin header.
  - Sends `auth` within 3 seconds with token; receives `auth_ok`.
  - Heartbeat timer every 5 seconds (`ext_heartbeat`).
  - Text document changes (`workspace.onDidChangeTextDocument`): counts edits only, never inspecting document text or file paths.
  - Document saves (`workspace.onDidSaveTextDocument`): increments save counter.
  - Diagnostics (`languages.onDidChangeDiagnostics`): counts errors and warnings.
  - Resumption delay (`resume_first_edit`): records timestamp when window focus is regained (`window.onDidChangeWindowState`), measures duration in milliseconds to the first edit burst, clamped to [0, 3600000] ms.
  - Periodic burst aggregation (`edit_burst`): computes scaled `editsPerMin` (0..2000), saves, errors, warnings over a configured window (e.g. 30s).
  - State machine (`vscode_state`): evaluates `flowing` (active editing, low errors), `stuck` (>= 3 errors with low edit progress), and `idle_open` (unfocused or > 5 min without edits).
  - Debug tracking: tracks `onDidStartDebugSession` / `onDidTerminateDebugSession`.
  - Reconnect with backoff on connection drop.

### 2.2 Prompt 9X2: Browser Extension (`browser-ext/`)
- `manifest.json`: Manifest V3 extension for Chromium/Edge with permissions `tabs`, `idle`, `storage`, and host permissions `<all_urls>`.
- `domain-classifier.js`:
  - Maps domain names locally to the 14 contract categories: `coding`, `docs`, `communication`, `social`, `video`, `short_video`, `games`, `shopping`, `news`, `music`, `learning`, `utility`, `system`, `unknown`.
  - Pure function; returns category string only. Never leaks domain names or URLs.
- `content.js`:
  - Counts scroll and wheel events per window.
  - Observes `<video>` elements to detect `playing`, `paused`, or `none`.
  - Immediately classifies YouTube `/shorts` vs `/watch` into `surface: "shorts" | "watch"`.
  - Detects `feed`, `chat`, `editor`, and `other` surfaces.
  - Dispatches aggregated metrics to the background service worker.
- `service-worker.js` (`DhyanBrowserSensor`):
  - Connects to `ws://127.0.0.1:<port>/ingest` passing `Origin: chrome-extension://${extensionId}`.
  - Performs token auth within 3 seconds; receives `auth_ok`.
  - Heartbeats every 5 seconds (`ext_heartbeat`).
  - Listens to active tab changes (`chrome.tabs.onActivated`, `chrome.tabs.onUpdated`).
  - Emits `tab_category` frame (`kind`, `ts`, `domainCat`, `surface`, `videoState`, `scrollCount`, `tabActive`).
  - Persists port and token in `chrome.storage.local`.

---

## 3. Test & Verification Results

### 3.1 Extension Test Suites
- `tools/test/vscode-ext.test.mjs`: Tests end-to-end connection to `startMockIngest`, token authentication, heartbeat, `edit_burst`, `resume_first_edit` delay, `vscode_state`, and validates that all emitted frames conform to JSON schema and contain 0 privacy violations.
- `tools/test/browser-ext.test.mjs`: Tests domain classifier taxonomy against 14 categories, origin validation, authentication, tab URL classification, video and scroll metrics, and validates that all emitted frames conform to JSON schema and contain 0 privacy violations.

### 3.2 Full Test Suite (`npm test`)
```
> test
> node --test tools/test/*.test.mjs

ℹ tests 74
ℹ suites 0
ℹ pass 74
ℹ fail 0
ℹ duration_ms 1849.901
```

### 3.3 Checks (`npm run checks`)
```
> checks
> node tools/check-schema-sync.mjs && node tools/check-twin-purity.mjs && node tools/check-no-persona-imports.mjs

schema-sync: PASS (1.0.0; 1 Kotlin version file(s))
twin-purity: PASS (96 twin file(s); absent modules are not target verification)
no-persona-imports: PASS (235 production file(s); absent modules are not target verification)
```

### 3.4 Contract Checks (`npm run check:contracts`)
```
> check:contracts
> node --test tools/test/contract-*.test.mjs

ℹ tests 16
ℹ suites 0
ℹ pass 16
ℹ fail 0
```
