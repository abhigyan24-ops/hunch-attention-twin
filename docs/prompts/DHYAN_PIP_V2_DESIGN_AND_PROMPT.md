# DHYAN Pip v2: movable pet and distraction reactions

Two parts: **Part 1** is the design (the "think more" part). **Part 2** is the prompt for the agent. Pip lives in `laptop/`, which Codex owns.

Status: design only. I have not seen your current Pip code and have not run Electron. Electron API names below come from my knowledge and must be verified against the installed `electron` typings (rule V-G2 in the prompt).

---

## Part 1: Design

### 1.1 What is wrong today (from your description)
Pip sits fixed at the screen center and only changes animation. Needed: **(a)** the user can move it anywhere and it remembers; **(b)** when the user is distracted, Pip comes up on screen in a graded, respectful way; **(c)** Pip never becomes the distraction itself.

### 1.2 Principle: motion is attention
Movement and sound pull attention. So Pip follows three rules:
1. **Calm while working.** During focused work states (`flowing`, or a declared focus session) Pip does not move or pop up; at most a slow breathing animation at low frame rate.
2. **Ask, don't accuse.** The state inference is rule-based and **uncalibrated**, so copy is a question or offer ("Need a hand getting back?"), never "You are distracted". It never names an app, site or file.
3. **Escalate only on evidence, with budgets.** Higher levels need longer, continuous distraction evidence; every level has cooldowns, an hourly and daily cap, and a clear "not now" path that makes Pip quieter afterward.

### 1.3 Movable Pip

**Behaviors**
| Feature | Behavior |
|---|---|
| Drag | Press and drag the sprite anywhere inside the usable area (`workArea`) of any display; click-through stays on everywhere else |
| Throw | Releasing while moving gives the sprite inertia with friction; it settles and snaps to a nearby edge or corner if within a magnet radius |
| Persist | Position saved as `{displayId, relX, relY, anchor}` in settings; restored on start |
| Clamp | After display changes (unplug, resolution or scale change) the position is clamped into a visible area; if its display is gone, fall back to the bottom-right of the primary display |
| Lock | "Lock position" toggle; locked Pip can still be clicked but not dragged |
| Reset | "Reset position" in the tray and right-click menu |
| Size and opacity | Small / Medium / Large; opacity fades to about 60% after 20 s idle, restored on hover |
| Menu | Right-click menu: Snooze 15 min / 1 h / today, Break 15 min, Lock, Reset, Size, Calm mode, Open panel, Pause Pip, Quit |
| Click and double-click | Click shows quick actions; double-click opens the panel |
| Panic exits | Tray "Pause Pip" and a global shortcut (default `Ctrl+Alt+P`, configurable) hide Pip instantly |

**Window architecture: decide by measurement (milestone M0 spike)**
- **Option A, small window moved with `setBounds`:** simple; risk of jank and known issues when a window crosses displays with different scale factors; dragging needs main-process cursor polling because mouse events stop at the window edge.
- **Option B, one transparent click-through overlay per display (sized to that display's `workArea`), sprite animated with CSS transforms inside:** smooth motion, trivial edge snapping, no cross-display window moves (Pip "walks off" one overlay and enters the next at the matching edge). Needs mouse forwarding for hover detection, which Electron supports on Windows and macOS **(verify)**.
- **Recommendation:** try B first; fall back to A only if B shows a problem you can measure (CPU, frame time, click-through reliability, fullscreen interactions). Pick with numbers, not feelings.

**Click-through safety (a bad bug here makes the whole screen unclickable)**
- Default is "ignore mouse events", with hover forwarding; capture only while the pointer is over the sprite or bubble (hitbox).
- Watchdog: if the renderer stops responding for more than 2 s, the main process forces ignore-all and shows a tray warning.
- The overlay window is non-focusable and shown without activation, so it never takes keyboard focus.
- Always-on-top level chosen so it stays above normal windows but does not cover the taskbar (use `workArea`). Behavior over exclusive-fullscreen apps and games is **unknown until tested**; document it and provide a manual "Do not disturb" toggle (section 1.6).

**Drag math (pure, unit-testable):** pointer capture during drag; velocity from the last ~80 ms of pointer samples; after release `v <- v * exp(-k*dt)` (friction) until below a threshold; magnet snap to edges within `snapRadius`; clamp to the display's usable area. Slides use a critically damped spring (`x'' = -w^2 (x - target) - 2w x'`) so motion has no overshoot jitter. Reduced-motion setting disables inertia and slides (jump-cuts with fade).

### 1.4 Distraction reactions: the escalation ladder

**Distraction evidence** (inputs already exist in 7A, 7C or 8L; no new inference thresholds beyond this table):
- `browsing` with surface `shorts` or `feed`, or categories social / short_video / games / shopping / news; `gaming` state;
- remote: phone second-screen overlap (via `PetInputBus`) while laptop work was recent;
- **Required context:** work evidence in the last 15 minutes (laptop state `flowing`, `stuck` or `idle_open` in a work category). Without recent work evidence, Pip does not nag (someone relaxing on purpose is not drifting). A long leisure stretch gets at most one gentle "stretch break" check-in per hour.
- **Never** while on a declared break, in snooze, in quiet hours (default 22:00-07:00, configurable), in DND, or when `laptopState` is `offline` or stale (gaps are never evidence).
- Surface `watch` (long videos) is treated softer: thresholds x2, because tutorials are work.

Let `D` be the continuous distraction time (gaps up to 20 s ignored).

| Level | Default trigger (all assumptions) | What Pip does | Notes |
|---|---|---|---|
| L0 | working or no evidence | rests at its perch; breathing only | no motion in `flowing` |
| L1 | `D >= 60 s` | wakes, eyes glance toward the screen center | tiny, no bubble |
| L2 | `D >= 3 min` | small bubble at its current spot, question style, 6 s | 10 min cooldown |
| L3 | `D >= 8 min` | slides in from the nearest edge to the **display where the cursor is**, stays in the outer 25% band, bubble with buttons: **Back to task**, **Break 15 min**, **Not distracted** | 20 min cooldown |
| L4 | `D >= 20 min`, at most once per episode | larger "come up" card, still dismissible, auto-retreats after 12 s, offers "Start a 25-minute focus session" | never blocks the center, never captures keyboard |
| Never | | sound, flashing, shaking, covering the screen center, stealing focus, repeating the same line twice in a row | |

Recovery: after 60 s back on work, Pip plays a small celebrate animation once per episode and returns to its perch.

**Budgets and adaptation (all numbers are assumptions, tune on your data):**
- Nudge budget: at most 4 per hour and 20 per day across levels L2 to L4.
- **Not distracted** (or "that was work") suppresses that category+surface for 60 min and multiplies the next thresholds by 1.5; three dismissals in a row pause nudges for 1 hour and ask once "Want fewer nudges?".
- Feedback counters are stored locally as numbers (nudges shown / accepted / dismissed per level, per day); no text, no app names.

**Phone-driven reactions (when synced):** `pet_event held` gives a perk-up and a "1 held" badge; second-screen overlap while laptop focus is active gives a raised-eyebrow glance (L1) and a gentle L2 bubble after 2 minutes ("Phone time? I'm holding your notifications."); `released` and `celebrate` map to existing animations.

### 1.5 Bubble copy policy
Short, kind, varied; a pool of at least 8 lines per level; no shame, no names of apps, sites or people; no numbers about "wasted time"; rotate without repeats; localizable strings file; honest uncertainty ("Looks like a detour?"). Provide a reduced-text mode (icon only).

### 1.6 Respect and safety features
- **Calm mode:** no movement at all, bubbles only. Also follows the OS reduce-motion preference.
- **Do not disturb:** manual toggle; categories that suppress overlay (gaming by default); behavior over fullscreen apps is untested and must be reported honestly (**unknown**).
- **Multi-monitor:** Pip appears on the display containing the cursor for L3 and above (a proxy for where attention is, since window geometry is not available).
- **No keyboard capture, no screen reading:** Pip uses no screenshots or screen capture.
- **Performance budget:** idle CPU under 2% and no continuous rendering when hidden (pause animations and drop to 30 fps when not interacting); measure and report, do not assume.
- **Accessibility:** tray and shortcut work without a mouse; text contrast checked; bubble auto-dismiss time configurable.

### 1.7 Decisions for the owner

| ID | Decision | Recommendation |
|---|---|---|
| P-1 | Window architecture | M0 spike decides by measurement; prefer Option B |
| P-2 | Maximum level | Allow up to L4 with the budgets above |
| P-3 | Nudge budget and thresholds | Start with the defaults; tune after one week of use |
| P-4 | Which categories count as distraction | social, short_video, games, shopping, news; `watch` soft |
| P-5 | Sound | Off, no setting in v2 |
| P-6 | Quiet hours default | 22:00-07:00 |
| P-7 | Global shortcut | `Ctrl+Alt+P` unless it conflicts |

---

## Part 2: Prompt for the agent (Codex lead; paste the Preamble, the Grounding Protocol and Section L first)

Files to paste before this prompt, all from your prompt library: the coordination preamble (`DHYAN_STAGE789_PROMPTS.md`), the Grounding Protocol (Part A of `DHYAN_ANTIGRAVITY_GROUNDED_PROMPTS.md`; it works for Codex too), and Section L (`DHYAN_NEXT_PROMPTS.md`).

```text
[PASTE PREAMBLE] [PASTE GROUNDING PROTOCOL] [PASTE SECTION L]

Task PIP-V2: make Pip movable and add graded distraction reactions, following docs/prompts/DHYAN_PIP_V2_DESIGN_AND_PROMPT.md Part 1. Branch pip-v2 from main (rebase first; if s7-laptop 7B is not merged, say so and base on it). Notes NOTES_pip-v2.md.
Scope: laptop/src/pet/**, laptop/src/panel/** (pet settings and menu only), laptop/src/main/pet* and window files, laptop/src/shared settings keys for pet, laptop/MANUAL_CHECKS.md (append), tests, root script key check:pip. Not allowed: laptop adapters/pipeline semantics, relay/, phone/, core-spec/, docs/contracts/, contracts/sync/, tools/mock-*. Use the existing PetInputBus and LaptopPipeline exports as they are; request any change in the notes. No new runtime dependencies.

SPECIFIC GROUNDING RULES (in addition to the Grounding Protocol)
 P-G1 Electron API claims are [UNKNOWN] until verified. Open the installed electron typings (node_modules/electron/electron.d.ts) and paste the declaration line for EVERY API you use: BrowserWindow options (transparent, frame, focusable, skipTaskbar, alwaysOnTop level), setIgnoreMouseEvents(ignore, {forward}), setFocusable, showInactive, setAlwaysOnTop(flag, level), setBounds, screen.getAllDisplays/getDisplayNearestPoint/getCursorScreenPoint and the display-added, display-removed, display-metrics-changed events, globalShortcut, Menu.popup, powerMonitor events, webPreferences.backgroundThrottling. If a platform note says Windows/macOS only, say so. If an API is not in the typings, do not use it.
 P-G2 Behavior on this Windows machine (click-through forwarding, always-on-top over fullscreen video or games, mixed DPI, taskbar positions, sleep/wake, remote desktop) is [UNKNOWN] until the OWNER runs MANUAL_CHECKS.md. Never claim it works; build the checks and the panic exits.
 P-G3 Every threshold, cooldown, budget, radius, friction value and time is [ASSUMED] unless the design cites a source. Keep them in ONE config object (pip config), list them in the notes Assumptions table, and never tune one to make a test pass without recording why.
 P-G4 Distraction inference uses only existing outputs (LaptopPipeline state and diagnostics, feeds, remote inputs). Do not add new inference. Respect DD-13: confidence is uncalibrated; never show it as a number.
 P-G5 Pip never reads window titles, URLs, app names, file names or any text. Bubble copy never names apps, sites or people. Feedback counters store numbers only.
 P-G6 Pure logic has no electron import: DistractionAssessor, EscalationLadder, BubblePolicy, PositionStore (clamp/migrate), DragPhysics. Electron is touched only by thin adapters.

MILESTONE M0 (spike and plan, then STOP for owner approval)
1. Establish ground truth: git log, branch state, current pet window code (how it is created, sized, positioned, how click-through works today), current state machine and bubble code, PetInputBus signature. Cite file:line.
2. Verify the Electron APIs (P-G1) and report the installed electron version.
3. SPIKE both window architectures minimally: Option A (small window moved with setBounds) and Option B (one transparent click-through overlay per display with DOM-animated sprite). For each, in a throwaway branch or folder, measure: renderer CPU while idle and while dragging (use process metrics from app.getAppMetrics), frame time during a 5-second drag, whether mouse hover forwarding works over a transparent area, and whether clicks outside the sprite reach the window below. If you cannot run Electron here (the Electron binary may not be installed), say NOT RUN and give the owner the exact PowerShell commands to run; do NOT guess measurements.
4. Write the plan: chosen architecture with the evidence (or "owner must measure"), scope table with subagents (S1 pure logic: DistractionAssessor, EscalationLadder, BubblePolicy; S2 window/overlay/drag/display handling/persistence/watchdog; S3 renderer animation, bubbles, menu, panel settings; S4 tests, MANUAL_CHECKS), the interface file, the config object with every parameter and its [ASSUMED] tag, and the decisions P-1..P-7 you need.
STOP after M0.

M1 pure logic (S1): DistractionAssessor (inputs: laptop state snapshot, diagnostics health, feeds, remote inputs, clock; outputs: level target, dwell D, reasons as enums; implements the evidence rules, the work-context requirement, gaps-are-not-evidence, softer `watch`), EscalationLadder (levels L0-L4, cooldowns, hourly/daily budgets, adaptation after "Not distracted", three-dismissal pause, quiet hours, break/snooze/DND), BubblePolicy (pool per level, no immediate repeats, reduced-text mode). Tests with a fake clock: each level triggers at its threshold and not before; no motion in flowing; no nudge with offline/stale state; no nudge without recent work; budgets and cooldowns; adaptation; quiet hours; recovery celebrate once per episode; determinism with a seeded picker.
M2 movement (S2 + S3): drag, throw inertia, edge snap, clamp, persistence {displayId, relX, relY, anchor}, display add/remove/metrics-change handling, lock, reset, size, opacity fade, right-click menu, tray items, global shortcut, watchdog forcing ignore-all if the renderer hangs more than 2 s, non-focusable and showInactive. Pure tests: DragPhysics (velocity estimate, friction decay, snap choice, clamp), PositionStore (restore on missing display, resolution/scale change, corrupted settings), spring step (no overshoot). Adapter tests with a fake `screen` object.
STOP after M2 for owner review.
M3 reactions on screen: render L1-L4 (glance, bubble, slide to the cursor's display within the outer 25% band, card with buttons Back to task / Break 15 min / Not distracted / Start a 25-minute focus session at L4), reduced-motion/calm mode, auto-retreat, and no-motion-while-flowing. The buttons call existing declared-session and break paths in the main process (find them; report if missing, do not invent protocols). Phone-driven reactions use PetInputBus kinds held/released/nudge/celebrate and second-screen overlap if available; if remote inputs are not wired yet, implement with the bus interface and test with injected inputs.
M4 verification: root script check:pip (typecheck, build, unit tests, a privacy test that fails if any bubble or counter contains an app/site/file string or any text from fixtures), a performance note with the numbers you measured (or NOT RUN), and laptop/MANUAL_CHECKS.md "Pip v2" section with exact owner steps: drag across two monitors; mixed DPI 125%/150%; taskbar on each side; throw and snap; restart persistence; unplug a monitor; sleep and wake; click-through to a window under Pip; hover forwarding; fullscreen video and a game (report what happens, do not assume); the panic shortcut and tray pause; trigger L1-L4 using a dev-only "simulate distraction" menu or CLI flag; calm mode; quiet hours; snooze; and how to read the local nudge counters.
Stop points: after M0 and after M2. Report format: Grounding Protocol G13. Not verified on target: everything involving a real Windows desktop, real displays, fullscreen apps and real user behavior.
```

---

## Part 3: What you do (owner)

1. Approve P-1..P-7 (or change numbers) and save them in `docs/OWNER_DECISIONS.md`.
2. Start the agent; read the M0 spike results. If it says NOT RUN for Electron, run the PowerShell commands it gives you and paste the numbers back.
3. After M2, test dragging yourself using the new MANUAL_CHECKS section before approving M3.
4. During real use for a week, watch the local counters (nudges shown, accepted, dismissed). If dismissals dominate, raise the thresholds; do not claim the nudges "improve focus" without measured evidence (that belongs in the validation work).