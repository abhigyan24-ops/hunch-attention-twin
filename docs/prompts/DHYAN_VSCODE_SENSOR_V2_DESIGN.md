# DHYAN VS Code Sensor v2: accuracy-first design

Purpose: upgrade the existing Dhyan VS Code extension (9X1) from simple counters to a validated sensor that reports `edit_burst`, `vscode_state` (flowing / stuck / idle_open), and `resume_first_edit` as accurately as the VS Code API allows, without ever reading code, file names, paths, commands or messages.

Output contract is unchanged: `docs/contracts/SYNC_CONTRACT.md` section 9 (local ingest, counts and categories only). This document changes **how the numbers are computed**, not what is sent.

Status: design only. I have not run or tested anything. Sources are listed in section 13; items marked **(verify)** were not confirmed in this session.

---

## 0. Honest framing (read first)

1. **No physics gives ground truth of attention.** "Flowing" and "stuck" are *inferred states*. What we can do is: measure real signals correctly, use statistics that suit the data, personalise thresholds per user, add hysteresis, and **validate against labels you create**. Anything beyond that would be claiming accuracy we cannot show.
2. **"Accurate" means measurable.** Section 9 defines the labelled-data protocol and acceptance gates. No accuracy figure goes in the pitch until it is computed from your labelled data by a script.
3. **Blind spots exist.** VS Code exposes no global keyboard hook, no terminal typing, no chat-panel typing, no webview input. The sensor must report these as *unobserved*, not as idle certainty (section 7.4).
4. **Privacy beats accuracy.** Where an accuracy gain needs reading content, the design either uses a numeric-only audited path (decision E-1) or drops the feature.

---

## 1. What the research and existing tools tell us to build

### 1.1 Prior art and what to borrow

| Source | What it does | What we borrow | What we avoid |
|---|---|---|---|
| **WakaTime plugins** | Heartbeats on file switch, on save/write, and about every 2 minutes of continued activity; durations built by joining heartbeats within a keystroke timeout (default 15 min); sends file paths, project and branch names | Event-driven heartbeats, throttling, timeout-merging for long-horizon "coding time" | Sending paths, project or branch names |
| **ActivityWatch `aw-watcher-vscode` and the heartbeat model** | Sends editor heartbeats (file, project, language) with a `pulsetime`; the server merges adjacent identical events within that window into one longer event | The **pulsetime merge** idea for gap-free interval accounting; one bucket per watcher and host | Content-bearing fields (file, project) |
| **ActivityWatch AFK watcher** | Idle detection by input timeout (a third-party summary lists a 180 s default; verify) | Idle timeout as a configurable parameter | OS-level global input hooks (not available to an extension anyway) |
| **Wakapi** | Re-implements WakaTime duration logic; had a padding rule, then moved to WakaTime-style timeouts (default 10 min, range 1-60 min) | Lesson: duration math is sensitive to timeout design; make it explicit and testable | Padding rules (they distort short sessions) |
| **Parnin and Rugaber, resumption strategies** (10,000 sessions, 86 programmers) | Resumption lag after interruptions; only about 10% of sessions resume coding within 1 minute; about 7% edit without first navigating; often 10-15 minutes before the first edit | Define resumption lag precisely: time from refocus to first *user* edit, plus navigation as a cue-seeking signal | Treating lag as a quality score for a single user without a baseline |
| **Task-interruption studies in software teams** (arXiv 1805.05508, 4,910 task logs, 17 developers) | Self-interruptions can be more disruptive than external ones; many interrupted tasks are never resumed | Separate "interruption cause" categories later; keep resume tracking per context | Over-interpreting task-switch counts |
| **Keystroke-logging research in writing** | A pause threshold of about 2 seconds is the common convention; inter-key intervals are heavily right-skewed and often bimodal (fluent vs hesitant transitions); fixed thresholds are arbitrary and a mixture model is a principled alternative | **2 s default pause**, then a **personalised two-component mixture** on log intervals | Using the mean inter-key interval |

### 1.2 Open-source code to read before writing anything
(Read for structure and edge cases; do not copy code unless its licence allows, and credit it.)
- `wakatime/vscode-wakatime` (heartbeat triggers, throttling, handling of file saves, focus and AFK timing).
- `ActivityWatch/aw-watcher-vscode` and `aw-client-js` (the heartbeat queue, pulsetime, reconnect behaviour).
- ActivityWatch docs on buckets, events and the `app.editor.activity` event type; the Python `heartbeat_merge` description.
- VS Code API reference (`TextDocumentChangeEvent`, `TextEditorSelectionChangeKind`, `WindowState`, shell integration events, diagnostics, tasks, debug). **(verify the API surface against your `engines.vscode` version)**
- The VS Code issue that led to the `reason` field on text-document change events (undo and redo are flagged; other causes are not).

---

## 2. Signals available in the VS Code API

| Signal | API | What it gives | Caveat |
|---|---|---|---|
| Document edits | `workspace.onDidChangeTextDocument` | `contentChanges[]` (range, rangeLength, inserted text), `reason` (**undo / redo only**, otherwise undefined) | Cannot tell typing from paste, completion, formatter, refactor, git checkout or agent edits without heuristics (section 4) |
| Cursor and selection | `window.onDidChangeTextEditorSelection` | `kind`: Keyboard, Mouse, Command | Every edit also moves the cursor; dedupe selection events within ~30 ms of a document change |
| Scroll and reading | `window.onDidChangeTextEditorVisibleRanges` | visible range changed | Good "reading" evidence when not editing |
| Active editor and tabs | `window.onDidChangeActiveTextEditor`, `window.tabGroups` events | switches and counts | Never read names or URIs |
| Window focus | `window.onDidChangeWindowState` | `focused`; recent versions also expose an `active` flag **(verify)** | Per window; several windows can exist |
| Save | `workspace.onDidSaveTextDocument`, `onWillSave` | save moments | Needed to recognise format-on-save bursts |
| Diagnostics | `languages.onDidChangeDiagnostics`, `languages.getDiagnostics` | counts by severity | Use counts and deltas only; never messages |
| Tasks | `tasks.onDidEndTaskProcess` | exit code | Names and command lines are forbidden |
| Terminal commands | `window.onDidStartTerminalShellExecution`, `onDidEndTerminalShellExecution` (shell integration) **(verify)** | start/end and exit code | Never read the command line; shell integration may be off |
| Debugging | `debug.onDidStartDebugSession`, `onDidTerminateDebugSession`, `activeDebugSession` | session active | Stopped-at-breakpoint state needs a debug adapter tracker; optional advanced feature (section 5.6) |
| Git | built-in Git extension API | HEAD change events | Count commits only; never messages or hashes |
| Notebooks | `workspace.onDidChangeNotebookDocument` | cell edits | Treat as editing |
| Environment | `env.remoteName`, `extension.extensionKind` | remote host detection | Matters for localhost (section 7.1) |

---

## 3. Architecture

Everything that decides a state is **pure TypeScript with no `vscode` import**, so it is unit-testable and replayable. The `vscode` API is touched only by thin adapters.

```
 VS Code events ──> src/api/*        (adapters: convert events to plain RawSignal objects, no content kept)
                      │
                      v
                 src/signals/*        (ChangeClassifier, FocusTracker, NavTracker, DiagTracker, RunTracker, DebugTracker)
                      │   typed SignalEvent {tMono, tWall, kind, numbers only}
                      v
                 src/features/*       (RollingWindow, EWMA, RobustBaseline, BurstMixture, TheilSen, IntervalUnion)
                      │   FeatureVector per 5 s tick
                      v
                 src/inference/*      (ActivityState, StuckModel, FlowModel, ResumptionTracker, Hysteresis)
                      │   SensorOutput {edit_burst, vscode_state, resume_first_edit}
                      v
                 src/privacy/*        (SchemaGuard against contracts/sync/schemas, forbidden-key walk)
                      │
                      v
                 src/transport/*      (existing ingest client: auth, heartbeat, backoff, 60 s buffer)

 src/calibration/*   local labels (enum + timestamp only), per-user baseline store (numbers only)
 src/replay/*        deterministic trace player used by tests and the evaluation script
```

Clocks: durations use a monotonic clock; `ts` uses wall time. If wall-minus-monotonic drifts by more than 2 s between ticks, treat it as a clock jump or system sleep: close all open intervals, emit no negative durations, restart rolling windows.

---

## 4. Change classification (the most important accuracy step)

An edit event can come from the user, a completion, a paste, a formatter, a refactor, git, an external tool, Live Share or an AI agent. Counting all of them as "typing" inflates activity and breaks the stuck model.

### 4.1 Decision tree (parameters in section 8; start from these, tune on golden traces)

```
1. reason == Undo   -> UNDO          reason == Redo -> REDO
2. window not focused OR document not in the active editor -> EXTERNAL   (never counts as user activity)
3. contentChanges.length > 1:
     all inserts are 1-2 chars (multi-cursor) -> TYPING_MULTI (count once per event)
     else                                      -> BULK  (format, refactor, rename, agent edit)
4. insertedLen == 0 and rangeLength > 0:
     rangeLength < 20 -> DELETE ; else -> BULK_DELETE
5. insertedLen in [1,2] and rangeLength == 0 -> TYPING
6. insertedLen >= 12 or newlines >= 3        -> PASTE_OR_COMPLETION
7. otherwise                                  -> AUTO_EDIT (snippet, auto-close, auto-indent, small completion)
Post-rule: a BULK or AUTO_EDIT within 800 ms after a save -> FORMAT
```

Only TYPING, TYPING_MULTI, DELETE and UNDO/REDO count as **user keystroke activity**. PASTE_OR_COMPLETION counts as *user-initiated edit* (weight 1 event, zero typed characters). BULK, BULK_DELETE, FORMAT and EXTERNAL count as **machine edits** and never extend flow or resume evidence.

### 4.2 Decision E-1 (owner): reading `insertedText.length`
Rules 3 to 7 need the *length* and newline count of inserted text. That means touching the text string in memory, which the current privacy check forbids. Proposal: one audited function `measureChange(change) -> {insertedLen, newlines, rangeLength}` is the **only** code allowed to read `.text`; it returns numbers and never stores, logs or transmits the string. The privacy lint allow-lists that one file and a canary test proves nothing leaks. If you reject E-1, the fallback uses only `rangeLength`, change count and timing; expect worse separation of typing from paste and completions, and say so in the pitch.

### 4.3 Typed-character estimate
`typedCharsPerMin = sum(insertedLen of TYPING and TYPING_MULTI)` over the window; this is a proxy, not a keystroke count.

---

## 5. Feature extraction and models

### 5.1 Time constants and smoothing
Leaky-integrator smoothing (an exponentially weighted average), time-step aware:

```
alpha = 1 - exp(-dt / tau)
A_t   = A_{t-1} + alpha * (x_t - A_{t-1})
```

Use `tau = 30 s` for rhythm features and `tau = 5 min` for the baseline-relative features. All `tau` values are parameters.

### 5.2 Activity evidence and gap-free active time (the heartbeat idea, done right)
Each user-activity signal creates an interval `[t, t + TTL(kind)]`; the union of intervals gives `activeSeconds` with no double counting (same idea as ActivityWatch's pulsetime merge).

| Kind | TTL (default, assumption) | Rationale |
|---|---|---|
| TYPING, DELETE, UNDO/REDO | 10 s | covers normal intra-burst pauses |
| PASTE_OR_COMPLETION | 10 s | |
| Selection change (Keyboard or Mouse, not edit-coincident) | 15 s | navigating code |
| Visible-range change (scroll) | 20 s | reading |
| Active debug session with stop evidence (optional, 5.6) | 60 s | thinking at a breakpoint |
| Task or terminal run in progress | 30 s | waiting for a build is not idle |

Separately keep a **long-horizon timeout counter** (default 15 min, WakaTime-style) for reports only. Never use the long timeout for state inference.

### 5.3 Typing rhythm: personalised burst model
Intervals between user keystroke events are heavy-tailed; model them as a mixture on the log scale:

```
x = ln(interval_ms)
x ~ pi * N(mu_f, s_f^2) + (1-pi) * N(mu_h, s_h^2)      f = fluent, h = hesitant
```

Online EM with forgetting (half-life about 1 hour of typing). `tau_pause` is the interval where `P(hesitant | x) = 0.5`, clamped to `[0.5 s, 5 s]`. **Cold start:** use `tau_pause = 2 s` (the writing-research convention) until at least 200 intervals are seen. Derived features per 15 s: burst rate (bursts/min), mean burst length (events), pause fraction (time in hesitant gaps / time typing-active), P-burst count.

### 5.4 Robust per-user baselines
Rolling 14-day median and MAD per feature (optionally per hour-of-day band), stored locally as numbers only:

```
z = (x - median) / (1.4826 * MAD),    clipped to [-3, 3]
```

Cold start (fewer than 3 active days): use only absolute rules from section 6 and report `uncalibrated` in diagnostics.

### 5.5 Trend and change detection
- Diagnostics error count over the last 5 min: **Theil-Sen slope** (median of pairwise slopes) is robust to spikes; "rising" means positive slope and last value above baseline plus 1 MAD.
- Optional **CUSUM** on activity rate to detect sustained drops (interruptions) without being fooled by single pauses.
- **Schmitt-trigger hysteresis** (separate enter and exit thresholds plus minimum dwell) on every state, to stop flicker.

### 5.6 Optional advanced: debug "stopped" evidence
A debug adapter tracker can see protocol messages. Read **only** the message type field (`event == "stopped"` or `"continued"`), discard everything else, and add a unit test with a canary. Gives: time paused at a breakpoint counts as thinking, not idle. If the privacy review rejects it, skip it.

### 5.7 Navigation-versus-edit ratio
`navPerEdit = nonEditSelectionEvents / max(1, userEditEvents)` over 60 s. High values with few edits are consistent with searching or reading; they are evidence for "stuck" only together with other signals.

---

## 6. State inference

Output states (contract): `flowing`, `stuck`, `idle_open`, plus `windowFocused` and `debugging` booleans. Internal states also include `away` (window blurred) and `unknown_input` (see 7.4).

### 6.1 idle_open
Window focused, no user-activity evidence interval active for **60 s** (parameter `T_idle`, aligned with the laptop's 7A boundary), and not in a task/terminal run. Output `idle_open`. Known limitation: chat panel, terminal typing and webviews are unobserved (7.4).

### 6.2 flowing (enter / exit with hysteresis)
Enter when, over the last 5 min with at least 3 min of observed focus:
- user-activity coverage (`activeSeconds / observedFocusSeconds`) >= 0.6, and
- no stuck evidence, and
- window switches/blurs <= 1 in the window, and
- machine-edit share of edits < 0.5.
Exit when coverage < 0.35 for 90 s, or blur >= 60 s, or stuck enters. Thresholds are assumptions to calibrate.

### 6.3 stuck (evidence score, not a single threshold)
Compute a score from z-features (robust baselines, clipped):

```
S = w1*z(deleteRatio) + w2*z(undoRate) + w3*z(-netInsertRate) + w4*z(pauseFraction)
  + w5*z(navPerEdit)  + w6*max(0, z(diagErrSlope)) + w7*failedRuns10m + w8*fileThrash
  - w9*z(burstRate)
```

- `deleteRatio` = (DELETE + BULK_DELETE) / user edits; `undoRate` = UNDO per minute; `netInsertRate` = typed chars minus deleted chars per minute.
- `failedRuns10m` = non-zero exits from tasks or shell-integration executions in 10 min (exit codes only).
- `fileThrash` = active-editor switches among the same two or three editors in 3 min (counts only).
- **Initial weights (proposal, uncalibrated):** all `w = 1` except `w9 = 0.5`; scale `S` to a 0-1 range by dividing by the sum of positive weights. These are starting points, not findings.
- Enter when `S >= theta_hi` for `T_enter = 3 min`; exit when `S <= theta_lo` for `T_exit = 2 min`. Defaults `theta_hi = 0.6`, `theta_lo = 0.4` (assumptions).
- After calibration (section 9), replace the linear score with a logistic model `p = sigmoid(b0 + b.f)` fitted on your labels, keeping the same hysteresis. Report `confidence = uncalibrated` until Brier/ECE are computed.
- `stuck` never fires without at least two independent evidence families (for example edits plus diagnostics, or edits plus failed runs) to avoid labelling plain reading as stuck.

### 6.4 `edit_burst` message
Every 15 s window with user activity: `editsPerMin` (user keystroke events + paste/completion events, scaled to a minute), `saves`, `errors` and `warnings` (counts at window end), `windowSec`. Machine edits are excluded from `editsPerMin`.

### 6.5 Resumption (Parnin definition)
- **Interruption** = window blur or user-activity gap >= `T_int` (60 s) while a workspace was active. Debounce: re-blur within 5 s merges into the same interruption.
- **Refocus** = focus regained (or first user-activity signal after the gap while focused).
- **Resumption lag** = time from refocus to the first *user keystroke edit* (TYPING, DELETE, UNDO/REDO; not machine edits, not paste/completion unless nothing else occurs for 5 min).
- Also record internally the time to first navigation (cue-seeking), but only `resume_first_edit {delayMs}` is sent.
- Cap `delayMs` at 3,600,000; if no edit within 60 min, send nothing (censored), do not invent a value.
- Never emit when the interruption was shorter than `T_int`.

---

## 7. Environment realities

### 7.1 Remote windows, WSL, SSH, Codespaces
`127.0.0.1` from a remote extension host is **not** your laptop. If `env.remoteName` is set and the extension runs in the remote host, do not connect; show status "remote window: not supported" and report it in diagnostics. A UI-side design is possible but unverified; leave it as a documented limitation until tested **(verify)**.

### 7.2 Multiple VS Code windows
The ingest contract keeps one connection per client kind and the newest authenticated connection replaces the old one. Design: **only the focused window connects and sends**; unfocused windows stay disconnected and silent. Debounce focus changes (2 s) to avoid reconnect storms. Test with two windows.

### 7.3 Forks and variants
VS Code Insiders, VSCodium, Cursor and similar editors share most APIs but not always the same versions or shell-integration features. Feature-detect each API; never assume. Record the host name and version in diagnostics (not in events).

### 7.4 Blind spots and honesty
Unobserved: terminal typing (partly covered by shell-integration run events), AI chat panels, webviews, browser previews, reading documentation elsewhere. When the window is focused and the **only** evidence is "no signal", the output is `idle_open`; diagnostics must also expose `observedCoverage` so the laptop and docs can state "may be using unobserved panels". The laptop's confidence stays "uncalibrated" (decision DD-13).

### 7.5 AI agents and bulk edits
Edits applied by agents, refactors or git operations arrive as BULK/EXTERNAL and are excluded from user activity. Reviewing such changes shows up as selection and scroll activity, which correctly counts as reading.

### 7.6 Time and sleep
Laptop sleep or a clock jump closes all intervals (section 3). After resume, treat the first 5 s as warm-up before emitting.

---

## 8. Parameter table (single config object, every value an assumption unless cited)

| Parameter | Default | Basis |
|---|---|---|
| `T_idle` | 60 s | aligned with laptop 7A idle boundary (assumption) |
| `T_int` (interruption) | 60 s | same |
| `tau_pause` cold start | 2 s | writing-research convention |
| `tau_pause` clamp | 0.5-5 s | assumption |
| paste/completion length | 12 chars or 3 newlines | assumption; tune on golden traces |
| bulk-delete length | 20 chars | assumption |
| format-after-save window | 800 ms | assumption |
| selection-vs-edit dedupe | 30 ms | assumption |
| TTLs | 10 / 15 / 20 / 30 / 60 s | section 5.2 (assumption) |
| EWMA `tau` | 30 s, 5 min | assumption |
| baseline window | 14 days, median and MAD | assumption |
| flow: coverage enter / exit | 0.6 / 0.35 | assumption |
| stuck: `theta_hi` / `theta_lo` / `T_enter` / `T_exit` | 0.6 / 0.4 / 3 min / 2 min | assumption |
| long-horizon timeout (reports only) | 15 min | WakaTime default |
| tick | 5 s | assumption |
| heartbeat to laptop | 5 s | contract |

Expose these in `dhyan.sensor.*` settings or a JSON file; changing a value never changes the message schema.

---

## 9. Validation: how "accurate" becomes a measured number

1. **Golden traces** (deterministic replay, `src/replay/`): typing at slow and fast speeds; typing with auto-close brackets; paste of a large block; AI-style completion; format on save; rename refactor; undo storm; long reading with only scrolling; breakpoint pause; build with failing exit codes; Live Share or external file change; git checkout bulk change; two-window switching; laptop sleep and clock jump; blur and return with varied lags. Each trace has an expected classification and expected state timeline written by hand **before** tuning.
2. **Property tests:** no negative durations; no `resume_first_edit` without a prior interruption; machine edits never increase `editsPerMin`; state never flows while blurred; hysteresis prevents transitions faster than the minimum dwell; same input trace yields the same output (determinism).
3. **Labelling protocol (local, content-free):** two commands, keyboard-bindable: "Dhyan: mark stuck now" and "Dhyan: mark flowing now" (also "mark interrupted"). A label stores only `{ts, enum}`. For a week of real work, aim for 40+ labels across different days.
4. **Evaluation script** `tools/eval-sensor.mjs`: replays recorded feature windows against labels (label window is the 3 minutes before and after the mark) and prints: confusion matrix, precision and recall for stuck and flowing, false positives per hour, median time-to-detect, calibration (Brier score, ECE) once a probabilistic model exists. Output to `docs/validation/sensor-<commit>.md` with the commit hash and data size.
5. **Cross-check with other trackers:** run WakaTime or ActivityWatch on the same day and compare *active time* trends (expect correlation, not equality; their timeouts differ). Record the comparison; never claim equality.
6. **Acceptance gates:** set targets **after** the first labelled week (for example precision for `stuck`, false positives per hour). Until then, the release note says "rule-based, uncalibrated, no accuracy claim".
7. **Soak and performance:** 8 hours of replayed synthetic activity: CPU under 1% average, memory growth bounded (state objects capped; document the cap), no event-loop stalls above 50 ms attributable to the sensor.

---

## 10. Privacy and security (non-negotiable)

- Allow-list of fields read from each API; everything else is dropped at the adapter. No paths, file names, URIs, language ids, branch or commit text, diagnostic messages, terminal command lines, task names, notebook content.
- Single audited `measureChange` (decision E-1), single audited debug-tracker reader (5.6) if enabled.
- `tools/check-ext-privacy.mjs` extended: fails on any other read of `.text`, `getText`, `fileName`, `uri`, `languageId`, `commandLine`, `message`; also fails if a canary string (`CANARY_CODE_7f3a`) typed in the test workspace appears in logs, buffers or sent frames.
- Token stays in `SecretStorage`; connection only to `127.0.0.1`; `Origin` rules and auth are the contract's job (already enforced by the laptop).
- No telemetry, no network calls except the localhost ingest.
- Labels and baselines are local files containing numbers and enums only; a command "Dhyan: delete all sensor data" wipes them.
- Commands: "Dhyan: pause sensing" (visible in the status bar) and a setting `dhyan.enabled`. Respect Workspace Trust: in an untrusted workspace, run in limited mode or disabled (declare it in the manifest capabilities **(verify wording)**).

---

## 11. Implementation plan (milestones, parallel split)

Branch `s9-ext-v2` from `main`. Scope: `vscode-ext/**`, `tools/check-ext-privacy.mjs`, `tools/eval-sensor.mjs`, `docs/validation/`. Not allowed: laptop, phone, relay, contracts, core-spec (a message change needs a CCR).

| M | Deliverable | Exit |
|---|---|---|
| M0 | Audit existing 9X1: list every signal and rule it uses; read STAGE9_EXT_NOTES; plan; **stop for owner approval (decide E-1 to E-5)** | plan artifact |
| M1 | Interfaces: `RawSignal`, `SignalEvent`, `FeatureVector`, `SensorOutput`, config object, replay format | committed interfaces |
| M2 | `signals/` classifiers + adapters (ChangeClassifier, FocusTracker, NavTracker, DiagTracker, RunTracker) with golden-trace tests | tests green |
| M3 | `features/` (EWMA, robust baseline, burst mixture EM, Theil-Sen, interval union) with numeric tests against hand-computed values | tests green |
| M4 | `inference/` (activity, flow, stuck, resumption, hysteresis) + property tests | tests green |
| M5 | `privacy/` guards, canary tests, transport integration, single-focused-window logic, remote detection | tests green against `tools/mock-ingest` |
| M6 | `calibration/` labels, baseline store, delete-data command | tests green |
| M7 | `tools/eval-sensor.mjs`, soak test, docs | real output pasted |
| M8 | `MANUAL_CHECKS.md` for the owner (Extension Development Host, labelling week, compare with WakaTime/AW) | owner runs |

Parallel split when the tool supports it (lead writes M1 interfaces first, then): **S1** signals (M2), **S2** features (M3), **S3** privacy + transport + tests infrastructure (M5, replay harness), **S4** evaluation tooling and docs (M7, M8). Inference (M4) starts after S1 and S2 merge. Shared files (`package.json`, `tsconfig`, config object, notes) belong to the lead only.

---

## 12. Decisions for the owner

| ID | Decision | Recommendation |
|---|---|---|
| E-1 | Allow one audited function to read inserted-text *length* | Approve; it is the main accuracy lever |
| E-2 | Remote windows unsupported in v2 | Approve; document it |
| E-3 | Machine edits (bulk/agent/format/external) never count as user activity | Approve |
| E-4 | Local label commands (enum + timestamp) | Approve |
| E-5 | Parameters configurable and logged in diagnostics (numbers only) | Approve |
| E-6 | Optional debug-stopped tracker (5.6) | Defer until after M7 |

---

## 13. References (checked in this session unless marked verify)

- WakaTime FAQ, heartbeat triggers and keystroke timeout: https://wakatime.com/faq
- Wakapi, duration calculation and timeout changes: https://hub.docker.com/r/n1try/wakapi and https://newreleases.io/project/github/muety/wakapi/release/2.13.0
- Browser-extension time tracker built on the same heartbeat ideas (throttling and merging approach): https://github.com/JosephusPaye/yhee
- ActivityWatch data model, buckets, events, heartbeat merging and `app.editor.activity`: https://docs.activitywatch.net/en/latest/buckets-and-events.html
- ActivityWatch heartbeat merge in the server and Python API: https://docs.activitywatch.net/en/latest/api/python.html
- ActivityWatch JS client heartbeat queue and pulsetime: https://github.com/ActivityWatch/aw-client-js
- Parnin and Rugaber, Resumption Strategies for Interrupted Programming Tasks: https://sites.cc.gatech.edu/reverse/repository/resumptionstrategies.pdf
- Parnin, Programmer, Interrupted: https://www.gamedeveloper.com/programming/programmer-interrupted ; summary at https://www.infoq.com/news/2013/01/Interruptions/
- Parnin and DeLine, Evaluating Cues for Resuming Interrupted Programming Tasks (slides): https://scriptagc.wasmer.app/https_www_slideshare_net/slideshow/evaluating-cues-for-resuming-interrupted-programming-tasks/6202259
- Task Interruption in Software Development Projects: https://ar5iv.arxiv.org/html/1805.05508
- Keystroke pauses and bursts in writing research: https://www.jowr.org/jowr/article/download/1318/979 and https://link.springer.com/article/10.1007/s11145-021-10203-z and https://link.springer.com/doi/10.1007/s11145-022-10284-4
- VS Code request that led to undo/redo change reasons: https://github.com/microsoft/vscode/issues/120617
- To read, not fetched here (verify current content): VS Code API reference https://code.visualstudio.com/api/references/vscode-api ; VS Code remote extension guidance https://code.visualstudio.com/api/advanced-topics/remote-extensions ; https://github.com/wakatime/vscode-wakatime ; https://github.com/ActivityWatch/aw-watcher-vscode

---

## 14. Lead prompt for the agent (paste after the coordination preamble)

```text
[PASTE PREAMBLE FROM DHYAN_STAGE789_PROMPTS.md]
[PASTE SECTION L FROM DHYAN_NEXT_PROMPTS.md]

You are in Antigravity or Codex (whichever the owner chose). Task V1: implement docs/prompts/DHYAN_VSCODE_SENSOR_V2_DESIGN.md. Branch s9-ext-v2 from main. Notes NOTES_s9-ext-v2.md.
Scope: vscode-ext/**, tools/check-ext-privacy.mjs, tools/eval-sensor.mjs, docs/validation/**. Not allowed: laptop/, phone/, relay/, core-spec/, docs/contracts/, contracts/sync/, tools/mock-*.
Allowed dependencies: none beyond the existing extension dev dependencies (typescript, esbuild, ws, @types/*). No new runtime libraries; implement EWMA, EM, Theil-Sen and CUSUM yourself with unit tests.

M0 (plan only, then STOP): audit the current extension; list every signal and rule; read STAGE9_EXT_NOTES.md; read the design document fully; report which parts conflict with the existing code or contract; confirm the VS Code engine version and which APIs in section 2 exist in it (verify, do not assume); list which decisions E-1..E-6 you need from the owner; produce the scope table and interfaces plan. Do not write code before approval.
Then M1..M8 exactly as the design's section 11, with these rules: pure logic has no vscode import; every threshold comes from the config object; golden traces and expected timelines are written BEFORE tuning; never tune numbers to make a test pass without recording why; the output messages stay exactly the contract's section 9 shapes (no new fields); machine edits never count as user activity; remote windows are unsupported; only the focused window connects. Paste real command output for every claim. Not verified on target: the real VS Code runtime, real laptop server, real usage. Report anything in the design you believe is wrong, with evidence, instead of silently changing it.
```
