# Hunch VS Code Extension Architecture & Technical Decisions

This document captures the architectural decisions, privacy boundaries, and runtime performance constraints of the Hunch VS Code Sensor and Attention Companion extension.

## 1. Architectural Principles
- **Engine Baseline:** Targets `engines.vscode` `^1.93.0` with feature detection (`src/capabilities.ts`) to remain compatible with standard test environments (`^1.80.0`).
- **Clock Injection:** Injected `Clock` interface (`{ now(): number, mono(): number }`) applied across aggregation and privacy modules (`src/clock.ts`). `activate(context, clock?)` accepts an optional clock for deterministic testing.
- **Dual-Stack Compatibility:** Exports `DhyanVsCodeSensor`, `DhyanCompanionViewProvider`, and `DhyanStatusBar` to maintain backward-compatibility with testing and ingest harnesses.

## 2. Privacy & Telemetry Hardening
- **Task Key Salting (P1/P2):** `taskKey` is generated strictly as `HMAC-SHA256(workspacePath + "|" + branchName, salt).slice(0, 16)`. File paths, folder names, and branch names remain strictly on-device.
- **Terminal Command Filtering (P1):** Command parsing inspects only the first whitespace token against a strict allowlist. Raw arguments and command lines are never persisted or emitted.
- **Intention Hashing (P1/P2):** User intention input during session starts is salted and hashed to 16 hex characters (`intentionHash`), with only the length recorded (`intentionLength`). Raw text is never stored in memory, events, or disk.
- **Snapshot Locality (P5):** Editor state snapshots (`filePath`, `line`, `character`, `parkedNote`) live strictly in `context.workspaceState` for resume assistance and are never transmitted over the network or serialized into telemetry events. Excluded files (`.env`, secrets) are rejected prior to capture.
- **Schema Sanitization:** All outgoing events are filtered through `PrivacyGate.sanitize`, dropping unknown fields and strings longer than 64 characters.

## 3. Sensor Hot-Path Performance
- **Editor Sensor:** Extracts only `.text.length` and `.rangeLength` without string allocations; measured average handler latency is < 0.001ms against a 0.05ms budget.
- **Window Filtering:** Activity tracking is strictly restricted to URI schemes `file` and `untitled`, discarding internal buffers (`output`, `git`, `vscode-userdata`).

## 4. Resilience & Network Fault Tolerance
- **Bounded Outbox:** Enforces a bounded memory ring buffer (5,000 events) and a 5MB JSONL disk spill file (`outbox_spill.jsonl`), retaining the newer half of lines upon reaching the cap.
- **Multi-Window Debounce:** Focus transitions are debounced by 2 seconds to avoid connection storms across multiple editor windows.
- **Remote Host Protection:** When running under a remote extension host (`vscode.env.remoteName`), localhost engine connections are disabled by policy to preserve the local machine security boundary.
