# Technical Decisions & Assumptions: Dhyan VS Code Extension

- **MODE A SELECTED:** Existing extension identified in `vscode-ext/` (`package.json`, `src/extension.js`, `companionProvider.js`, `insightsPanel.js`); migrating to strict TypeScript while preserving Bento/Pet UI and legacy test compatibility.
- **ENGINES VERSION:** Setting `engines.vscode` to `^1.93.0` with feature detection to maintain compatibility with `^1.80.0` test harnesses.
- **SALT STORAGE:** Per-install HMAC-SHA256 salt generated as 32 random bytes on first run and persisted in VS Code `context.secrets`.
- **CLOCK INJECTION:** Injected `Clock` interface `{ now(): number, mono(): number }` applied across all pure aggregation and privacy modules; `activate(context, clock?)` accepts optional clock for deterministic integration testing.
- **DUAL-STACK ADAPTER:** Preserving exports for `DhyanVsCodeSensor`, `DhyanCompanionViewProvider`, and `DhyanInsightsPanel` in `vscode-ext/` to prevent regressions in `tools/test/vscode-ext.test.mjs`.
- **DISCOVERY FALLBACK:** In the absence of `~/.dhyan/endpoint.json`, sensor polls with backoff and defaults to port 47821 without emitting popup errors.
- **WINDOW FILTERING:** Edit counting strictly restricted to URI schemes `'file'` and `'untitled'`, discarding `'output'`, `'git'`, `'vscode-userdata'`, and `'comment'`.
- **PROMPT OVERRIDE PRECEDENCE:** In accordance with Master Prompt Source of Truth rule 3, Master Prompt HARD RULES and SPEC DIGEST override any ambiguous wording in prior design notes.
- **HOT-PATH METRICS:** `EditorSensor` extracts only `.text.length` and `.rangeLength` without string building or awaits; measured 0.00063ms avg per edit against the 0.05ms budget.
- **OUTBOX SPILL STRATEGY:** Spills oldest 500 events to `outbox_spill.jsonl` at 5000 memory threshold and enforces 5MB disk cap by retaining the newer half of lines.
- **SAFE WEBSOCKET TEARDOWN:** `EngineClient` calls `ws.terminate()` on CONNECTING sockets to prevent unhandled "WebSocket was closed before the connection was established" exceptions.
- **TASK KEY PRIVACY (P1/P2):** `taskKey` is strictly `HMAC-SHA256(workspacePath + "|" + branchName, salt).slice(0, 16)`. Path and branch are strictly kept local and never emitted.
- **TERMINAL COMMAND PRIVACY (P1):** Terminal command parsing parses only the first whitespace token against a strictly allowlisted command map; raw command lines or arguments are never persisted or emitted.
- **NATURAL BREAK DETECTION MUTUAL EXCLUSION:** `save_green` (15s quiet after save with 0 errors, confidence 0.7) takes priority over `idle_after_save` (20s idle after save where errors were cleared, confidence 0.6) to prevent duplicate break triggers. `test_ok` and `build_ok` flush immediately upon successful task completion.
