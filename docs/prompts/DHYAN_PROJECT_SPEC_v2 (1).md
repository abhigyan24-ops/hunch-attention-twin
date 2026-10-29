# DHYAN (ध्यान): Attention Digital Twin

> **Tagline:** Your attention, simulated before it is spent.
> **Hackathon:** CASCADE '26 · Theme 3 · Problem 3: *The Attention Digital Twin*
> **Builder:** solo · **Build budget:** 12 working hours, 10 stages, full scope (nothing cut)
> **Stack:** Kotlin + Jetpack Compose (phone), Electron + TypeScript (laptop), Node `ws` relay on AWS (or Render/Railway fallback)
> **Pet:** "Pip", a desktop companion that is the visible face of the twin

*Dhyan* means "attention / focused awareness" in Hindi and Sanskrit.

**v2 changes:** in-memory hot path (<10 ms decisions), paired Common-Random-Number simulation, single-owner episode attribution, hour-normalized cost function, snooze-first actuation, 3-tab UI, JSONL laptop store (no native addons), Windows click-through fix, Render/Railway relay fallback, and a new **12-hour, 10-stage plan** (section 17).

---

## 0. How to use this document

- This is the **master spec**. It is meant to be pasted (whole or section by section) into ChatGPT / Gemini / Antigravity as context when generating code.
- Section numbers are stable. Prompt like: *"Implement section 11.3 (NotificationListener collector) following section 6 (event schema)."*
- Section 17 is the **stage plan**. Work stage by stage and hit each exit check before moving on.
- Anything marked **[STRETCH]** is lower priority within its stage but is still **in the plan** (section 17 schedules it).
- Anything marked **[ASSUMPTION]** is something decided without confirmation; see section 24.

---

## 1. Overview

### 1.1 What Dhyan is
A **personal digital twin of one user's attention**. It watches the user's phone **and** laptop at the same time (activity categories and counts, never content), learns how that person gets distracted and recovers, and answers:

> *"If I let this notification through right now, what is likely to happen to me, and what would have happened if I had not?"*

It then acts: **allow, delay, batch into a digest, or silently log** the notification, and explains why in one plain sentence.

### 1.2 The one-line example from the problem statement, in our system
User is coding in VS Code for 52 minutes. A "Someone liked your post" notification arrives on the phone.
Twin simulates 500 paired futures per option:

| | SHOW | SUPPRESS |
|---|---|---|
| P(distraction > 1m) | 0.62 | 0.09 |
| Expected focus lost | 24 min (80% CI 8–41) | 2 min |
| Verdict | | **Digest it.** "Likes from this app pull you away 6 of 10 times while coding; 4 hrs in, you take 8 min to resume." |

A message from "Mom" with urgent wording is **allowed**, because importance outweighs predicted cost. The twin is not a blanket blocker.

### 1.3 What makes it different
1. **Cross-device**: phone and laptop are modeled together. Only this system can see "notification on phone → laptop left VS Code → 8 min until first edit".
2. **Counterfactual by design**: paired show-vs-suppress simulation, plus logged propensities and 5% exploration so counterfactuals can be estimated honestly.
3. **Intentionality, not screen time**: a long YouTube session is not assumed to be bad. It is scored by signals (content-switch rate, surface type, engagement, other device).
4. **Visible learning**: Beta/lognormal parameters update after every episode and the UI shows the prediction changing.
5. **Privacy by construction**: categories and counts only; raw content never leaves the device.
6. **A pet** that makes the twin's state visible and lively on both screens.

---

## 2. Goals and non-goals

### Goals
- Learn the six things the problem lists: best focus time, interruption causes, content that prolongs sessions, notifications that matter, fatigue, fragmenting apps.
- Simulate "allow this notification now?" with a distribution and uncertainty.
- Provide counterfactual estimates ("what would have happened if shown").
- Act on notifications safely on a real phone (Oppo Reno 14).
- Run a live demo on the user's own devices, no projector.

### Non-goals
- No server-side ML, no cloud LLM on real user data.
- No screen capture, no keylogging, no reading of message text for any purpose except local importance classification.
- No iOS, no macOS/Linux support.
- Not a general parental-control or app-blocker.

---

## 3. Requirement traceability (problem statement → feature)

| Problem requirement | Where it is met |
|---|---|
| Learns **when the user focuses best** | Insights: focus-hour heatmap from episodes (section 11.6, P10) |
| Learns **what causes interruptions** | Interruption-trigger ranking per app/sender/category (P10); self-initiated vs notification-triggered split |
| Learns **content that causes prolonged sessions** | Dwell lognormal per (category, surface); intentional-vs-compulsive score (section 8.6) |
| Learns **which notifications matter** | Importance Beta per sender/app, updated by feedback and quick opens (8.5) |
| Learns **when the user is fatigued** | Fatigue index and band (8.3); fatigue curve in Insights |
| Learns **which apps fragment concentration** | Switch-rate and short-session stats per app (P10) |
| Simulates "if I allow this notification now…" | Paired Monte Carlo SHOW vs SUPPRESS (8.4, 8.7) |
| Example chain: interruption → app → browsing → resumption delay | State machine with explicit stages (8.2); chain shown on timeline UI |
| **Counterfactual** predictions | Model-based counterfactual + logged propensity + exploration (section 10) |
| Suppress the interruption | Actuator: delay / digest / silent log (11.5) |

---

## 4. Decision log

| # | Decision | Reason |
|---|---|---|
| D1 | Native Kotlin + Jetpack Compose | Notification listener, usage stats and accessibility APIs are far easier natively |
| D2 | **One twin engine, on the phone**, pure Kotlin | Decisions must be instant and offline; avoids writing the model twice |
| D3 | Laptop = sensor + pet + rule-based state inference | Saves hours; the pet only needs the focus state |
| D4 | Both devices stream **categorized events** continuously; phone fuses them | Cross-device episodes need laptop events, not just a heartbeat |
| D5 | Everything is an event with one schema (real, laptop, synthetic) | Persona demo mode and testing come almost free |
| D6 | Laptop collection: **ActivityWatch as backbone, behind an adapter interface** | Starts logging real data in minutes; replaceable later (see 12.2) |
| D7 | Sync via **Node `ws` relay on AWS EC2** (with Render/Railway 1-click fallback) | Full control; costs a few dollars of credits (section 14.4) |
| D8 | Distributions: Beta (probabilities), lognormal (durations), Paired Monte Carlo (500 runs) | Simple, explainable, updates in O(1) with counts |
| D9 | Hierarchical fallback (bucket → parent → prior) | Works with hours of real data instead of weeks |
| D10 | Explanations from templates, no runtime LLM | Fast, private, deterministic |
| D11 | Snooze + Digest delivery for suppressed notifications, VIPs always allowed | Safety, compatibility with MessagingStyle apps, and user trust |
| D12 | E2E encryption on relay is **[STRETCH]** (scheduled in Stage 8) | TLS is the baseline; payloads are already coarse |
| D13 | Laptop (Windows) notifications are **[STRETCH]** (scheduled in Stage 9) | Fiddly; low value for MVP |
| D14 | LLMs are development-time tools (dataset, personas, code assist) | Privacy story stays clean |
| D15 | In-memory `ParamStore` & `ContextSnapshot` on phone | Guarantees `<10 ms` decision latency on `onNotificationPosted` without SQLite disk I/O blocking the binder thread |
| D16 | JSONL + in-memory ring buffer on laptop (no `better-sqlite3`) | Avoids native C++ `node-gyp` / `electron-rebuild` compilation failures on Windows |
| D17 | 12-hour plan in 10 dependency-ordered stages with gate checks | Full scope fits only if the core path is sequential and boilerplate goes to AI assistants |

---

## 5. System architecture

### 5.1 Diagram

```
┌──────────────── LAPTOP (Windows, Electron + TS) ────────────────┐
│ ActivityWatch (window, afk, web)  ·  VS Code ext · Browser ext  │
│   └─► AW Adapter (poll :5600) ─► Normalizer (categories only)   │
│        ─► JSONL Store ─► State Inferrer ─► Pet UI + Panel       │
└───────────────────────────┬─────────────────────────────────────┘
                            │ wss (categorized events + state)
                      ┌─────▼──────┐
                      │   RELAY    │  AWS EC2 · Caddy TLS · Node ws
                      │ in-memory  │  rooms by hashed pairing code
                      └─────┬──────┘
                            │ wss
┌───────────────────────────▼──────── PHONE (Kotlin + Compose) ───┐
│ Collectors: Notification · UsageStats · Screen/Unlock ·         │
│             Accessibility (scroll counts, surface)              │
│ Ingest → Room DB + In-Memory StateFlow → Fusion → Inference     │
│                         │                                       │
│              ┌──────────▼───────────┐                           │
│              │     TWIN ENGINE      │  pure Kotlin (in-memory)  │
│              │ params · episodes ·  │                           │
│              │ simulator · counter- │                           │
│              │ factual · decision   │                           │
│              └──────────┬───────────┘                           │
│ Actuator (allow/delay/digest/silent) ─► UI ◄── Feedback         │
└─────────────────────────────────────────────────────────────────┘
DEV: Persona generator → same event schema → same pipeline
DEV: Replay tool → any recorded/persona day at 100x for tests and demo
```

### 5.2 Principles
1. **Event-sourced.** Raw events are the source of truth; episodes, params and insights can be rebuilt by replay.
2. **Local-first & In-Memory Hot Path.** Each device works alone; active parameters and context snapshots live in memory for instant (<10 ms) decisions and flush asynchronously to Room.
3. **Gaps are never "focus".** Missing data is marked `gap` and lowers confidence.
4. **Categories not content.** The normalizer is the privacy boundary.
5. **Explainable.** Every decision stores its top contributing factors.

### 5.3 Repository layout

```
dhyan/
├── core-spec/                # event schema JSON, category map, state defs, default params
├── phone/                    # Android Studio project
│   └── app/src/main/java/.../
│       ├── collectors/       # NotifCollector, UsageCollector, ScreenReceiver, A11yCollector
│       ├── ingest/           # Normalizer, AppCategorizer, LabelStore, ClockOffset
│       ├── fusion/           # ContextFuser (phone + laptop → in-memory StateFlow<ContextSnapshot>)
│       ├── inference/        # FocusStateInferrer, CompulsiveScore, FatigueIndex
│       ├── twin/             # (pure Kotlin, no android.* imports)
│       │   ├── params/       # BetaStat, LogNormalStat, InMemoryParamStore, Hierarchy
│       │   ├── episodes/     # EpisodeBuilder, OutcomeWindow, EpisodeCloser
│       │   ├── simulator/    # SemiMarkovSim, PairedMonteCarlo
│       │   ├── counterfactual/ # CounterfactualEngine, ExplorationPolicy, Regret
│       │   └── decision/     # Importance, CostFunction, ActionSelector, Explainer
│       ├── safety/           # NeverHoldRules, KillSwitch, Undo
│       ├── actuator/         # Holder, DigestBuilder, ReleaseScheduler
│       ├── personas/         # PersonaDef, HistoryGenerator, FastForward
│       ├── sync/             # RelayClient, OutboundQueue, Backfill
│       ├── data/             # Room entities + DAOs (async write-through)
│       ├── metrics/          # Calibration, ParamRecovery, SavedMinutes
│       └── ui/               # SimulateLabTab, LiveDigestTab, InsightsTab, PersonaSettingsSheet
├── laptop/                   # Electron + TS (electron-vite template)
│   └── src/
│       ├── main/             # app lifecycle, pet window, tray, jsonlStore.ts
│       ├── adapters/         # AwAdapter, ActiveWinFallback, VsCodeFeed, BrowserFeed
│       ├── normalizer/       # categories.json, normalize.ts
│       ├── state/            # focusState.ts
│       ├── sync/             # relayClient.ts
│       ├── pet/              # petStateMachine.ts, sprites/, bubbles.ts
│       └── panel/            # React/HTML panel: today, held items, declare session
├── relay/                    # server.js, Caddyfile, deploy.sh
├── vscode-ext/               # Stage 9
├── browser-ext/              # Stage 9
└── tools/                    # persona dataset generator (LLM), schema validator, replay CLI
```

---

## 6. Event schema and taxonomy

### 6.1 Envelope

```json
{
  "id": "uuid-v4",
  "ts": 1730000000000,
  "device": "phone | laptop",
  "seq": 1042,
  "source": "notif | usage | screen | a11y | window | idle | vscode | browser | persona | system",
  "type": "notif_posted",
  "payload": {},
  "synthetic": false
}
```

- `ts` is epoch ms on the device clock. The receiver stores `ts_adj = ts + clockOffset(device)`.
- `seq` is a per-device monotonic counter used by sync backfill (section 14).

### 6.2 Event types and payloads

| type | source | payload (no raw text/URLs/titles) |
|---|---|---|
| `notif_posted` | notif | `{key, pkg, appCat, notifCat, senderId(hash), isVip, urgencyScore, hasReplyAction, repeatCount5m, isMessagingStyle}` |
| `notif_removed` | notif | `{key, reason: clicked/dismissed/held/snoozed/auto}` |
| `app_foreground` | usage | `{pkg, appCat, surface?}` |
| `app_background` | usage | `{pkg, durationMs}` |
| `screen_on / screen_off / unlock` | screen | `{}` |
| `pickup` | screen | `{triggeredByNotifKey?}` |
| `scroll_burst` | a11y | `{pkg, count, windowSec, surface}` |
| `surface_change` | a11y | `{pkg, surface: feed/shorts/watch/chat/other}` |
| `window_change` | window | `{app, appCat, minutesInPrev}` |
| `idle_start / idle_end` | idle | `{idleSec}` |
| `edit_burst` | vscode | `{editsPerMin, saves, errors, warnings}` |
| `resume_first_edit` | vscode | `{delayMs}` |
| `tab_category` | browser | `{domainCat, surface, videoState?, scrollCount?}` |
| `declared_session` | system | `{task, plannedMin, strict}` |
| `feedback` | system | `{refKey, label: important/noise/useful/waste}` |
| `gap` | system | `{deviceFrom, deviceTo, reason}` |
| `state_snapshot` | system | ContextSnapshot (section 6.4) |

### 6.3 Category taxonomy (starter `categories.json`)
`coding, docs, communication, social, video, short_video, games, shopping, news, music, learning, utility, system, unknown`
- Apps map by package (phone) or process name (laptop); domains by a small built-in table.
- Unknown apps get a one-time prompt: "What is this?" The answer is stored in `app_labels` (label once, remembered).
- Surfaces: `feed, shorts, watch, chat, editor, other`.

### 6.4 ContextSnapshot

```json
{
  "ts": 1730000000000,
  "focusState": "deep_focus|working|drifting|distracted|recovering|break",
  "taskType": "coding|studying|writing|gaming|browsing|unknown",
  "taskSource": "declared|inferred",
  "confidence": 0.82,
  "hourBand": "evening",
  "minutesInState": 52,
  "fatigueBand": "med",
  "recentInterrupts30m": 3,
  "laptopState": "flowing|stuck|idle_open|browsing|gaming|away|offline",
  "phoneState": "screen_off|in_app|scrolling",
  "secondScreenOverlap": false,
  "declaredSession": null
}
```

### 6.5 Privacy boundary (the normalizer)
| Data | Handling |
|---|---|
| Notification title/text | Used **in memory** for urgency and importance scoring; stored only in `held_notifs` (app-private) while held; never synced |
| Window titles / URLs | In memory for classification only, then discarded; never stored, never synced |
| Keystrokes | Counts only, never content |
| Sender | Stored as a salted hash |
| Synced to relay | Categorized events and focus state only |

---

## 7. Pipelines

| ID | Name | Trigger | Output |
|---|---|---|---|
| P1 | Collect + normalize | Every raw signal | Schema events in local DB + in-memory bus |
| P2 | Stream | Every event, batched ≤2 s | Events on relay, queue + backfill |
| P3 | Fuse + infer | Every 10–30 s and on key events | In-memory `StateFlow<ContextSnapshot>` |
| P4 | Episode building | Notification, pickup/app-open, window-leave | Open and closed episodes |
| P5 | Learning | Episode closes | Updated in-memory Beta/lognormal params + async Room write |
| P6 | Decision | `onNotificationPosted` | Action + explanation (<10 ms from memory) |
| P7 | Actuation | Decision | Snooze / hold / digest / release |
| P8 | Feedback | User taps, quick opens | Labels → P5 |
| P9 | Counterfactual accounting | Decision + outcome | Regret, saved minutes, propensities |
| P10 | Insights | Dashboard open | Aggregates |
| P11 | Persona/replay | Demo or test | Synthetic or replayed events through P1–P10 |
| P12 | Mirror | State change | Pet state on laptop, "held" badge |

### 7.1 Episode definition and attribution rule
An **episode** starts at a trigger (notification arrival, or a self-initiated pickup/app-open with no notification) and runs a **30-minute outcome window** (`W = 30 min`, configurable).

```
trigger → frozen ContextSnapshot
        → outcomes within window:
            pickedUp? (t)             first app opened + category + surface
            dwellMs                   time in distracting app(s)
            laptopLeftWork?           window_change away from work category
            resumeDelayMs             time from return to first meaningful edit / activity
            feedback?                 important / noise
        → label: notif_triggered | self_initiated | superseded | none
```

**Single-Owner Attribution Rule (prevents double-counting):**
1. At most **one** unresolved `notif_triggered` episode is active per device at any moment.
2. If notification B arrives while notification A's outcome window is still open and the user has **not** yet picked up the phone:
   - Episode A closes immediately at `t_B` with `pickedUp = false` (counted as an ignored notification) and `label = superseded`.
   - Episode B becomes the active trigger for any subsequent pickup.
3. Once a user picks up the phone (`IN_APP`), any notifications arriving *during* that active distraction session do not start new episodes; they are logged as in-session arrivals.
4. Self-initiated episodes (pickups when no notification occurred in the prior 2 minutes) establish the **baseline hazard** `λ_self(x)` that suppression cannot remove.

### 7.2 Outcome measurement by device
- Phone: `app_foreground` after trigger, `app_background.durationMs`.
- Laptop: `window_change` from work to non-work; `resume_first_edit` (VS Code ext) or "first non-idle input in a work app" as the proxy.
- Without a laptop: dwell only, with `resumeDelay` drawn from the prior and flagged `estimated`.

---

## 8. Twin model specification

### 8.1 Notation
`x` = context bucket key, `n` = notification class, `W` = outcome window (30 min), `N` = Monte Carlo runs (500).

### 8.2 States (semi-Markov)

```
FOCUSED ─(notif shown)→ NOTIFIED ─ignore→ FOCUSED (still subject to baseline λ_self)
                           │
                         open/pickup
                           ▼
                         IN_APP(cat, surface)  ── dwell D ──►  RESUMING ── delay R ──► FOCUSED
```
Suppression removes `NOTIFIED`. The user may still self-initiate: `FOCUSED → IN_APP` at baseline rate `λ_self(x)`.

### 8.3 Context buckets
`x = (taskType, hourBand, fatigueBand, notifClass)` with
- `hourBand`: night (0–6), morning (6–12), afternoon (12–17), evening (17–22), late (22–24)
- `fatigueBand`: low/med/high from **fatigue index**
  `f = clip(0.5·(hoursWorked/6) + 0.3·switchRateZ + 0.2·stallRatio, 0, 1)`; low <0.33, med <0.66, else high
- `notifClass`: `{appCat, isVip, urgencyBand, repeatBand}`

**Hierarchy (backoff):** `x_full → (taskType, notifClass) → (notifClass) → population prior`.

### 8.4 Parameters and their estimators

**Beta (probabilities)** for `p_pickup(x)`, `p_ignore`, `p_important(sender|app)`:
- State `(α, β)`, init from prior pseudo-counts (default α=β=1 or population-informed).
- Update: success → `α += 1`, failure → `β += 1`. Optional recency decay: multiply both by `γ=0.995` per episode.
- Hierarchical shrinkage with parent mean `μ_p` and strength `κ=5`:
  `p̂(x) = (α_x + κ·μ_p) / (α_x + β_x + κ)`
  and for sampling use Beta(`α_x + κ·μ_p`, `β_x + κ(1−μ_p)`).

**Categorical** `P(cat, surface | pickup, x)` via Dirichlet counts (same shrinkage).

**Lognormal (durations)** for dwell `D(cat, surface, x)` and resume delay `R(taskType, stuck?, dwellBand)`:
- Online estimate of `m = mean(ln d)` and `s² = var(ln d)` with Welford's algorithm.
- Prior pseudo-observations `n0 = 3` with `(m0, s0)` from the population prior; posterior mean `m = (n0·m0 + Σ ln d)/(n0+n)`.
- Sample: `d = exp(m + s·Z)`, `Z~N(0,1)`, with `s` floored at 0.25.

**Baseline hazard** `λ_self(x)`: self-initiated pickups per hour in bucket `x`; Gamma(α,β) posterior (events, exposure hours). `p_self(W) = 1 − exp(−λ·W/60)`.

### 8.5 Importance model
`I = 1 − (1−I_sender)(1−I_urgency)(1−I_repeat)` (noisy-OR), all in [0,1]:
- `I_sender`: posterior mean of Beta per sender-hash (fallback per app, then prior). VIP → 1.0.
- `I_urgency`: from category (call/alarm/OTP/transport/delivery = high), reply action present, urgency keywords (local list, EN + Hinglish).
- `I_repeat`: `0.3` after 2 messages from the same sender in 5 min, `0.6` after 3+.
Labels update the Beta: opened within 2 min or tapped "important" = success; ignored in digest or "noise" = failure.

### 8.6 Intentional vs compulsive score
Logistic over signals, hand-initialized weights, online SGD from feedback:

```
s = b0 + w1·switchRate + w2·scrollRate + w3·isShortsOrFeed
       − w4·engagement − w5·declaredStudy − w6·otherDeviceWorkActivity
       + w7·secondScreenOverlap + w8·lateHour
P(compulsive) = σ(s)
```
- `switchRate`: content/surface changes per minute. `engagement`: pauses, seeks, rate changes, typing in a notes app.
- Update: `w ← w + η(y − p)x − ηλ(w − w_prior)`, with η≈0.05, λ≈0.01.
- Used for: Insights ("sessions that cause long drift"), the dwell bucket, and pet nudges.
- Output actions: `< 0.35` silent, `0.35–0.65` ask once ("Study or break?") and remember, `> 0.65` gentle nudge.

### 8.7 Monte Carlo simulation (Paired Common Random Numbers)
For run `i = 1 … N` (`N = 500`), simulate **both** `SHOW` and `SUPPRESS` using shared random draws for the baseline self-interruption hazard, so variance cancels and `ΔL` is stable even when `λ_self` is high:

```
For i = 1..N:
  # 1. Sample epistemic parameters from posteriors
  p_i    ~ Beta(posterior of p_pickup(x))
  λ_i    ~ Gamma(posterior of λ_self(x))
  p_self = 1 − exp(−λ_i · W / 60.0)

  # 2. Pre-draw shared baseline self-distraction outcome (Common Random Numbers)
  self_i ~ Bernoulli(p_self)
  if self_i:
     (cat_s, surf_s) ~ Categorical(Dirichlet sample for baseline x)
     D_self ~ LogNormal(m_D(cat_s, surf_s), s_D(cat_s, surf_s))
     R_self ~ LogNormal(m_R(taskType), s_R(taskType))
     L_base = D_self + R_self
  else:
     L_base = 0.0

  # 3. Evaluate SUPPRESS arm
  L_suppress[i] = L_base

  # 4. Evaluate SHOW arm
  picked_i ~ Bernoulli(p_i)
  if picked_i:
     (cat_n, surf_n) ~ Categorical(Dirichlet sample for notif x)
     D_notif ~ LogNormal(m_D(cat_n, surf_n), s_D(cat_n, surf_n))
     R_notif ~ LogNormal(m_R(taskType), s_R(taskType))
     L_show[i] = c_glance + D_notif + R_notif
  else:
     # User glanced and ignored the notification, but still faces baseline hazard
     L_show[i] = c_glance + L_base

  # 5. Paired counterfactual difference for run i
  ΔL[i] = L_show[i] − L_suppress[i]
```

Output per option: mean, p10, p50, p90, `P(L > 1.0 min)`, and the credible interval of `p_pickup`.
**Expected Saved Focus:** `ΔL = mean(ΔL[1…N])` with 80% CI (`p10(ΔL)` to `p90(ΔL)`).
**Confidence:** `conf = n_eff / (n_eff + 10)`, reported to the user (e.g., *"low confidence: 9 observations"*).
**Note:** `ΔL` for an individual run can be negative (the user would have self-distracted anyway); only the mean is expected to be positive for notification-prone users and near zero for self-distraction-dominated users (Kabir).

### 8.8 Fatigue and "when you focus best"
- Focus quality per hour = mean uninterrupted work minutes, `1 − distractedFraction`, and (inverse) resume delay, aggregated by hour-of-day and day-of-week with Beta/CI.
- Fatigue curve = focus quality vs hours worked, per user.

### 8.9 Default population priors (used before any data)
| Parameter | Prior |
|---|---|
| p_pickup after social notif while working | Beta(3,4) (≈0.43) |
| p_pickup after message from known contact | Beta(4,3) |
| p_pickup after promo | Beta(1,6) |
| Dwell, social | LogNormal(m=ln 9, s=0.8) |
| Dwell, short_video | LogNormal(m=ln 14, s=0.9) |
| Dwell, messaging | LogNormal(m=ln 3, s=0.6) |
| Resume delay, coding | LogNormal(m=ln 6, s=0.6) |
| Resume delay, studying | LogNormal(m=ln 4, s=0.6) |
| Fatigue multiplier on pickup odds | ×1.0 / ×1.2 / ×1.5 (low/med/high) |

These are tunable; they only matter until real data accumulates.

---

## 9. Decision engine and safety

### 9.1 Flow (In-Memory Hot Path, <10 ms)
All state reads inside `onNotificationPosted` hit the in-memory `InMemoryParamStore` and `StateFlow<ContextSnapshot>`, **never** blocking on SQLite disk I/O. Run one dummy simulation at app start so the JVM is warmed up before the first real notification.

```
onNotificationPosted(sbn):
  1. SafetyRules.neverHold(sbn)? → ALLOW, asyncLog, stop
  2. snapshot = Fusion.currentSnapshotFlow.value      # O(1) memory read
  3. I = Importance.scoreInMemory(sbn)                # O(1) memory read
  4. if I ≥ 0.85 → ALLOW, asyncLog, stop
  5. sim = Simulator.run(snapshot, sbn)               # Paired SHOW vs SUPPRESS, N=500 (<5 ms warm)
  6. J(a) for each action (9.2)
  7. a* = argmin J; apply exploration policy (9.4)
  8. Explainer.build(a*, top factors)
  9. Actuator.apply(a*); CoroutineScope(IO).launch { logDecision(...) }
```

### 9.2 Cost function (units: focus-minutes)
Let `E[L_show]` and `E[L_suppress]` be the expected focus-minutes lost under each arm, `τ_a` the expected hold time in **minutes**, and `κ_d` the delay-harm coefficient (user "caution" slider, default `3.0` focus-min per hour of hold at `I = 1`).

Hold duration is normalized into hours (`τ_a / 60`) so all terms are in comparable focus-minute units:

```
J(ALLOW)  = E[L_show]

J(DELAY)  = E[L_suppress] + κ_d · I² · (τ_release / 60.0) + c_rel · E[L_show]

J(DIGEST) = E[L_suppress] + κ_d · I² · (τ_digest  / 60.0) + c_digest

J(SILENT) = E[L_suppress] + κ_d · I² · (τ_digest  / 60.0) + λ_miss · I
```

**Parameter semantics and defaults:**
- `τ_release`: predicted minutes until the next low-cost break/transition (typically 15–45 min).
- `τ_digest`: minutes until the next scheduled digest batch (default 60–120 min).
- `c_rel = 0.25`: release discount factor (releasing at a natural break costs about 25% of an in-flow interruption).
- `c_digest = 0.8 min`: fixed cognitive overhead of reviewing this item inside the digest batch later.
- `λ_miss = 5.0 min`: penalty for silently logging an item without alerting the user in the digest.

**Decision boundaries created by these equations:**
- **`SILENT` vs `DIGEST`:** `J(SILENT) < J(DIGEST)` iff `λ_miss · I < c_digest`, i.e. `5.0 · I < 0.8`, i.e. `I < 0.16`. Pure promos/spam (`I < 0.16`) are silently logged; notifications with `I ≥ 0.16` go to `DIGEST` or `DELAY`.
- **`DIGEST` vs `DELAY`:** when `I` is moderate (`0.35 ≤ I < 0.75`) and a natural break is near (`τ_release ≪ τ_digest`), `DELAY` beats `DIGEST` because the quadratic importance penalty accrues over a shorter wait.
- **`ALLOW` override:** when `I` is high or `ΔL` is small, the importance delay penalty exceeds `ΔL` and `ALLOW` wins automatically (plus the hard safety override at `I ≥ 0.85`).
- **Risk-aversion:** when `conf < 0.4` and `I > 0.5`, prefer `ALLOW`.
- **Declared strict session** ("deep work"): multiply `κ_d` by `0.5` (more willing to hold) except for VIP/never-hold rules.

### 9.3 Explanation generator
Top-2 factors by contribution to `J(ALLOW) − J(a*)`:
- predicted loss ("24 min lost, 80% CI 8–41")
- pickup pattern ("you open this app 6 of 10 times while coding")
- fatigue ("4 hrs in, resume delay is 8 min")
- laptop state ("deep in VS Code for 52 min")
- importance ("low-importance sender")
Template: `Held: {factor1}; {factor2}. Tap to show now.`

### 9.4 Exploration and propensities
For non-VIP, non-never-hold notifications with `I < 0.4` and `ΔL > 0`: with probability `ε = 0.05` choose the *other* arm (show instead of hold). Always log `π(a | context)` (the actual probability of the chosen action). This enables unbiased counterfactual estimation (section 10).
Exploration is **off** during strict sessions and can be disabled in Settings.

### 9.5 Safety submodule (separate and demoable)
- Never-hold: calls, alarms, OTP/transaction codes, VIP contacts, emergency keywords, navigation, and any app on the user's allow-list.
- **Undo / Show now** on every held item; digest is always accessible.
- Global **kill switch** (Settings tile and notification action).
- Missing/gap data is never treated as focus; with no context, default ALLOW.
- Every hold logged with a reason; items expire (OTPs older than 10 min removed from digest).
- If the listener is disconnected, nothing is held (fail-open).

### 9.6 Release scheduling
Release moments: idle ≥ 2 min, commit detected, declared session end, app-switch to a communication app, natural scheduled digest (e.g. :00 of every 2 hours), or any time `E[L_show]` predicted < threshold (default 3 min). Pet whisper on laptop: "2 held (1 family)".

---

## 10. Counterfactuals and validation

### 10.1 Three levels of counterfactual honesty
1. **Model-based:** same snapshot, force SHOW vs SUPPRESS in simulation. Fast, interpretable, depends on model assumptions.
2. **Logged propensities:** every decision stores `π`. Observed outcomes for shown items give real outcomes; suppressed items have only predicted ones.
3. **Exploration (ε=5%):** randomized arms give unbiased data on low-importance items.

### 10.2 Estimators
With exploration logs `(x_j, a_j, Y_j, π_j)` (Y = realized focus loss):
- **IPW:** `Ŷ_show(x) = mean_j [ 1(a_j=SHOW)·Y_j / π_j ]` within bucket; likewise for SUPPRESS.
- **Doubly robust:** `Ŷ_DR = m̂(x) + 1(a=SHOW)(Y − m̂(x))/π`, where `m̂` is the twin's own predicted loss. This is valid if either the propensity or the model is right.
- Report `ΔL̂ = Ŷ_show − Ŷ_suppress` with bootstrap intervals once ≥ 20 exploratory episodes exist for a bucket; otherwise show model-based only and say so.

### 10.3 Assumptions stated openly (pitch slide)
1. No big hidden confounders beyond the context features we log.
2. Behavior within a bucket is roughly stationary over days (with recency decay to hedge).
3. Suppression does not change future behavior (users may later compensate by checking the phone more); tracked via self-initiated rate after suppression.

### 10.4 Validation on synthetic personas (the only place true counterfactuals are known)
| Test | Method | Metric |
|---|---|---|
| Parameter recovery | Train on days 1–10 of a persona | Error between learned and hidden `p_pickup`, `m_D`, `m_R` (target: median abs error < 0.08 prob, < 25% duration) |
| Predictive accuracy | Hold out day 11 (no future leakage) | MAE of dwell, log-loss on pickup |
| Calibration | Predicted P(pickup) vs observed | Reliability diagram, Brier score, ECE < 0.08 |
| Counterfactual accuracy | Persona generator knows true outcome under both arms | Error of `ΔL̂` vs truth |
| Utility | Policy replay | Minutes saved vs urgent items delayed (target: ≥ 40% saved, 0 VIP/urgent held) |
| Safety | Replay with injected urgent messages | Held-urgent count = 0 |

### 10.5 Temporal validation rule
The replay tool trains with `ts < t0` only; features may never read events with `ts ≥ t0`. A unit test enforces this by failing if any query touches future events.

---

## 11. Phone app specification

### 11.1 Platform
- Kotlin, Jetpack Compose, Material 3, min SDK 26 (target latest).
- Room, WorkManager, Kotlin Coroutines/Flow, OkHttp (WebSocket), Koin or Hilt.
- Test device: **Oppo Reno 14 (ColorOS)**. Emulator (Pixel) for UI and engine tests only.

### 11.2 Permissions and setup flow
| Permission | Needed for | How the user grants it |
|---|---|---|
| Notification access | Listener | Settings → Notification access → enable Dhyan |
| Usage access | App foreground/duration | Settings → Usage access |
| Accessibility service | Scroll counts, surface | Settings → Accessibility → Dhyan (explain clearly: counts only) |
| POST_NOTIFICATIONS | Our digest and foreground notification | Runtime prompt |
| Foreground service | Keep collectors alive | Declared type: `specialUse` or `dataSync` per target SDK |
| Contacts (read, optional) | VIP from favorites | Runtime prompt; store only hashed IDs |
| Ignore battery optimization | Survive ColorOS | Intent to request exemption |

An onboarding screen checks each permission and deep-links to its settings page. If toggles are greyed out for a sideloaded build, open App info → ⋮ → **Allow restricted settings**.

### 11.3 Collectors

**NotifCollector** (`NotificationListenerService`)
- `onNotificationPosted`: extract `pkg`, `category`, `isOngoing`, `hasReplyAction`, `isMessagingStyle`, `people` extras if present, hashed sender; keep title/text in memory for scoring.
- `onListenerDisconnected`: call `requestRebind(componentName)`; log a `gap`.
- Heartbeat watchdog (WorkManager, ~15 min): detects a stale listener and prompts the user via notification.

**UsageCollector**
- `UsageStatsManager.queryEvents` every 15–30 s plus on screen-on: derive foreground/background transitions and durations per package.

**ScreenReceiver** (registered dynamically in the foreground service)
- `ACTION_SCREEN_ON/OFF`, `ACTION_USER_PRESENT` → `screen_on`, `unlock`, `pickup` events.

**A11yCollector** (`AccessibilityService`)
- Subscribe to `TYPE_VIEW_SCROLLED` and `TYPE_WINDOW_STATE_CHANGED` only. Never call text getters.
- Emit `scroll_burst {count, windowSec}` every 15 s while scrolling.
- Surface detection from class name / view-ID resource name against a **JSON config** (`surface_map.json`) discovered with Layout Inspector; treat as one weak signal and keep it updatable.
- Configure `canRetrieveWindowContent=false` where possible; restrict `packageNames` to the distracting apps list.

### 11.4 Ingest & In-Memory State Holder
- `Normalizer`: raw → schema; drops text. `AppCategorizer`: package → category via bundled map + `app_labels`.
- `InMemoryParamStore`: loads `params`, `vip`, and `app_labels` from Room once at startup into concurrent maps; updates in place on P5 Learning and flushes asynchronously to Room on a single-thread IO dispatcher.
- `ClockOffset`: stores offset to laptop clock from relay ping/pong (14.3).

### 11.5 Actuator (Snooze-First Strategy for Hardware Compatibility)
Android apps using `NotificationCompat.MessagingStyle` (WhatsApp, Telegram, Messages) often re-post summary notifications or chime again if `cancelNotification(key)` is called on a child notification. We therefore use a hybrid strategy:
- **A. Snooze (`snoozeNotification(key, durationMs)`, primary for MessagingStyle and DELAY):** preserves the exact original PendingIntent, deep link and inline actions, and avoids the re-post/chime problem. Used for `DELAY` (snoozed until `τ_release`) and for `DIGEST` on `MessagingStyle` apps (snoozed until `τ_digest`, while still showing a summary card inside Dhyan's in-app Digest list).
- **B. Cancel + Digest (`cancelNotification(key)`, social / content / promo apps):** used for Instagram, YouTube, Gmail, shopping, and our own demo test notifications. Stores title/text in app-private `held_notifs` and reposts at digest time as a grouped "Dhyan Digest" notification with `getLaunchIntentForPackage`.
- **Snooze caveat:** a snoozed notification cannot be un-snoozed early through the listener API. "Show now" / Undo for a snoozed item therefore re-posts a Dhyan notification (or launches the app) immediately; the original reappears when its timer ends and Dhyan clears the duplicate when it detects it. Verify this on the Reno 14.
- `DigestBuilder`: groups held and snoozed items by sender/app, shows count, importance ordering, and "Show now / Mark important / Noise" actions.
- `ReleaseScheduler`: evaluates release moments (9.6) via WorkManager + live context.
- **Undo**: any held item can be re-posted instantly (cancel+digest items) or surfaced via re-post/launch (snoozed items).

### 11.6 Consolidated 3-Tab + Bottom-Sheet UI (Solo-Builder Scoped)
To guarantee completion within Stage 6, the 7 logical views are consolidated into **3 bottom-navigation tabs** plus **1 modal sheet**:

| Tab / Sheet | Purpose | Key elements |
|---|---|---|
| **Tab 1: Simulate & Lab** (Hero) | Pick/enter a notification + context, see two futures & live Bayesian curves | Context chips, SHOW vs SUPPRESS timelines with p10–p90 bands, verdict card, "why" sentence, expandable **Twin Lab** drawer showing Beta/Lognormal curves tightening before/after training |
| **Tab 2: Live & Digest** | Current cross-device state + held items | Focus state ring, laptop state badge, recent decisions with explanations, and the interactive **Digest list** right below with Show-now / Important / Noise buttons |
| **Tab 3: Insights** | The six learned things + counterfactual validation | Focus-hour heatmap, interruption triggers, fatigue curve, fragmenting apps, intentional-vs-compulsive breakdown, and saved minutes / ECE calibration card |
| **Top-Bar Sheet: Personas & Settings** | Demo controls + safety controls | Persona picker (Rahul/Anika/Kabir), "Fast-forward 14 days" button, VIP/never-hold list, caution slider (`κ_d`), exploration toggle, kill switch, pairing code |

### 11.7 Room schema (sketch)

```
events(id PK, ts, tsAdj, device, seq, source, type, payloadJson, synthetic)
episodes(id PK, triggerType, triggerKey, startTs, endTs, snapshotJson, outcomeJson, label, closed)
params(key PK, kind[beta|lognorm|dirichlet|gamma], a, b, n, mean, m2, updatedTs)
app_labels(pkg PK, category, userSet)
held_notifs(key PK, pkg, titleEnc?, textEnc?, senderHash, importance, mechanism[snooze|cancel], heldTs, releaseTs?, state)
decisions(id PK, ts, notifKey, action, pi, predictedJson, factorsJson, snapshotJson, exploration)
feedback(id PK, ts, refKey, label, source)
vip(senderHash PK, name?, source[contacts|manual])
sync_cursor(device PK, lastSeqAcked, offsetMs)
settings(key PK, value)
```

---

## 12. Laptop app specification

### 12.1 Platform (Zero Native C++ Addons)
- Windows 10/11, Electron + TypeScript (`electron-vite` template), `ws`, React (panel) or plain HTML.
- **Storage:** in-memory ring buffer flushed to an append-only `events.jsonl` file (`fs.promises.appendFile`). **Do not use `better-sqlite3`**: this eliminates `node-gyp` and Visual Studio C++ build tool failures on Windows.

### 12.2 Collection backbone: ActivityWatch (decision D6)
**Decision:** use **ActivityWatch** (open source, credit it in README/slides) as the collection backbone, behind an `Adapter` interface:

```ts
interface LaptopAdapter {
  start(): void;
  onRaw(cb: (e: RawLaptopEvent) => void): void;
}
```
- `AwAdapter` polls `http://localhost:5600/api/0/` buckets: `aw-watcher-window_<host>`, `aw-watcher-afk_<host>`, `aw-watcher-web-*` (if installed), and optionally `aw-watcher-vscode`.
- Reasons: logging of real data can start in ~10 minutes with zero code (the data clock matters), window/afk/web watchers exist and are maintained, and it removes a day of collector work.
- Limits: titles/URLs are stored by AW locally (our normalizer must never forward them), no scroll/video signals, no fine VS Code edit-rate.
- **Fallback / replacement path:** `ActiveWinFallback` (`active-win` or PowerShell foreground-window poll + Electron `powerMonitor.getSystemIdleTime()`) is ~40 lines and can replace AW for window/idle if AW misbehaves.
- Custom `browser-ext` and `vscode-ext` supply the signals AW lacks (scroll, video state, edit bursts) and are built in Stage 9.

### 12.3 Normalizer
- Process name / domain → category via `categories.json`.
- Games detected by known-list, Steam/Epic path, or user label-once.
- Output events (section 6) with titles/URLs removed.
- Debounce window changes shorter than 3 s into the previous state.

### 12.4 Laptop state inference (rule-based)
Window of last 60 s, evaluated every 5 s:

| State | Rule |
|---|---|
| `away` | idle ≥ 5 min |
| `idle_open` | work app focused, idle 60 s–5 min |
| `flowing` | work app (coding/docs/learning) focused, activity continuous, switches < 3/min (+ edit bursts if available) |
| `stuck` | work app, errors rising and edits low, or long pauses with deletions [needs vscode-ext] |
| `browsing` | browser focused, category social/news/shopping/video |
| `gaming` | game category focused |
| `offline` | adapter not reporting (gap) |

Emits `state_snapshot` to the relay on change and every 5 s as a heartbeat.

### 12.5 Pet "Pip" & Windows Click-Through Boilerplate
- Electron `BrowserWindow`: `transparent: true, frame: false, alwaysOnTop: true, skipTaskbar: true, resizable: false`.
- **Windows Click-Through Hit-Testing (critical boilerplate):** on Windows, `setIgnoreMouseEvents(true, { forward: true })` forwards `mousemove` events to the renderer DOM. Attach `mouseenter` and `mouseleave` listeners exclusively to the `#pip-hitbox` div:
  ```ts
  // renderer.ts
  const hitbox = document.getElementById('pip-hitbox')!;
  hitbox.addEventListener('mouseenter', () => ipcRenderer.send('set-ignore-mouse', false));
  hitbox.addEventListener('mouseleave', () => ipcRenderer.send('set-ignore-mouse', true));

  // main.ts
  petWindow.setIgnoreMouseEvents(true, { forward: true });
  ipcMain.on('set-ignore-mouse', (_, ignore: boolean) => {
    petWindow.setIgnoreMouseEvents(ignore, { forward: true });
  });
  ```
- Always-on-top `'screen-saver'` level to show over most windows. Exclusive-fullscreen games hide it; borderless works.
- Animations: 6 states from sprite sheets or Lottie; free assets only (credit them).

| Focus state | Pip behavior |
|---|---|
| deep_focus | Sleeping/quietly working, tiny breathing animation |
| notification held | Perks up, "1 held" badge |
| drifting | Looks at you, soft nudge bubble |
| distracted | Worried; offers "resume task" |
| recovering | Cheers |
| fatigued | Yawns, suggests a break |
| second-screen overlap | Raises eyebrow: "Phone and laptop both busy?" |

- Speech bubble style: short, kind, never nagging; per-type cooldowns (no repeats within 10 min).
- Click → panel: today's focus time, held items (show now), declare session, mini what-if, pairing code.
- Declared session: "Focus: coding, 2h, strict" (tray menu and panel). Sets `taskSource=declared`.

### 12.6 Windows notifications (Stage 9)
Windows exposes a notification-listener API reachable via a native module or a PowerShell/WinRT bridge; it is fiddly, so it is the last item in Stage 9. Emit categorized `notif_posted` events with `device: laptop` (no text), so laptop notifications can also be held/learned from. If the bridge cannot be made reliable, record that limitation in the demo notes rather than shipping it half-working.

---

## 13. Extensions (Stage 9)

### 13.1 VS Code extension (TypeScript)
Report counts only to `ws://localhost:<port>` (the Electron app):
- `onDidChangeTextDocument` → `editsPerMin`; `onDidSaveTextDocument` → `saves`; `onDidChangeDiagnostics` → `errors/warnings`; task/debug start-end; git commits via built-in Git API; `window.onDidChangeWindowState` focus loss/gain.
- **Resumption delay** = time from window focus regained to first edit burst → `resume_first_edit`.
- States: `flowing`, `stuck`, `idle_open`.
- Never read document text.

### 13.2 Browser extension (MV3, Chrome/Edge)
- Content script: scroll/wheel counts per minute, `video.playbackRate/paused/seeks`, YouTube path pattern (`/shorts` vs `/watch`) used immediately, not stored.
- Service worker: `chrome.tabs`, `chrome.idle`; persist state in `chrome.storage` (MV3 workers are killed often).
- Sends `tab_category` events to the Electron app via localhost WebSocket.

---

## 14. Sync and relay

### 14.1 Purpose
Move categorized events and state between devices; carry commands (declare session, show now); no storage.

### 14.2 Pairing
1. Laptop shows a 6-char code (e.g. `K7M2QX`) in the panel.
2. Phone enters the code in Settings.
3. Both connect to `wss://<relay>/ws?room=<sha256(code)>&device=phone|laptop`.
4. The relay holds at most 2 clients per room, rooms live in memory only, idle rooms expire in 24 h.
5. End-to-end encryption (Stage 8): derive an AES-GCM key from the code (PBKDF2) and encrypt payloads; the relay then sees only ciphertext.

### 14.3 Protocol (JSON messages)

| type | direction | payload |
|---|---|---|
| `hello` | both | `{device, version, lastSeqAcked}` |
| `ping / pong` | both | `{t0, t1, t2, t3}` NTP-style: `offset = ((t1−t0)+(t2−t3))/2` |
| `events` | both | `{fromSeq, events:[...]}` batched ≤ 2 s or 50 events |
| `ack` | both | `{upToSeq}` (cumulative) |
| `state` | both | ContextSnapshot subset: `{focusState, laptopState/phoneState, confidence, declared, heldCount}` |
| `pet_event` | phone→laptop | `{kind: held|released|nudge|celebrate, count}` |
| `command` | both | `{cmd: declare_session|end_session|show_now|kill_switch, args}` |

- **Offline queue:** each device persists outbound events with `seq`; on reconnect, `hello` carries `lastSeqAcked`, the peer replays missing events, and the receiver applies `tsAdj`.
- **Dedup:** by `(device, seq)`.
- **Rate limit:** relay drops clients exceeding 50 msg/s.
- **Gap events:** marked when a peer is silent > 30 s.

### 14.4 Relay deployment (AWS EC2 primary + Render/Railway 2-minute fallback)

**Option A (primary): AWS EC2**
- **Instance:** one small EC2 (Graviton `t4g.micro`/`small`) in `ap-south-1` (Mumbai) for latency to India, Ubuntu LTS.
- **Software:** Node 20 + `ws` server (~100 lines) under `systemd`; **Caddy** as the TLS reverse proxy (Android blocks cleartext by default, so `wss` is required).
- **Hostname for TLS without buying a domain:** use `<ELASTIC-IP-with-dashes>.sslip.io`; Caddy obtains a Let's Encrypt cert automatically.
- **Security group:** 443 from anywhere; 22 from your IP only.
- **Cost estimate (approximate, verify current pricing):** about $4–7 for the event week and $12–15 for a month (under 10% of a $400 USD credit). Set an AWS Budgets alert at $20 and terminate after the event. Avoid ALB and NAT Gateway.

**Option B (zero-config fallback): Render / Railway / Fly.io**
- Push `relay/server.js` and `package.json` to GitHub and deploy as a Web Service. Gives an instant `wss://<app>.onrender.com` with automated TLS in about 2 minutes if the EC2/Caddy setup hits snags. Free tiers may sleep when idle, so ping it before the demo.

**Rule:** if `wss` is not echoing within 15 minutes in Stage 1, switch to Option B and move on.

### 14.5 Failure behavior
- Relay down: both devices continue independently; UI shows "Unsynced"; the phone twin lowers confidence and states it.
- Peer offline: `laptopState = offline`; decisions use phone-only context.
- Reconnect: exponential backoff (1 s → 30 s) + backfill.

---

## 15. Personas and synthetic data

### 15.1 Persona definition (hidden ground truth)

```json
{
  "name": "Rahul",
  "pickupProb": { "social_notif|coding": 0.62, "msg_known|coding": 0.55, "promo|coding": 0.12 },
  "dwell": { "social": {"mu": 2.6, "sigma": 0.7}, "short_video": {"mu": 2.8, "sigma": 0.9} },
  "resume": { "coding": {"mu": 2.0, "sigma": 0.6} },
  "fatigueMultiplier": [1.0, 1.2, 1.5],
  "focusPeaks": [10, 11, 15, 21],
  "selfInitiatedPerHour": 1.4,
  "importantSenders": ["mom", "teammate_a"]
}
```

### 15.2 Starter personas
| Persona | Character | Why in demo |
|---|---|---|
| **Rahul**, developer | High pickup for social likes while coding, long resume delay when fatigued, peak focus 10–12 & 21–24 | Hero scenario |
| **Anika**, student | Lectures on YouTube (watch pages are fine), Shorts are the problem, exam-night late hours | Shows intentional-vs-compulsive logic |
| **Kabir**, gamer-dev | High self-initiated pickups (`selfInitiatedPerHour = 4.5`), low notification effect | Shows the twin learns that suppression helps little for him (`ΔL ≈ 0.4 min`, honest counterfactual) |

### 15.3 History generator
Simulates 14 days of events with the same schema, `synthetic:true`, including cross-device events (laptop work, phone pickups, second-screen overlap). Uses the persona's hidden parameters, so counterfactual truth is known (for 10.4).

### 15.4 LLM-assisted dataset (development-time, OpenAI/Gemini)
- Generate ~2,000 labeled notification samples (EN + Hinglish): `urgent / important / normal / engagement_bait / promo`, with sender types.
- Use it to set the urgency keyword list and to evaluate the importance heuristic; optionally train a tiny on-device classifier (TFLite/logistic) if time remains after Stage 10.
- No real user text is sent to any cloud LLM.

### 15.5 Demo fast-forward
Persona switch → "Train 14 days" runs P11 at ~100× with a progress animation; the Twin Lab drawer shows Beta curves tightening and the prediction updating.

---

## 16. Real-data logging plan (start immediately, before kickoff if the rules allow)

| When | Action |
|---|---|
| Now | Install **ActivityWatch** on the laptop (window + afk + web watchers); leave running. Verify at `http://localhost:5600`. |
| Now | Install **aw-watcher-vscode** (via the VS Code marketplace) if available. |
| End of Stage 2 | The phone collectors from Stage 2 are the real-data logger: install on the Reno 14, apply the Oppo background settings, and let it run for the rest of the build. |
| Each hour | Spot-check that the foreground service is alive and gaps are marked. |
| Stage 10 | Export both logs; verify no titles/text leaked; load the real-data twin next to the personas. |
| At demo | Show the real-data twin alongside persona twins; say honestly that real data is hours old. |

---

## 17. Build plan: 12 hours, 10 stages, full scope

> **[ASSUMPTION]** You have **12 working hours** (even though the event is 24 h). The plan keeps **every feature**, so it is tight: the core path (Stages 1 → 3 → 5 → 6) is strictly sequential, and boilerplate (Compose screens, Electron shell, relay server, extension skeletons) is delegated to AI assistants so your own time goes to twin logic. If any pre-event setup is allowed by the rules, do Stage 1's setup items beforehand and gain ~30 minutes.

### 17.1 Overview

| # | Stage | Clock | Minutes | Gate |
|---|---|---|---|---|
| 1 | Setup and foundations | 0:00–0:50 | 50 | `wss` echo OK; app installs on Reno 14; schema frozen |
| 2 | Phone collectors and ingest | 0:50–1:50 | 60 | Real events in Room; service survives screen-off |
| 3 | Twin core | 1:50–3:15 | 85 | **Gate 1:** paired SHOW/SUPPRESS in <10 ms |
| 4 | Personas, replay, episodes, learning | 3:15–4:10 | 55 | Replay recovers persona parameters |
| 5 | Decision, safety, counterfactuals | 4:10–5:10 | 60 | 10-notification verdict sheet correct |
| 6 | Actuator and 3-tab UI | 5:10–6:50 | 100 | **Gate 2:** real notification held, digested, undone |
| 7 | Laptop app and Pip | 6:50–8:00 | 70 | Pip reacts to real state; click-through works |
| 8 | Sync and security | 8:00–9:00 | 60 | **Gate 3:** both screens show same state; reconnect replays |
| 9 | Extensions and laptop notifications | 9:00–10:20 | 80 | VS Code + browser signals change Pip and phone context |
| 10 | Insights, validation, polish, demo | 10:20–12:00 | 100 | Full demo runs twice end to end |

Total: 720 minutes.

### 17.2 Stage details

#### Stage 1: Setup and foundations (0:00–0:50)
- Android Studio project (Compose, Room, WorkManager, Koin/Hilt) with the package skeleton from 5.3.
- `core-spec/`: event schema (6), `categories.json` (6.3), default parameters (Appendix A).
- Relay: deploy per 14.4 Option A; apply the 15-minute rule and fall back to Option B if needed.
- ActivityWatch running and verified (16); persona JSONs ×3 (15) and the notification dataset from your OpenAI key (15.4).
- Laptop scaffold with `electron-vite` and `ws` only (no native addons, 12.1).
- **Exit check:** `wss` echo from a test client works; the app installs on the Reno 14; schema frozen (changes after this cost time).

#### Stage 2: Phone collectors and ingest (0:50–1:50)
- Onboarding and permission flow with the Oppo checklist (11.2, 22.1).
- `NotifCollector` (with `isMessagingStyle`, `requestRebind`, watchdog), `UsageCollector`, `ScreenReceiver`, `A11yCollector` with `surface_map.json` (11.3).
- `Normalizer`, `AppCategorizer`, label-once store, `gap` events, async Room writes, and the `InMemoryParamStore` loader skeleton (11.4).
- Foreground service; **leave it running for the rest of the build** (real data).
- **Exit check:** a real notification, a pickup and a scroll burst appear as schema events in Room; the service is still alive after 10 minutes of screen-off.

#### Stage 3: Twin core (1:50–3:15)
- `BetaStat`, `LogNormalStat` (Welford), Dirichlet, Gamma hazard, seeded RNG (8.4).
- Hierarchical backoff, recency decay, in-memory param store with async Room write-through (D15).
- Fatigue index, context bucketing (8.3), `StateFlow<ContextSnapshot>` (6.4, 9.1).
- `PairedMonteCarlo` with common random numbers (8.7): mean/p10/p50/p90, `P(L>1m)`, `ΔL` with 80% CI, confidence.
- JIT warm-up at startup and a latency benchmark.
- Unit tests: seeded reproducibility; `ΔL ≈ c_glance` when `p_pickup = 0`; stats converge to analytic values.
- **Gate 1 (3:15):** a hard-coded snapshot returns paired SHOW/SUPPRESS distributions in under 10 ms (warm), tests green.

#### Stage 4: Personas, replay, episodes and learning (3:15–4:10)
- Persona definitions (Rahul, Anika, Kabir) and the history generator with true counterfactual outcomes (15).
- Replay CLI at 100× with the temporal-isolation guard (10.5).
- Single-owner `EpisodeBuilder` (7.1): supersede rule, in-session arrivals, self-initiated episodes.
- `EpisodeCloser` → learning (P5), so replays move the parameters.
- Importance model (8.5) and intentional-vs-compulsive score with online updates (8.6).
- **Exit check:** a 14-day Rahul replay recovers `p_pickup` within ±0.08; the "two notifications 3 min apart" test closes the first as `superseded`.

#### Stage 5: Decision, safety and counterfactuals (4:10–5:10)
- `CostFunction` with hour-normalized `τ`, `c_digest`, `c_rel`, `λ_miss`, and `ActionSelector` (9.2).
- Safety module: never-hold rules, VIP from favorite contacts plus manual add, kill switch, fail-open, item expiry (9.5).
- `Explainer` templates (9.3); exploration policy `ε` with propensity logging and async decision log (9.4).
- IPW / doubly robust estimators, regret and saved-minutes accounting (10.2).
- **Exit check:** a 10-notification test sheet produces the expected verdicts, each of `SILENT / DIGEST / DELAY / ALLOW` is triggered at least once, and no never-hold item is ever held.

#### Stage 6: Actuator and 3-tab UI (5:10–6:50)
- Actuator (11.5): snooze-first for MessagingStyle and DELAY; cancel+digest for social/promo/demo; `DigestBuilder`, `ReleaseScheduler`, Undo/Show-now (with the snooze caveat).
- **Tab 1 Simulate & Lab:** context chips, two timelines with p10–p90 bands, verdict card, reason line, Twin Lab drawer.
- **Tab 2 Live & Digest:** focus ring, laptop badge, decisions with explanations, digest list with Show-now / Important / Noise.
- **Tab 3 Insights:** scaffold only (charts are filled in Stage 10).
- **Persona & Settings sheet:** persona picker, fast-forward, VIP and never-hold lists, caution slider, exploration toggle, kill switch, pairing-code field; declared-session tile.
- **Gate 2 (6:50):** a real notification on the Reno 14 is held or snoozed, appears in the digest, and Show-now works; the Simulate tab shows both timelines for a chosen persona.

#### Stage 7: Laptop app and Pip (6:50–8:00)
- Electron TS shell, `AwAdapter` + `ActiveWinFallback`, normalizer (categories only), JSONL store with ring buffer (12.1–12.3).
- Rule-based laptop states (12.4) and heartbeat emission.
- Pip window with hitbox IPC click-through (12.5), 7 behaviors, bubbles with cooldowns.
- Panel: today's focus, held items, declare session, pairing code, what-if.
- **Exit check:** Pip reacts to a real laptop state change, and you can click through its transparent area to VS Code behind it.

#### Stage 8: Sync and security (8:00–9:00)
- Final relay server and clients on both sides: `hello`, `ping/pong` offset, `events`/`ack` with backfill and dedup, `state`, `pet_event`, `command` (14.3).
- Phone fusion: laptop state in the snapshot, second-screen overlap detection.
- Mirror: held badges and pet events on the laptop; remote commands (declare session, show now).
- End-to-end AES-GCM with a PBKDF2-derived key from the pairing code (14.2).
- **Gate 3 (9:00):** both screens show the same focus state; a drop-and-reconnect test replays the missed events with no duplicates.

#### Stage 9: Extensions and laptop notifications (9:00–10:20)
- VS Code extension (13.1) with `resume_first_edit` (measured resumption delay) and the `stuck` state.
- Browser extension MV3 (13.2): scroll counts, video state, `/shorts` vs `/watch`, domain category.
- Windows notifications bridge (12.6), last in the stage.
- Wire all three into laptop state inference and cross-device episodes.
- **Exit check:** a VS Code edit burst and a browser Shorts session each change Pip's state and the phone's context snapshot.

#### Stage 10: Insights, validation, polish and demo (10:20–12:00)
- **Insights charts:** focus-hour heatmap, interruption triggers, fatigue curve, fragmenting apps, notifications that matter, long-session surfaces, intentional-vs-compulsive breakdown, regret / saved minutes.
- **Twin Lab:** before/after personalization diff, confidence display.
- **Validation (10.4, 18.3):** parameter recovery, calibration (Brier/ECE), minutes saved vs urgent held (= 0), counterfactual error vs truth, decision latency, battery check.
- Polish: empty/error/offline states, simulated-sync toggle, demo fast-forward animation.
- Rehearse the demo twice, record the backup video, build slides (architecture, privacy, metrics, ActivityWatch credit).
- **Exit check:** the full demo (section 19) runs end to end on your own devices, twice.

### 17.3 Gate and slippage protocol
- **Gate 1 (3:15), Gate 2 (6:50), Gate 3 (9:00)** are the checkpoints. Everything before Gate 2 is the product; everything after is amplification.
- If a gate slips by **more than 30 minutes**, borrow time from the **end** of a later stage, in this order: Stage 9 tail (Windows notifications) → Stage 10 extra charts → Stage 7 extra animations → Stage 9 browser extension polish. Items stay in the plan; they simply move to "after the demo is safe".
- **Never trade away:** the Simulate tab, paired SHOW/SUPPRESS with uncertainty, safety rules, explanations, persona demo, and one real hold-and-digest on the phone.
- Do not start Stages 7–9 until Gate 2 is passed, so the main demo is never at risk.

### 17.4 Where AI assistants do the heavy lifting
Stage 2 (collectors boilerplate), Stage 6 (Compose screens), Stage 7 (Electron shell, Pip window), Stage 8 (relay and clients), Stage 9 (extension skeletons). Use the prompts in section 23 and review output against this spec.

---

## 18. Testing and metrics

### 18.1 Unit tests (pure Kotlin `twin/`)
- Beta update and shrinkage, lognormal Welford, Dirichlet sampling (seeded RNG).
- Paired simulator reproducibility given a seed; with `p_pickup = 0` the mean `ΔL` equals `c_glance` even under high `λ_self`.
- Cost function chooses `ALLOW` as `I→1`, `SILENT` when `I < 0.16` and `ΔL` is high, and `DIGEST`/`DELAY` for moderate `I`.
- Single-owner episode test: two notifications 3 minutes apart before pickup result in the first episode closed as `superseded` and the second owning the dwell.
- Never-hold rules (calls, OTP, VIP) can never be overridden by the cost function.
- Temporal-isolation test (10.5).

### 18.2 Integration tests
- Persona replay → episodes count and labels sensible.
- Relay: reconnect + backfill without duplicates; clock offset sign convention.
- Oppo soak test: screen-off for the stretch between stages, check collector gaps.

### 18.3 Headline metrics (for slides)
Parameter recovery error, calibration (Brier/ECE), minutes saved per day vs urgent items delayed (= 0), counterfactual error vs ground truth, decision latency (target < 10 ms in-memory), battery impact (target < 3%/day extra, measure it).

---

## 19. Demo script (live on own devices, ~4–5 min)

1. **Hook (20 s):** "A single 'like' notification costs this developer 24 minutes. Dhyan simulates both futures before your phone even buzzes."
2. **Meet Pip (20 s):** the laptop pet sleeping while VS Code is open; phone Live tab shows the same focus state (sync).
3. **The hero scenario (60 s):** choose Rahul, coding, 4 hrs in. Fire "Someone liked your post". The two timelines appear: SHOW 24 min (8–41) vs SUPPRESS 2 min. Verdict: **Digest**, with the one-line reason. Pip perks up: "1 held."
4. **The twin is not a blocker (30 s):** fire "Mom: call me, urgent". It goes through immediately, with the reason ("high importance, family").
5. **Counterfactual and honest limits (30 s):** switch to Kabir (high self-distraction); show that the twin predicts only about 1 min saved from suppression because Kabir self-interrupts anyway, plus the exploration-backed regret score.
6. **It learns (45 s):** tap "that was important" on a held item; expand the Twin Lab drawer to show the Beta curve shift and the next prediction change live.
7. **Intentionality (30 s):** Anika's watch-page lecture, not flagged; Shorts, flagged with "Study or break?"
8. **Real data (30 s):** the twin on today's real phone and laptop logs, plus an honest "low confidence, 14 observations".
9. **Close (20 s):** privacy slide (only categories and counts leave the device), metrics slide, safety (never holds calls/OTPs, kill switch).

**Backup:** a screen recording of steps 2–7; a "simulated sync" toggle; spare phone hotspot.

---

## 20. Pitch Q&A (prepared answers)

1. **Why isn't this a Focus-mode app?** Rules block by category; Dhyan predicts the consequence for *this person* in *this context* and trades it against importance.
2. **How do you get counterfactuals?** Paired Common-Random-Number simulation, logged propensities, and 5% randomized exploration; validated on personas where the truth is known. Assumptions stated.
3. **Isn't synthetic data cheating?** It is how you validate counterfactuals; real data is shown alongside, and we say how old it is.
4. **Privacy?** Categories and counts only, on-device models, hashed senders, nothing text-like leaves the phone.
5. **What if it hides something important?** Never-hold list, VIPs, importance override, undo, kill switch, and fail-open.
6. **Is long YouTube time penalized?** No. Intentionality is scored from switching, surface, engagement and the other device, and the user can declare intent.
7. **Why not a neural net?** Hours of data; interpretable Bayesian counts update in O(1) time and give exact epistemic uncertainty.
8. **Accessibility service creepy?** It subscribes to scroll events only, never reads text, only for an allow-list of apps, and is optional with a fallback.
9. **What if the laptop is off?** The phone twin still works with lower confidence, and says so.
10. **What next?** Calendar-aware context, on-device federated personalization, mobile↔desktop wellbeing coaching, and a small learned model once data accumulates.

---

## 21. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| ColorOS kills background services | Data gaps, broken holds | Foreground service, auto-start, battery exemption, rebind, gap marking, soak test early |
| Android restricted settings block toggles (sideloaded app) | Can't enable listener/a11y | "Allow restricted settings" step in the guide |
| MessagingStyle apps (WhatsApp) re-post cancelled notifications | Holds fail or double-chime | `snoozeNotification()` as primary actuator for messaging apps (11.5); verify on device |
| Snoozed items cannot be un-snoozed early | "Show now" duplicates later | Re-post/launch for Show-now and clear the duplicate on reappearance (11.5) |
| Accessibility API surface IDs change | Surface detection fails | JSON config + other signals; treat as weak signal |
| SQLite disk I/O blocks `onNotificationPosted` | Slow decisions / ANR | `InMemoryParamStore` + `StateFlow<ContextSnapshot>` hot path (9.1, 11.4) |
| JVM cold start slows first decision | First notification lags | Warm-up simulation at app start (9.1) |
| Electron native addon build fails on Windows | Laptop app blocked | No `better-sqlite3`; JSONL + in-memory array (12.1) |
| Electron pet blocks clicks to VS Code behind it | Unusable desktop UX | `#pip-hitbox` `mouseenter`/`mouseleave` IPC forwarding (12.5) |
| Sync failure at demo | Embarrassment | Simulated-sync toggle, recorded backup, phone hotspot, Render fallback |
| Statistics bugs | Wrong claims | Paired CRN draws, single-owner episodes, seeded unit tests |
| Full scope in 12 hours | Unfinished core | Sequential core path, gates, slippage protocol (17.3), AI for boilerplate |
| Windows notification bridge unreliable | Unfinished feature | Last item in Stage 9; document limitation if needed |

---

## 22. Setup guide

### 22.1 Oppo Reno 14 (ColorOS) checklist
1. Settings → About → tap Build number ×7 → Developer options → **USB debugging** ON.
2. Install the app (Android Studio Run, or `adb install`).
3. Grant **Notification access**, **Usage access**, **Accessibility** (if toggles are greyed: App info → ⋮ → *Allow restricted settings*).
4. App info → Battery → **Unrestricted / Allow background activity**; disable battery optimization.
5. Enable **Auto-start** for Dhyan.
6. Lock the app in Recents.
7. Keep the foreground service notification visible.
8. Reference: dontkillmyapp.com for current Oppo steps (menu names vary by version).

### 22.2 Laptop
1. Install Node 20 LTS, VS Code, Git.
2. Install ActivityWatch; open `http://localhost:5600`; confirm window and afk buckets.
3. `npm create @quick-start/electron` (electron-vite) with TypeScript; add `ws` (no native SQLite addons).
4. Run `aw-watcher-web` extension (optional but useful before Stage 9).
5. Windows: if the pet hides behind games, use borderless windowed mode.

### 22.3 AWS relay (or Render/Railway)
Follow section 14.4. Add an AWS Budgets alert at $20. Tag resources `project=dhyan`. Terminate after the event.

### 22.4 Android Studio
- Latest stable Android Studio, one Pixel emulator for UI/engine work, the Reno 14 for everything involving notifications/usage/accessibility.

---

## 23. AI-assist playbook (ChatGPT / Gemini / Antigravity)

- Always paste: section 6 (schema), the relevant module spec, and the repo layout (5.3).
- Ask for **one file/class at a time** with tests; build and run after each.
- Suggested prompts (copy and adapt):
  - *"Write pure-Kotlin `BetaStat` and `LogNormalStat` classes per section 8.4 with Welford updates, seeded sampling and unit tests."*
  - *"Write `PairedMonteCarloSim` per 8.7 using Common Random Numbers producing mean/p10/p50/p90/P(L>1m) for SHOW, SUPPRESS, and ΔL with N=500."*
  - *"Write `CostFunction` and `ActionSelector` per 9.2 with hour-normalized τ, c_digest=0.8, and unit tests proving SILENT, DIGEST, DELAY, and ALLOW each win in their respective regimes."*
  - *"Write a `NotificationListenerService` per 11.3 and 11.5 that reads from `InMemoryParamStore`, uses `snoozeNotification` for MessagingStyle apps, and emits `notif_posted` events without storing raw text."*
  - *"Write the Electron pet window per 12.5 (transparent, click-through via `#pip-hitbox` mouseenter/mouseleave IPC, draggable) with a state machine for the 7 behaviors."*
  - *"Write a Node `ws` relay per 14.3: rooms keyed by hash, max 2 clients, in-memory, rate-limited, ping/pong."*
  - *"Write a VS Code extension per 13.1 that sends only counts to a localhost WebSocket."*
- Have a second AI review each module against this spec (privacy, safety, seeded determinism).
- Do **not** paste real notification text, window titles or personal data into any AI tool.

---

## 24. Assumptions and open items

**Assumptions made (change them freely):**
1. **[ASSUMPTION]** You have 12 working hours; pre-event logging and tooling setup are allowed only if the rules permit. Check official rules about pre-built tools, pre-written code, and open-source use (ActivityWatch must be credited).
2. **[ASSUMPTION]** AWS credits are USD; confirm balance and expiry.
3. **[ASSUMPTION]** Laptop is Windows 10/11 with Chrome or Edge; phone is the Oppo Reno 14 on ColorOS.
4. **[ASSUMPTION]** Outcome window 30 min; exploration ε = 5%; both configurable.
5. **[ASSUMPTION]** Snooze-first actuation for messaging apps and cancel+repost digest for non-messaging apps.
6. **[ASSUMPTION]** Judges are a mixed panel (technical + product).

**Still to verify at build time:**
- Current ActivityWatch API/bucket names; current AWS pricing in `ap-south-1`.
- How Oppo ColorOS handles `snoozeNotification` vs `cancelNotification` on WhatsApp and Instagram, and the duplicate behavior after Show-now.
- Foreground service type requirements for the target SDK.
- Whether the Windows notification bridge is reliable enough to demo.
- Final judging criteria and demo format.

---

## Appendix A: Default parameters (single place to tune)

| Name | Symbol | Default |
|---|---|---|
| Monte Carlo runs | `N` | 500 |
| Outcome window | `W` | 30 min |
| Hierarchy strength | `κ` | 5 |
| Beta recency decay | `γ` | 0.995 / episode |
| Lognormal prior strength | `n0` | 3 |
| Lognormal sigma floor | `σ_min` | 0.25 |
| Delay harm coefficient (per hour at I=1) | `κ_d` | 3.0 min/hr |
| Silent miss penalty | `λ_miss` | 5.0 min |
| Digest review overhead | `c_digest` | 0.8 min |
| Release discount at break | `c_rel` | 0.25 |
| Glance cost | `c_glance` | 0.4 min |
| Always-allow importance threshold | `I_max` | 0.85 |
| Silent-vs-Digest crossover (`c_digest / λ_miss`) | `I_silent` | 0.16 |
| Exploration rate | `ε` | 0.05 (when `I < 0.4`) |
| Low-confidence threshold | `conf_min` | 0.40 |
| Compulsive-score thresholds | none | 0.35 / 0.65 |
| State heartbeat | none | 5 s |
| Context snapshot interval | none | 10–30 s |
| Digest interval | `τ_digest_max` | 120 min |
| Relay reconnect backoff | none | 1 s → 30 s |

## Appendix B: Glossary
- **Episode:** a trigger plus its observed outcomes over the 30-minute outcome window (single-owner attributed).
- **Counterfactual:** the estimated outcome of the action not taken.
- **Common Random Numbers (CRN):** sharing the baseline hazard draw across SHOW and SUPPRESS in each Monte Carlo trial to reduce the variance of `ΔL`. Individual runs of `ΔL` can still be negative.
- **Propensity (π):** probability that the logged action was chosen in that context.
- **Second-screen overlap:** phone use while the laptop is in a work state.
- **Resumption delay:** time from returning to a work app to the first meaningful activity.
- **Hierarchical backoff:** using a broader bucket's statistics when the specific bucket has little data.

---

## 25. Critical Review Amendments — Correctness, Safety, and Handoff (v2.1)

> **Scope-preservation rule (binding):** This section clarifies and strengthens the existing specification. It does **not** remove, permanently defer, or downgrade any previously planned feature. All requirements in sections 1–24 remain in scope, including the phone app, laptop app, Pip's seven behaviors, ActivityWatch adapter, Windows notification bridge, VS Code extension, browser extension, sync/relay, all six insights, persona simulation, replay, learning, counterfactual estimation, privacy/security, and the complete three-tab interface. Any schedule adjustment is a sequencing change only; it is not permission to omit a feature. If the 12-hour limit conflicts with completing all scope safely, report the unfinished items explicitly and continue after the demo rather than pretending they are complete.

### 25.1 Authority and conflict-resolution rules

1. Sections 1–24 define the original product intent and feature inventory. This section adds correctness requirements and resolves ambiguities where the old wording is unsafe or statistically misleading.
2. When a statement in sections 1–24 conflicts with an explicit safety, measurement, privacy, or validation rule in this section, use the safer/more precise rule here while preserving the feature itself.
3. Do not silently invent behavior to resolve ambiguity. The implementing agent must document the conflict, propose the smallest compatible resolution, and ask the project owner if the choice changes user-visible behavior, the data contract, or the planned scope.
4. Keep a feature traceability ledger with every original requirement marked `not_started`, `in_progress`, `implemented`, `tested`, or `blocked`. `Implemented` is not the same as `tested`; only observed test evidence may mark a requirement `tested`.
5. Never claim a feature works on the Oppo Reno 14, Windows, a browser, or a relay deployment without a test result from that target. If the target is unavailable, label the result `not verified on target` and keep the feature in scope.

### 25.2 Twin simulation: competing risks, baseline behavior, and uncertainty

The current section 8.7 pseudocode is a useful starting point, but its independent `picked_i` draw does not explicitly model *when* the notification-triggered pickup competes with the user's baseline self-initiated pickup. Treat this as a model-contract clarification, not a feature removal.

**Required model behavior:**

- `SUPPRESS` must contain baseline self-initiated distraction opportunities; suppression does not make the user immune to self-distraction.
- `SHOW` must account for both the notification-triggered opportunity and baseline self-initiated hazard. Use a competing-risk/time-to-event model or an equivalently documented event-order model so that the first qualifying event owns the episode. Do not count the same dwell/resumption cost twice.
- Use paired common random numbers (CRN) for shared exogenous randomness where mathematically valid. Do not force an identical realized baseline event into both arms when the notification changes event timing or prevents a second event; preserve the coupling, but allow arm outcomes to differ for causal reasons.
- Document whether the outcome window is truncated at `W`, whether multiple distractions can occur in one window, and how overlapping dwell/resumption costs are handled. The first implementation may use a single-owner approximation, but it must be explicit, deterministic under a fixed seed, and tested against synthetic ground truth.
- Keep the existing test that `mean(ΔL) ≈ c_glance` when `p_pickup = 0` **if** the chosen model defines every shown notification as incurring a glance cost even when it is ignored. This identity is not itself a defect. Also test a no-glance/zero-cost configuration where appropriate, and test high-baseline-hazard personas to ensure the estimated incremental harm approaches the expected low value rather than double-counting baseline costs.
- The output remains SHOW vs SUPPRESS distributions, mean/p10/p50/p90, `P(L > 1 min)`, an 80% interval for `ΔL`, and the notification-pickup posterior interval. Do not label p10–p90 as a Bayesian credible interval unless the statistic and sampling procedure justify that term; call it an **80% simulation interval** otherwise.

**Separate uncertainty sources in internal results and diagnostics:**

1. Parameter/epistemic uncertainty from posterior draws.
2. Outcome/process variability from simulated user behavior.
3. Monte Carlo error due to finite `N`.
4. Model uncertainty due to assumptions, omitted behaviors, or weak evidence.

The UI may summarize these for users, but logs/tests must not collapse them into one unexplained number.

### 25.3 Confidence and calibration claims

- `conf = n_eff / (n_eff + 10)` is a **heuristic evidence-strength score**, not a calibrated probability that a prediction is correct. Rename its UI label to `evidence strength` or clearly describe it as a heuristic. Do not call it “80% confidence” or a probability of correctness.
- Keep prediction uncertainty intervals separate from the evidence-strength score.
- Calibrate probability predictions using held-out temporal data and report Brier score and expected calibration error (ECE), including sample size and the evaluation split. Use synthetic personas for known-ground-truth parameter recovery; use real user logs only for outcomes that are actually observable.
- Show data provenance in the UI: `observed`, `learned`, `simulated`, `estimated`, or `low evidence`. Never present synthetic persona outputs as live user measurements.
- Use a deterministic seed and named fixture for headline demo values. The displayed “24 min”, “2 min”, “urgent delayed = 0”, and similar claims must be computed from the same visible scenario/configuration or clearly marked as illustrative. Never hard-code a success metric merely to match the pitch.

### 25.4 Episode attribution: distinguish observed outcomes from unresolved outcomes

The existing `superseded` behavior prevents double-counting, but an episode closed because a newer notification arrived is not necessarily evidence that the earlier notification was consciously ignored.

Use separate fields rather than overloading a single label:

- `trigger_type`: `notification` or `self_initiated`.
- `episode_status`: `open`, `closed`, or `censored`.
- `outcome_label`: `picked_up`, `observed_ignore`, `superseded_unresolved`, `outcome_censored`, or `self_initiated` (where applicable).
- `closure_reason`: `pickup`, `new_notification`, `window_expired`, `service_gap`, `user_action`, or another enumerated reason.

Rules:

1. A superseded episode closes at the newer notification's timestamp for ownership purposes, but it must not automatically increment the ordinary ignore/failure count unless the product has a defensible observation rule that supports that conclusion.
2. A service gap, missing device telemetry, app crash, or truncated outcome window must be marked censored/unresolved, not silently counted as an ignore.
3. A pickup or distraction can have only one owning episode. In-session notification arrivals remain logged but do not create duplicate dwell attribution.
4. Self-initiated episodes contribute to baseline hazard estimation and must not be mislabeled as notification-triggered outcomes.
5. Add tests for duplicate notifications, late/out-of-order events, a service gap, a pickup after superseding, in-session arrivals, and events exactly on the outcome-window boundary.

### 25.5 Safety: fail open, fail visible, and make guarantees testable

The phrase “never hold urgent notifications” is a safety objective, not something a heuristic classifier can guarantee for every unseen or ambiguous notification. Preserve the never-hold behavior, and implement the following conservative policy:

- **Fail open:** if importance classification is missing, uncertain, malformed, stale, or throws; if the current context is stale; if the model is not loaded; if the listener/actuator state is unknown; if the scheduler or persistence layer fails; or if the app is recovering after a crash, do not newly suppress the notification. Allow it through and record a privacy-safe diagnostic event.
- Never-hold categories (calls, alarms, OTPs, configured VIP/favorite contacts, and other explicitly configured critical classes) must bypass learned cost optimization and exploration. Do not let a low predicted interruption cost override these rules.
- Apply never-hold checks both before choosing an action and immediately before actuation. Re-check the notification identity and age so a stale decision cannot be applied to a different/reposted notification.
- The kill switch must stop new holding immediately, release or restore held notifications where technically possible, cancel pending scheduling, and leave a visible state that the user can verify. Define recovery behavior after process death and device reboot.
- A UI claim such as “urgent delayed = 0” is valid only for the explicitly measured test run and its defined urgent test set. It is not a universal proof that classification can never fail.
- Maintain a test matrix covering known urgent examples, ambiguous messages, classifier exceptions, stale context, no network, low battery/background restrictions, process restart, reboot, duplicate/reposted notification, storage failure, scheduler failure, and kill-switch activation.
- Do not promise that all apps expose the same snooze, cancel, restore, or action semantics. Maintain a device/OS/app compatibility matrix and label unsupported paths clearly.

### 25.6 Cost function and action-selection contract

Before implementation is considered complete, document the exact units and decision boundary for every term in section 9.2:

- All modeled interruption costs are in focus-minutes; delay harm must use consistent time units and convert hours/minutes explicitly.
- State whether `ΔL` is an expected incremental cost, a distribution, or both; do not mix a sampled tail statistic with an expected-value term without explaining the policy.
- Define how `ALLOW`, `DELAY`, `DIGEST`, and `SILENT` are compared, including release cost, miss penalty, digest overhead, delay harm, urgency/importance overrides, expiry, and ties.
- The selector must be deterministic given the same context, model snapshot, policy settings, and seed, except where randomized exploration is explicitly enabled and logged.
- Exploration is permitted only in the documented eligible low-importance region. Safety overrides always take precedence. Log the full eligible action set, chosen action, policy/model version, and exact propensity `π(a|x)`; do not log a propensity that ignores safety filtering.
- Add boundary tests around `I_max`, `I_silent`, delay expiry, ties, and missing inputs. Verify each action can win in a well-defined test regime without bypassing safety.

### 25.7 Counterfactual estimation: estimand, support, and honest scope

Sections 1, 3, 9, and 10 retain the full counterfactual pipeline. The following definitions are required so the resulting estimates are defensible:

- Define the target estimand before implementing IPW or doubly robust (DR) estimation: for example, the average treatment effect for **eligible notification contexts where both actions had non-zero probability**, over a stated outcome window and outcome definition.
- State the treatment/action encoding, outcome, context `x`, propensity `π(a|x)`, outcome model, target population, time window, and unit of analysis. Specify whether the estimate is per notification, per episode, or per user-day.
- Use only records with valid action propensities and observed outcomes for the relevant estimator. Exclude or separately analyze censored outcomes, failed actuation, and protocol violations; report their counts.
- Check positivity/overlap and report effective sample size. If an action was impossible because of VIP/strict-session/never-hold rules, its counterfactual effect is **not identified by randomized exploration in that context**. Do not generalize the low-importance exploration result to excluded high-importance contexts.
- Use stabilized/normalized weights and a documented weight-truncation sensitivity analysis where appropriate. Specify the exact IPW and DR equations, nuisance-model fitting procedure, temporal split, and uncertainty method in the implementation docs/tests before calling the estimator complete.
- Synthetic personas may validate against known hidden ground truth; real-world estimates must be described as estimates under stated assumptions, not directly observed facts.
- Do not imply that model-generated counterfactuals are equivalent to randomized causal evidence. UI and pitch language must distinguish `model simulation`, `exploration-supported estimate`, and `observed outcome`.

### 25.8 Privacy and security hardening

The privacy boundary in section 6.5 remains mandatory. Strengthen implementation requirements as follows:

- Raw notification text, window titles, URLs, message content, and keystrokes must never be written to logs, analytics, crash reports, relay messages, or persisted event records. If local in-memory classification requires text, keep its lifetime minimal and clear references after classification/actuation where feasible. Held notification payloads are a narrowly scoped exception already specified in section 6.5 and must remain app-private, access-controlled, short-lived, and deleted after release/expiry.
- Treat sender hashes as **pseudonymous**, not anonymous. Use a per-install secret salt or keyed hash where appropriate; define rotation and deletion behavior. Do not assume a plain hash makes low-entropy sender identifiers unrecoverable.
- A six-character pairing code is a human-friendly pairing mechanism, not a strong standalone encryption secret. Do not rely on PBKDF2 alone to make a low-entropy code resistant to offline guessing. Use an authenticated pairing flow with rate limiting, expiry, explicit user confirmation on both devices, a high-entropy session key or an appropriate key-agreement design, and a clear recovery/re-pair procedure. If the existing PBKDF2-derived design is retained for the prototype, document its threat model and do not claim production-grade security.
- For AES-GCM, guarantee nonce/IV uniqueness for each key, authenticate protocol metadata, reject invalid tags, prevent replay, and define key rotation/revocation. Do not reuse a nonce with the same key.
- Validate every relay message against a strict, versioned schema with size limits, allowed message types, field allowlists, and rate limits. The relay must never accept or forward arbitrary raw notification/window content. Separate telemetry from control commands and authorize commands only for the paired peer.
- Specify TLS/WSS for transport, peer identity checks, reconnect/replay protections, retention periods, local database protection, and deletion/export behavior. Never place pairing secrets or keys in source control or logs.
- Keep a privacy regression test that inspects persisted events, JSONL, Room rows, relay payloads, and diagnostic logs for forbidden content fields.

### 25.9 Event schema, time, persistence, and recovery

The shared event schema in section 6 is a cross-module contract. Freeze and version it before parallel agent work:

- Every event must include a schema version, event ID, source/device ID (pseudonymous), event type, event timestamp, ingestion timestamp, and only the fields allowed for that event type.
- Define timestamp units and clock-offset conventions explicitly. Validate unknown enum values, missing required fields, negative durations, out-of-range probabilities, oversized payloads, and incompatible schema versions.
- Define duplicate handling, late/out-of-order handling, reconnect backfill, event-ID deduplication, and behavior when the device clock changes.
- Distinguish telemetry events from control commands. Commands need command IDs, authorization, expiry, acknowledgement, and idempotent handling; replaying a telemetry event must not repeat an actuation command.
- Keep the in-memory hot path for decisions, but make asynchronous persistence failures visible and recoverable. Define which state is reconstructed from Room/JSONL after restart and which state may be safely reset.
- Room on the phone and JSONL on the laptop must share semantic definitions even though their storage formats differ. Define JSONL rotation/retention, partial-line recovery, disk-full behavior, and safe recovery after truncated writes.
- Define stale-context thresholds for phone and laptop snapshots. Stale or offline laptop data must be marked as such and must not be mistaken for active focus.
- Add replay fixtures that exercise schema migration, duplicate events, reconnect, clock skew, partial writes, and restart recovery.

### 25.10 Notification actuation and device compatibility

Snooze-first remains the intended strategy, but its actual behavior is app- and OS-dependent. Preserve all planned actuation paths while treating each as a separately verified capability.

- For every supported notification style, test hold/snooze, release/show-now, digest creation, cancel/repost fallback, action preservation, duplicate prevention, notification ordering, expiry, and undo.
- Define what happens if the process dies while a notification is held, the phone reboots, the notification is updated by its source app, the original notification is removed, or a repost fails.
- Never silently discard a held notification because the scheduler, database, or relay failed. Prefer allowing it through or surfacing an explicit recoverable error when restoration is uncertain.
- Record compatibility by device model, Android/ColorOS version, target SDK, source app/version, and actuation method. A test on an emulator does not prove behavior on the Oppo Reno 14.
- The UI must distinguish `held`, `snoozed`, `scheduled`, `released`, `expired`, `failed-open`, and `actuation-unknown` where relevant. Do not display “held” merely because a decision was calculated; it must reflect a confirmed actuation result.

### 25.11 Feature-preserving delivery plan and stage gates

The 12-hour / 10-stage plan in section 17 remains the target plan and **all features remain planned**. The earlier slippage protocol is clarified as follows:

- A schedule slip may change order or move a feature to post-demo completion; it must never erase that feature from the traceability ledger or final deliverables.
- Prioritize a safe, testable end-to-end slice first, then continue implementing every remaining module. This is dependency sequencing, not scope reduction.
- Do not describe Stage 9 items (VS Code extension, browser extension, Windows notification bridge) or any Insights chart as optional in the product scope. Their relative order may change, but each needs an explicit completion/test result.
- Each stage gate must record: command(s) run, target/device, result, relevant logs or test names, known failures, unverified assumptions, and which downstream stages are blocked. “Looks good” is not an exit check.
- If a gate fails, stop dependent integration work, fix or document the blocker, and do not fabricate success. Parallel agents must not independently change shared schema, model equations, action semantics, or crypto protocol without updating the canonical contract and tests.
- All items postponed beyond the live demo must be tracked in a dated post-demo completion checklist with an owner, dependency, acceptance test, and status.

### 25.12 Repository handoff and agent continuity checklist

This master specification alone is not proof of implementation state. Before handing the project to another teammate or coding agent, create/update a separate `HANDOFF_STATUS.md` (or equivalent) containing verified facts only:

1. Current repository tree and the purpose of each important module.
2. Exact branch/commit, working-tree status, and any uncommitted changes.
3. Environment and dependency versions, setup commands, and device/emulator targets.
4. Last successful build/test commands and their actual output summaries.
5. Failing commands/tests with the exact error text, suspected cause clearly labeled as a hypothesis, and attempted fixes.
6. Feature ledger mapping every requirement in sections 1–24 and 25 to files/tests/status.
7. Implemented behavior versus stubs, mocks, hard-coded demo data, TODOs, and unverified platform assumptions.
8. Canonical event schema, model parameter contract, action/safety contract, relay protocol, and known migration constraints.
9. Reproduction steps for the main demo, including seeded persona/fixture and recovery steps if it fails.
10. Known regressions, privacy checks, device compatibility results, and next three recommended tasks in dependency order.

**Agent operating rule:** read the spec and handoff first; inspect the repository before editing; do not rewrite functioning modules merely to match personal style; make the smallest coherent change; add or update tests; run the relevant checks; report exact files changed and command results. If a requirement is ambiguous or conflicts with existing code, stop and report the conflict instead of guessing. Never claim to have run a test or verified a device unless it actually happened.

### 25.13 UI honesty and provenance

- Every prediction/insight should be distinguishable as observed, learned, simulated, estimated, or low-evidence.
- Show the evidence count, time range, model version, and relevant uncertainty where practical. Avoid false precision and avoid presenting a heuristic score as a probability.
- Empty, stale, offline, permission-denied, model-not-ready, and actuation-failed states are first-class UI states, not silent zeros.
- “Urgent delayed = 0” is a measured test result for a defined suite; show the suite size and pass/fail result. Do not treat it as a universal guarantee.
- Synthetic persona mode and real-data mode must be visibly distinct. Never mix their records in a user-facing chart without clear labels.
- Preserve the complete three-tab interface, Twin Lab, persona/settings sheet, Pip panel and seven behaviors, and all six insights. If a chart is not implemented yet, display a clearly marked pending/empty state rather than deleting the chart or showing fabricated data.

### 25.14 Additional acceptance tests

These tests supplement—not replace—the tests in sections 17 and 18:

- **Simulation:** fixed-seed determinism; zero notification-pickup case; high baseline hazard; shared-randomness sanity; no double-counted dwell; window truncation; uncertainty components reported separately.
- **Attribution:** superseded unresolved is not automatically ignored; censored outcomes excluded from ordinary success/failure updates; duplicate/late events; one owner per outcome; in-session arrival behavior.
- **Safety:** never-hold bypasses optimization; unknown classification fails open; stale context fails open; storage/scheduler failure; listener loss; process death/reboot; kill switch; expiry; action identity mismatch.
- **Counterfactuals:** exact logged propensity; positivity/overlap diagnostics; effective sample size; no causal claim for excluded contexts; temporal holdout; synthetic ground-truth comparison.
- **Privacy/security:** no raw content in persistent stores/logs/relay; invalid schema rejected; unauthorized command rejected; replay rejected; expired pairing rejected; AEAD tampering rejected; nonce uniqueness strategy documented; key rotation/re-pair path.
- **Recovery/sync:** duplicate-free backfill; clock skew; stale laptop state; partial JSONL line; Room/JSONL write failure; schema-version mismatch; command idempotency.
- **Actuation/UI:** original action affordances preserved where possible; show-now and undo; duplicate prevention; crash/reboot restoration; explicit actuation status; demo and live modes visibly distinct.
- **Feature coverage:** a traceability report proves every original feature is implemented or transparently marked incomplete—not silently omitted.

### 25.15 Final scope statement

Dhyan remains the full cross-device Attention Digital Twin described in this document. The amendments above make the simulator more defensible, safety behavior more conservative, counterfactual claims more honest, privacy/security stronger, event handling more robust, and the handoff more reliable. **No planned feature is removed by these amendments.** Any implementation gap must be recorded as a gap and worked through; it must not be reclassified as an intentional non-goal without the project owner's explicit approval.
