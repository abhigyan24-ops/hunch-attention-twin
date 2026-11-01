# Technical Decisions & Assumptions: Dhyan VS Code Extension

- **MODE A SELECTED:** Existing extension identified in `vscode-ext/` (`package.json`, `src/extension.js`, `companionProvider.js`, `insightsPanel.js`); migrating to strict TypeScript while preserving Bento/Pet UI and legacy test compatibility.
- **ENGINES VERSION:** Setting `engines.vscode` to `^1.93.0` with feature detection to maintain compatibility with `^1.80.0` test harnesses.
- **SALT STORAGE:** Per-install HMAC-SHA256 salt generated as 32 random bytes on first run and persisted in VS Code `context.secrets`.
- **CLOCK INJECTION:** Injected `Clock` interface `{ now(): number, mono(): number }` applied across all pure aggregation and privacy modules.
- **DUAL-STACK ADAPTER:** Preserving exports for `DhyanVsCodeSensor`, `DhyanCompanionViewProvider`, and `DhyanInsightsPanel` in `vscode-ext/` to prevent regressions in `tools/test/vscode-ext.test.mjs`.
- **DISCOVERY FALLBACK:** In the absence of `~/.dhyan/endpoint.json`, sensor polls with backoff and defaults to port 47821 without emitting popup errors.
- **WINDOW FILTERING:** Edit counting strictly restricted to URI schemes `'file'` and `'untitled'`, discarding `'output'`, `'git'`, `'vscode-userdata'`, and `'comment'`.
- **PROMPT OVERRIDE PRECEDENCE:** In accordance with Master Prompt Source of Truth rule 3, Master Prompt HARD RULES and SPEC DIGEST override any ambiguous wording in prior design notes.
