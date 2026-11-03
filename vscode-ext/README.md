# Dhyan VS Code Sensor & Attention Companion

A privacy-preserving telemetry sensor, resume assist engine, and quiet attention actuator for **Dhyan (ध्यान)** — the local-first cross-device attention digital twin.

---

## 1. Overview & Capabilities

The Dhyan VS Code extension serves three strictly bounded responsibilities:

1. **Sensor**: Periodically observes and reports coarse coding activity summaries (counts and durations only, zero code or document text) to a local desktop engine over localhost (`127.0.0.1`).
2. **Resume Assist**: Captures local editor snapshots in `workspaceState` upon window blur or idle periods; when returning from an interruption, calculates resumption delay and helps users return seamlessly to where they left off.
3. **Quiet Actuator**: Presents status bar focus states, holds non-urgent notifications during deep work, displays a natural-break digest, and accepts voluntary session focus/energy ratings without becoming an interruption itself.

---

## 2. Hard Privacy Guarantees & Non-Leaking Invariants

Privacy is the primary invariant of this extension. Every outbound event passes through a single choke point: `PrivacyGate.sanitize(event)`.

### Strict Banned Content Invariants:
- **Zero Document Text**: File content, typed keystrokes, and clipboard text are never read, buffered, or logged.
- **Zero File Paths or URLs**: File paths and repository URLs are excluded from all outbound events. Resumption snapshots containing file paths are stored purely in `context.workspaceState` on local disk and never sent across the network.
- **Zero Commit Messages / Branch Names**: Git sensors monitor branch change events and uncommitted file counts only. Branch names and workspace roots are salted and hashed via HMAC-SHA256 (`taskKey`) using a per-install 32-byte secret salt stored in VS Code `SecretStorage`.
- **Zero Terminal Command Lines**: Shell integration checks only the command category (e.g., `build`, `test`, `git`, `run`, `editor`) against an allowlisted binary prefix map. Raw command strings and arguments are discarded.
- **Zero Diagnostic Text**: Only error and warning counts (`diagErr`, `diagWarn`) are captured; error messages and diagnostic text are never logged.
- **Local Network Boundary Only**: Network connections are strictly restricted to `127.0.0.1`. No external analytics, cloud servers, or third-party telemetries are contacted.
- **Remote Window Safe**: If running on a remote host (WSL, SSH, Codespaces via `env.remoteName`), localhost connections are disabled by policy and marked as `remote-unsupported` to prevent accidental network exposure.

---

## 3. Outbound Event Schema Allowlist

All outbound events match the allowlist schemas below. Any unknown or unlisted property is automatically dropped by `PrivacyGate.sanitize`.

### `ed_summary` (10-Second Periodic Window)
| Field | Type | Description |
|---|---|---|
| `type` | `"ed_summary"` | Event discriminator |
| `ts` | `number` | Unix epoch timestamp (ms) |
| `mono` | `number` | Monotonic clock timestamp (ms) |
| `windowStart` | `number` | Window start Unix timestamp (ms) |
| `windowId` | `string` | Unique 12-hex window identifier |
| `durMs` | `number` | Elapsed duration of window (ms) |
| `focusedMs` | `number` | Time window was focused (ms) |
| `activeMs` | `number` | Time user was actively editing or navigating (ms) |
| `taskKey` | `string` | 16-hex salted HMAC of workspace + branch |
| `taskType` | `enum` | Inferred task category: `coding`, `reading`, `testing`, `debugging`, `idle` |
| `lang` | `string` | Active editor language ID (e.g. `typescript`, `python`) |
| `typingChars` | `number` | Characters typed |
| `deletions` | `number` | Characters deleted |
| `undo` / `redo` | `number` | Undo/redo operation counts |
| `pasteLike` / `bulk` | `number` | Large insertion counts |
| `external` | `number` | External or background document modifications |
| `cursorMoves` / `scrolls` | `number` | Navigation action counts |
| `fileSwitches` / `saves` | `number` | Editor tab and file save counts |
| `diagErr` / `diagWarn` | `number` | Active error and warning counts |
| `dirtyDocs` / `openTabs` | `number` | Open tab count and unsaved document count |
| `debugActive` / `debugPaused` | `boolean` | Debugger execution and pause states |
| `uncommitted` | `number` | Git uncommitted change count |
| `gapP50Ms` / `gapP90Ms` | `number` | Median and 90th percentile inter-keystroke pauses |
| `longPauseRatio` | `number` | Ratio of typing pauses > 2 seconds |

### `ed_break` (Natural Breakpoint Event)
| Field | Type | Description |
|---|---|---|
| `type` | `"ed_break"` | Event discriminator |
| `ts` / `mono` | `number` | Timestamps (ms) |
| `kind` | `enum` | Break cause: `save_green`, `idle_after_save`, `test_ok`, `build_ok`, `commit`, `branch_switch`, `debug_end`, `file_closed_clean` |
| `confidence` | `number` | Confidence score between 0.0 and 1.0 |

### `ed_resume` (Resumption Assist Experiment Event)
| Field | Type | Description |
|---|---|---|
| `type` | `"ed_resume"` | Event discriminator |
| `ts` / `mono` | `number` | Timestamps (ms) |
| `taskKey` | `string` | 16-hex salted HMAC of task context |
| `awayMs` | `number` | Absence duration before returning |
| `offered` | `boolean` | Whether resume assist candidate was identified |
| `used` | `boolean` | Whether user clicked resume assist |
| `resumed` | `boolean` | Whether user resumed typing (12 edits in 30s) |
| `lagMs` | `number` | Delay to resumption (censored at 1,800,000ms if not resumed) |
| `propensity` | `number` | Logged propensity score (`clamp(p, 0.05, 0.95)`) for counterfactuals |

### `ed_session` (Focus Session Event)
| Field | Type | Description |
|---|---|---|
| `type` | `"ed_session"` | Event discriminator |
| `ts` / `mono` | `number` | Timestamps (ms) |
| `sessionId` | `string` | Unique session identifier |
| `action` | `enum` | `"start"` or `"end"` |
| `durMs` | `number` | Session duration (ms) |
| `focusRating` | `number` | User self-rated focus score (1–5; 0 if skipped) |
| `energyRating` | `number` | User self-rated energy score (1–5; 0 if skipped) |
| `intentionHash` | `string` | Salted 16-hex HMAC of stated intention |
| `intentionLength` | `number` | Character length of stated intention |

### `ed_feedback` (User Nudge / Digest Interaction)
| Field | Type | Description |
|---|---|---|
| `type` | `"ed_feedback"` | Event discriminator |
| `ts` / `mono` | `number` | Timestamps (ms) |
| `id` | `string` | Nudge or item ID |
| `target` | `string` | Target category or component |
| `action` | `enum` | `"useful"`, `"not_useful"`, `"dismiss"`, or `"click"` |

---

## 4. Architecture & Hot-Path Performance

- **Non-blocking Event Loops**: Document edit handlers perform only lightweight array pushes and counter increments (< 0.001ms per edit against a 0.05ms budget). No JSON serialization or string allocation occurs on the hot path.
- **Activation on Startup Finished**: Activates exclusively on `onStartupFinished` to ensure zero impact on editor launch times (< 50ms startup latency).
- **Outbox Ring Buffer & Disk Spill**: Events are held in an in-memory queue (capacity 5,000). If capacity is exceeded, oldest events spill to `outbox_spill.jsonl` with an enforced 5MB cap. Sequence numbers are strictly monotonic per session epoch and replayed from watermark upon reconnection.
- **Multi-Window Debounce**: Multi-window setups isolate window instances via unique `windowId`s. Active network connections are maintained by the focused window, with a 2-second debounce on focus switches to prevent reconnect storms.

---

## 5. Configuration Settings

| Setting | Default | Description |
|---|---|---|
| `dhyan.endpointFile` | `~/.dhyan/endpoint.json` | Path to the desktop engine discovery file |
| `dhyan.assistProbability` | `0.8` | Assist propensity for randomized resume experiment |
| `dhyan.minAwayMinutes` | `3` | Minimum absence duration before offering resume assist |
| `dhyan.autoRestore` | `false` | Automatically restore cursor position on resume click |
| `dhyan.nudges.maxPerHour` | `4` | Maximum allowable nudges per sliding hour |
| `dhyan.nudges.toast` | `false` | Display toasts for nudges instead of quiet status bar items |
| `dhyan.digest.toastAtBreak` | `false` | Notify with a quiet toast when held items exist at natural breaks |
| `dhyan.exclude.languages` | `[]` | Excluded language IDs (e.g. `["log", "env"]`) |
| `dhyan.exclude.globs` | `["**/.env*", "**/secrets/**"]` | Glob patterns excluded from telemetry tracking |
| `dhyan.exclude.workspaces` | `[]` | Excluded workspace paths or hashes |
| `dhyan.diagnostics.verbose` | `false` | Log numeric summaries to the output channel |

---

## 6. Commands & Keybindings

- **`dhyan.pause`**: Toggles attention tracking on or off.
- **`dhyan.startSession`**: Begins a focused work session with optional intention.
- **`dhyan.endSession`**: Concludes the active focus session and requests 1–5 focus/energy ratings.
- **`dhyan.park`** (`Ctrl+Alt+P`): Parks a note and snapshot of current working position.
- **`dhyan.resume`** (`Ctrl+Alt+R`): Restores the editor to the last parked or blur location.
- **`dhyan.openDigest`**: Opens held notifications for review at natural transition points.
- **`dhyan.diagnostics`**: Displays real-time connection state, capabilities, and telemetry buffer sizes.
- **`dhyan.forgetToday`**: Clears today's local session history, pending digests, and memory events.
