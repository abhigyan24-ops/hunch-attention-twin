# DHYAN (ध्यान): Attention Digital Twin

> **Tagline:** Your attention, simulated before it is spent.
> **Hackathon:** CASCADE '26 · Theme 3 · Problem 3: *The Attention Digital Twin*
> **Builder:** solo · **Stack:** Kotlin + Jetpack Compose (phone), Electron + TypeScript (laptop), Node `ws` relay on AWS
> **Pet:** "Pip", a desktop companion that is the visible face of the twin

*Dhyan* means "attention / focused awareness" in Hindi and Sanskrit.

---

## 0. How to use this document

- This is the **master spec**. It is meant to be pasted (whole or section by section) into ChatGPT / Gemini / Antigravity as context when generating code.
- Section numbers are stable. Prompt like: *"Implement section 11.3 (NotificationListener collector) following section 6 (event schema)."*
- Anything marked **[STRETCH]** is cut first when time runs short (section 17).
- Anything marked **[ASSUMPTION]** is something I decided without confirmation; see section 24.

---

## 1. Overview

### 1.1 What Dhyan is
A **personal digital twin of one user's attention**. It watches the user's phone **and** laptop at the same time (activity categories and counts, never content), learns how that person gets distracted and recovers, and answers:

> *"If I let this notification through right now, what is likely to happen to me, and what would have happened if I had not?"*

It then acts: **allow, delay, batch into a digest, or silently log** the notification, and explains why in one plain sentence.

### 1.2 The one-line example from the problem statement, in our system
User is coding in VS Code for 52 minutes. A "Someone liked your post" notification arrives on the phone.
Twin simulates 500 futures per option:

| | SHOW | SUPPRESS |
|---|---|---|
| P(distraction) | 0.62 | 0.09 |
| Expected focus lost | 24 min (80% CI 8–41) | 2 min |
| Verdict | | **Digest it.** "Likes from this app pull you away 6 of 10 times while coding; 4 hrs in, you take 8 min to resume." |

A message from "Mom" with urgent wording is **allowed**, because importance outweighs predicted cost. The twin is not a blanket blocker.

### 1.3 What makes it different
1. **Cross-device**: phone and laptop are modeled together. Only this system can see "notification on phone → laptop left VS Code → 8 min until first edit".
2. **Counterfactual by design**: show-vs-suppress simulation, plus logged propensities and 5% exploration so counterfactuals can be estimated honestly.
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
| Simulates "if I allow this notification now…" | Monte Carlo SHOW vs SUPPRESS (8.4, 8.7) |
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
| D7 | Sync via **Node `ws` relay on AWS EC2**, TLS via Caddy | Full control; costs a few dollars of credits (section 14.4) |
| D8 | Distributions: Beta (probabilities), lognormal (durations), Monte Carlo (500 runs) | Simple, explainable, updates with counts |
| D9 | Hierarchical fallback (bucket → parent → prior) | Works with hours of real data instead of weeks |
| D10 | Explanations from templates, no runtime LLM | Fast, private, deterministic |
| D11 | Digest delivery for suppressed notifications, VIPs always allowed | Safety and user trust |
| D12 | E2E encryption on relay is **[STRETCH]** | TLS is the baseline; payloads are already coarse |
| D13 | Laptop (Windows) notifications are **[STRETCH]** | Fiddly; low value for MVP |
| D14 | LLMs are development-time tools (dataset, personas, code assist) | Privacy story stays clean |

---

## 5. System architecture

### 5.1 Diagram

```
┌──────────────── LAPTOP (Windows, Electron + TS) ────────────────┐
│ ActivityWatch (window, afk, web)  ·  VS Code ext [later]        │
│   └─► AW Adapter (poll :5600) ─► Normalizer (categories only)   │
│        ─► SQLite ─► State Inferrer ─► Pet UI + Panel            │
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
│ Ingest → Room DB → Fusion (phone+laptop) → Inference            │
│                         │                                       │
│              ┌──────────▼───────────┐                           │
│              │     TWIN ENGINE      │  pure Kotlin              │
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
2. **Local-first.** Each device works alone; sync only enriches context.
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
│       ├── fusion/           # ContextFuser (phone + laptop → ContextSnapshot)
│       ├── inference/        # FocusStateInferrer, CompulsiveScore, FatigueIndex
│       ├── twin/             # (pure Kotlin, no android.* imports)
│       │   ├── params/       # BetaStat, LogNormalStat, ParamStore, Hierarchy
│       │   ├── episodes/     # EpisodeBuilder, OutcomeWindow, EpisodeCloser
│       │   ├── simulator/    # SemiMarkovSim, MonteCarlo
│       │   ├── counterfactual/ # CounterfactualEngine, ExplorationPolicy, Regret
│       │   └── decision/     # Importance, CostFunction, ActionSelector, Explainer
│       ├── safety/           # NeverHoldRules, KillSwitch, Undo
│       ├── actuator/         # Holder, DigestBuilder, ReleaseScheduler
│       ├── personas/         # PersonaDef, HistoryGenerator, FastForward
│       ├── sync/             # RelayClient, OutboundQueue, Backfill
│       ├── data/             # Room entities + DAOs
│       ├── metrics/          # Calibration, ParamRecovery, SavedMinutes
│       └── ui/               # Simulate, Live, Insights, Digest, Personas, Settings
├── laptop/                   # Electron + TS (electron-vite template)
│   └── src/
│       ├── main/             # app lifecycle, pet window, tray
│       ├── adapters/         # AwAdapter, ActiveWinFallback, VsCodeFeed, BrowserFeed
│       ├── normalizer/       # categories.json, normalize.ts
│       ├── state/            # focusState.ts
│       ├── sync/             # relayClient.ts
│       ├── pet/              # petStateMachine.ts, sprites/, bubbles.ts
│       └── panel/            # React/HTML panel: today, held items, declare session
├── relay/                    # server.js, Caddyfile, deploy.sh
├── vscode-ext/               # [STRETCH]
├── browser-ext/              # [STRETCH]
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
| `notif_posted` | notif | `{key, pkg, appCat, notifCat, senderId(hash), isVip, urgencyScore, hasReplyAction, repeatCount5m}` |
| `notif_removed` | notif | `{key, reason: clicked/dismissed/held/auto}` |
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
| P1 | Collect + normalize | Every raw signal | Schema events in local DB |
| P2 | Stream | Every event, batched ≤2 s | Events on relay, queue + backfill |
| P3 | Fuse + infer | Every 10–30 s and on key events | ContextSnapshot |
| P4 | Episode building | Notification, pickup/app-open, window-leave | Open and closed episodes |
| P5 | Learning | Episode closes | Updated Beta/lognormal params |
| P6 | Decision | `onNotificationPosted` | Action + explanation (<50 ms) |
| P7 | Actuation | Decision | Hold / snooze / digest / release |
| P8 | Feedback | User taps, quick opens | Labels → P5 |
| P9 | Counterfactual accounting | Decision + outcome | Regret, saved minutes, propensities |
| P10 | Insights | Dashboard open | Aggregates |
| P11 | Persona/replay | Demo or test | Synthetic or replayed events through P1–P10 |
| P12 | Mirror | State change | Pet state on laptop, "held" badge |

### 7.1 Episode definition
An **episode** starts at a trigger (notification arrival, or a self-initiated pickup/app-open with no notification) and runs a **30-minute outcome window** (configurable).

```
trigger → frozen ContextSnapshot
        → outcomes within window:
            pickedUp? (t)             first app opened + category + surface
            dwellMs                   time in distracting app(s)
            laptopLeftWork?           window_change away from work category
            resumeDelayMs             time from return to first meaningful edit / activity
            feedback?                 important / noise
        → label: notif_triggered | self_initiated | none
```
Self-initiated episodes establish the **baseline hazard** that suppression cannot remove.

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
FOCUSED ─(notif shown)→ NOTIFIED ─ignore→ FOCUSED
                           │
                         open/pickup
                           ▼
                         IN_APP(cat, surface)  ── dwell D ──►  RESUMING ── delay R ──► FOCUSED
```
Suppression removes `NOTIFIED`. The user may still self-initiate: `FOCUSED → IN_APP` at baseline rate.

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

### 8.7 Monte Carlo simulation
For option `o ∈ {SHOW, SUPPRESS}` and run `i = 1..N`:

```
SHOW:
  p_i  ~ Beta(posterior of p_pickup(x))          # epistemic uncertainty
  picked ~ Bernoulli(p_i)
  if picked:
     (cat,surf) ~ Categorical(Dirichlet sample)
     D ~ LogNormal(m_D, s_D)
     R ~ LogNormal(m_R, s_R)
     L_i = c_glance + D + R
  else:
     L_i = c_glance                                # 0.3–0.5 min
SUPPRESS:
  self ~ Bernoulli(p_self(W))   (λ sampled from Gamma)
  if self: draw D, R from the same baseline distributions as SHOW
          L_i = D + R
  else:   L_i = 0
```

Output per option: mean, p10, p50, p90, `P(L>0)`, and an interval of `p_pickup` itself. **Delta** = `E[L_show] − E[L_suppress]` with its own p10/p90 from paired runs.

Confidence = f(effective sample size of the buckets used): `conf = n_eff / (n_eff + 10)`, reported to the user ("low confidence: 9 observations").

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

### 9.1 Flow

```
onNotificationPosted(sbn):
  1. SafetyRules.neverHold(sbn)? → ALLOW, log, stop
  2. snapshot = Fusion.current()
  3. I = Importance.score(sbn)
  4. if I ≥ 0.85 → ALLOW
  5. sim = Simulator.run(snapshot, sbn)       # SHOW vs SUPPRESS, N=500
  6. J(a) for each action (9.2)
  7. a* = argmin J; apply exploration policy (9.4)
  8. Explainer.build(a*, top factors)
  9. Actuator.apply(a*); Log decision + propensities + predicted outcomes
```

### 9.2 Cost function (units: focus-minutes)
Let `ΔL = E[L_show] − E[L_suppress]` (≥0 typically), `τ_a` expected hold time, `κ_d` = delay harm coefficient (user "caution" slider, default 0.5).

```
J(ALLOW)   = E[L_show]
J(DELAY)   = E[L_suppress] + κ_d · I² · τ_release + c_rel · E[L_show at release]
J(DIGEST)  = E[L_suppress] + κ_d · I² · τ_digest
J(SILENT)  = E[L_suppress] + κ_d · I² · τ_digest + λ_miss · I
```
- `τ_release` from the ReleaseScheduler's prediction of the next low-cost moment (e.g. after commit, idle, task end); `c_rel ≈ 0.3` (released at a cheap moment).
- `τ_digest` = minutes to the next scheduled digest. `λ_miss` = 5 (penalty for a possibly-missed item).
- With `I² `, low-importance items are cheap to hold and high-importance items are expensive to hold; at I→1 ALLOW always wins.
- **Risk-aversion:** when confidence < 0.4 and `I > 0.5`, prefer ALLOW (use p75 of `L_show` only if ALLOW is blocked by strict declared sessions).
- **Declared strict session** (user said "deep work"): multiply `κ_d` by 0.5 (more willing to hold) except for VIP/never-hold.

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
- `onNotificationPosted`: extract `pkg`, `category`, `isOngoing`, `hasReplyAction`, `people` extras if present, hashed sender; keep title/text in memory for scoring.
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

### 11.4 Ingest
- `Normalizer`: raw → schema; drops text. `AppCategorizer`: package → category via bundled map + `app_labels`.
- `ClockOffset`: stores offset to laptop clock from relay ping/pong (14.3).
- All writes batched on a single-thread dispatcher.

### 11.5 Actuator
Two holding mechanisms, chosen per app:
- **A. Cancel + digest:** `cancelNotification(key)`, store content in `held_notifs`, repost as grouped "Dhyan digest" with a content intent that launches the original app (`getLaunchIntentForPackage`). Flexible release times. Loses original actions and exact deep link.
- **B. Snooze:** `snoozeNotification(key, ms)` keeps the original notification and intent but needs a fixed duration, and cannot be released earlier. Use for DELAY with a predicted release within 1–2 h.
Test with WhatsApp, Instagram, Gmail, YouTube on the Reno 14 first; some apps re-post or ignore cancel.
- `DigestBuilder`: groups by sender/app, shows count, importance ordering, and "Show now / Mark important / Noise" actions.
- `ReleaseScheduler`: evaluates release moments (9.6) via WorkManager + live context.
- **Undo**: any held item can be re-posted instantly.

### 11.6 Screens

| Screen | Purpose | Key elements |
|---|---|---|
| **Simulate** (hero) | Pick/enter a notification + context, see two futures | Context chips, SHOW vs SUPPRESS timelines with p10–p90 bands, verdict card, "why" sentence |
| **Live** | Current state | Focus state ring, laptop state, held count, recent decisions with explanations, Show-now/Undo |
| **Insights** | The six learned things | Focus-hour heatmap, interruption triggers, fatigue curve, fragmenting apps, notifications that matter, long-session surfaces, regret/saved minutes |
| **Digest** | Held items | Grouped list, bulk release, feedback buttons |
| **Personas / Demo** | Synthetic mode | Persona picker, fast-forward 14 days, before/after learning, speed control |
| **Twin Lab** | Learning visibility | Param table with Beta curves and counts; "personalization" before/after diff |
| **Settings** | Control | VIP list, never-hold list, caution slider, exploration toggle, kill switch, pairing code, data export/delete |

### 11.7 Room schema (sketch)

```
events(id PK, ts, tsAdj, device, seq, source, type, payloadJson, synthetic)
episodes(id PK, triggerType, triggerKey, startTs, endTs, snapshotJson, outcomeJson, label, closed)
params(key PK, kind[beta|lognorm|dirichlet|gamma], a, b, n, mean, m2, updatedTs)
app_labels(pkg PK, category, userSet)
held_notifs(key PK, pkg, titleEnc?, textEnc?, senderHash, importance, heldTs, releaseTs?, state)
decisions(id PK, ts, notifKey, action, pi, predictedJson, factorsJson, snapshotJson, exploration)
feedback(id PK, ts, refKey, label, source)
vip(senderHash PK, name?, source[contacts|manual])
sync_cursor(device PK, lastSeqAcked, offsetMs)
settings(key PK, value)
```

---

## 12. Laptop app specification

### 12.1 Platform
- Windows 10/11, Electron + TypeScript (electron-vite template), `better-sqlite3`, `ws`, React (panel) or plain HTML.

### 12.2 Collection backbone: ActivityWatch (decision D6)
**Decision:** use **ActivityWatch** (open source, credit it in README/slides) as the collection backbone for the MVP, behind an `Adapter` interface:

```ts
interface LaptopAdapter {
  start(): void;
  onRaw(cb: (e: RawLaptopEvent) => void): void;
}
```
- `AwAdapter` polls `http://localhost:5600/api/0/` buckets: `aw-watcher-window_<host>`, `aw-watcher-afk_<host>`, `aw-watcher-web-*` (if installed), and optionally `aw-watcher-vscode`.
- Reasons: logging of real data can start in ~10 minutes with zero code (the data clock matters), window/afk/web watchers exist and are maintained, and it removes a day of collector work.
- Limits: titles/URLs are stored by AW locally (our normalizer must never forward them), no scroll/video signals, no fine VS Code edit-rate.
- **Fallback / replacement path:** `ActiveWinFallback` (`active-win` + Electron `powerMonitor.getSystemIdleTime()`) is ~40 lines and can replace AW for window/idle if AW misbehaves.
- Custom `browser-ext` and `vscode-ext` are only for signals AW lacks (scroll, video state, edit bursts) and are **[STRETCH]**.
- Verify AW's current API and bucket names at build time.

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

### 12.5 Pet "Pip"
- Electron `BrowserWindow`: `transparent:true, frame:false, alwaysOnTop:true, skipTaskbar:true, resizable:false`, `setIgnoreMouseEvents(true,{forward:true})` with a hit region on the pet; draggable; position persisted.
- Always-on-top `'screen-saver'` level to show over most windows. Exclusive-fullscreen games hide it; borderless works. For those, the phone is the notifier.
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

### 12.6 Optional: Windows notifications **[STRETCH]**
Windows has a notification-listener API reachable via native modules; fiddly. Skip unless MVP is done.

---

## 13. Extensions **[STRETCH]**

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
5. **[STRETCH]** Derive an AES-GCM key from the code (PBKDF2) and encrypt payloads end-to-end; the relay then sees only ciphertext.

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

### 14.4 Relay on AWS

**Instance:** one small EC2 (Graviton `t4g.micro`/`small`) in `ap-south-1` (Mumbai) for latency to India, Ubuntu LTS.
**Software:** Node 20 + `ws` server (~100 lines) under `systemd`; **Caddy** as the TLS reverse proxy (Android blocks cleartext by default, so `wss` is required).
**Hostname for TLS without buying a domain:** use a wildcard-DNS name such as `<ELASTIC-IP-with-dashes>.sslip.io`; Caddy obtains a Let's Encrypt cert automatically. (Or a cheap/own domain if you have one.)
**Security group:** 443 inbound from anywhere; 22 inbound from your IP only.
**Steps (deploy.sh outline):**
1. Launch instance, attach an Elastic IP.
2. `sudo apt install -y nodejs npm caddy` (or NodeSource Node 20).
3. Copy `relay/` and `npm i`.
4. `systemd` unit running `node server.js` on 127.0.0.1:8080.
5. `Caddyfile`: `<host>.sslip.io { reverse_proxy 127.0.0.1:8080 }`.
6. Test with `wscat -c wss://<host>/ws?...`.

**Cost estimate (approximate, verify current pricing in your region):**

| Item | Approx. |
|---|---|
| t4g.micro, 24×7 | ~US$0.01/hr ≈ $7–9/month |
| Public IPv4 / Elastic IP | ≈ $0.005/hr ≈ $3.6/month |
| EBS gp3 ~10–20 GB | ≈ $1–2/month |
| Data transfer | MBs per day, within the free allowance |
| TLS (Let's Encrypt), sslip.io | $0 |
| **Event week (~10 days incl. testing)** | **≈ $4–7** |
| **Full month** | **≈ $12–15** |
| **Worst case with extras** (second test instance, CloudWatch logs, experiments) | **< $40** |

That is **under 10% of a $400 credit**, assuming the credit is USD. Confirm that, plus the credit expiry date, in the Billing console. Set an **AWS Budgets alert at $20**, and **stop/terminate** the instance after the event. Avoid an Application Load Balancer (~$16+/month), NAT Gateway (~$30+/month) and managed WebSocket stacks that add cost without benefit.
Non-AWS costs: dataset generation with the OpenAI API is likely US$2–5.

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
| **Kabir**, gamer-dev | High self-initiated pickups, low notification effect | Shows the twin learns that suppression helps little for him (honest counterfactual) |

### 15.3 History generator
Simulates 14 days of events with the same schema, `synthetic:true`, including cross-device events (laptop work, phone pickups, second-screen overlap). Uses the persona's hidden parameters, so counterfactual truth is known (for 10.4).

### 15.4 LLM-assisted dataset (development-time, OpenAI/Gemini)
- Generate ~2,000 labeled notification samples (EN + Hinglish): `urgent / important / normal / engagement_bait / promo`, with sender types.
- Use it to set the urgency keyword list and to evaluate the importance heuristic; optionally train a tiny on-device classifier (TFLite/logistic) as a stretch.
- No real user text is sent to any cloud LLM.

### 15.5 Demo fast-forward
Persona switch → "Train 14 days" runs P11 at ~100× with a progress animation; the Twin Lab screen shows Beta curves tightening and the prediction updating.

---

## 16. Real-data logging plan (start immediately, before kickoff if the rules allow)

| When | Action |
|---|---|
| Now | Install **ActivityWatch** on the laptop (window + afk + web watchers); leave running. Verify at `http://localhost:5600`. |
| Now | Install **aw-watcher-vscode** (via the VS Code marketplace) if available. |
| First phone milestone | Build the minimal Android logger (Notif + Usage + Screen collectors → Room), install on the Reno 14, apply the Oppo background settings, and let it run all day. |
| Each evening | Export both logs; verify no gaps; check privacy (no titles/text in synthetic or sync paths). |
| At demo | Show the real-data twin alongside persona twins; say honestly that real data is hours-to-days old. |

The data clock matters: every hour earlier adds real episodes.

---

## 17. Build plan and cut lines

> **[ASSUMPTION]** The 24-hour clock starts at kickoff on 25 Oct. Pre-event work (logging, relay deploy, dataset generation, personas) is only done if the rules permit; if not, the plan below simply shifts those items into hours 0–4.

### 17.1 Phases

| Hours | Phase | Deliverable |
|---|---|---|
| **Pre** | Setup | AW logging, AWS relay up, persona JSON, notification dataset, Android Studio and Oppo dev mode ready |
| 0–2 | Foundations | Project scaffold, permissions flow, notif listener working on the Reno 14, Room schema |
| 2–5 | Data + personas | Normalizer, event schema, persona generator, replay tool |
| 5–10 | Twin core | Param store (Beta/LN), episode builder, simulator, Monte Carlo, unit tests |
| 10–13 | Decision | Importance, cost function, safety rules, explainer, exploration log |
| 13–17 | Phone UI | Simulate screen with timelines (hero), Live, Digest, Settings |
| 17–19 | Laptop app + pet | Electron shell, AW adapter, state rules, Pip window, panel |
| 19–21 | Sync | Relay client, pairing, events + state + pet_event, backfill |
| 21–22.5 | Insights + validation | Insights screens, metrics (calibration, recovery, saved minutes) |
| 22.5–23.5 | Polish | Twin Lab, demo mode, error states, battery check |
| 23.5–24 | Buffer | Rehearsal, backup screen recording, slides |

### 17.2 Cut order (cut from the bottom first)
1. E2E encryption (already stretch) · 2. Windows notifications · 3. Browser extension · 4. VS Code extension (keep AW's vscode watcher) · 5. Twin Lab polish · 6. Insights beyond 3 charts · 7. Laptop pet animations beyond 4 states · 8. Cross-device sync (pet runs on laptop state alone; phone twin uses phone-only context).
**Never cut:** Simulate screen, SHOW/SUPPRESS Monte Carlo with uncertainty, safety rules, explanation, persona demo, one real-device hold-and-digest working end to end.

### 17.3 Gate checks
- **Hour 5:** persona data replays through the pipeline and produces episodes.
- **Hour 10:** engine returns SHOW/SUPPRESS distributions for a hard-coded snapshot.
- **Hour 14:** a real notification on the Reno 14 is held and appears in the digest.
- **Hour 19:** Pip reacts to a real laptop state change.
- **Hour 21:** phone and laptop show the same focus state via the relay.
If a gate slips by more than 90 min, apply the next cut.

---

## 18. Testing and metrics

### 18.1 Unit tests (pure Kotlin `twin/`)
- Beta update and shrinkage, lognormal Welford, Dirichlet sampling (seeded RNG).
- Simulator reproducibility given seed; distributions match analytic expectation within tolerance.
- Cost function chooses ALLOW as `I→1`; DIGEST for low I and high ΔL.
- Never-hold rules (calls, OTP, VIP) can never be overridden by the cost function.
- Temporal-isolation test (10.5).

### 18.2 Integration tests
- Persona replay → episodes count and labels sensible.
- Relay: reconnect + backfill without duplicates; clock offset sign convention.
- Oppo soak test: 2 hours screen-off, check collector gaps.

### 18.3 Headline metrics (for slides)
Parameter recovery error, calibration (Brier/ECE), minutes saved per day vs urgent items delayed (= 0), counterfactual error vs ground truth, decision latency (target < 50 ms), battery impact (target < 3%/day extra, measure it).

---

## 19. Demo script (live on own devices, ~4–5 min)

1. **Hook (20 s):** "A single 'like' notification costs this developer 27 minutes. Dhyan sees it before it happens."
2. **Meet Pip (20 s):** the laptop pet sleeping while VS Code is open; phone Live screen shows the same focus state (sync).
3. **The hero scenario (60 s):** choose Rahul, coding, 4 hrs in. Fire "Someone liked your post". The two timelines appear: SHOW 24 min (8–41) vs SUPPRESS 2 min. Verdict: **Digest**, with the one-line reason. Pip perks up: "1 held."
4. **The twin is not a blocker (30 s):** fire "Mom: call me, urgent". It goes through, with the reason ("high importance, family").
5. **Counterfactual (30 s):** open a past held item: "If shown: ~21 min lost (est.); exploration data says…" and the regret score.
6. **It learns (45 s):** tap "that was important" on a held item; show the Beta curve shift and the next prediction change in Twin Lab.
7. **Intentionality (30 s):** Anika's watch-page lecture, not flagged; Shorts, flagged with "Study or break?"
8. **Real data (30 s):** the twin on today's real phone and laptop logs, plus an honest "low confidence, 14 observations".
9. **Close (20 s):** privacy slide (only categories and counts leave the device), metrics slide, safety (never holds calls/OTPs, kill switch).

**Backup:** a screen recording of steps 2–7; a "simulated sync" toggle; spare phone hotspot.

---

## 20. Pitch Q&A (prepared answers)

1. **Why isn't this a Focus-mode app?** Rules block by category; Dhyan predicts the consequence for *this person* in *this context* and trades it against importance.
2. **How do you get counterfactuals?** Model-based simulation, logged propensities, and 5% randomized exploration; validated on personas where the truth is known. Assumptions stated.
3. **Isn't synthetic data cheating?** It is how you validate counterfactuals; real data is shown alongside, and we say how old it is.
4. **Privacy?** Categories and counts only, on-device models, hashed senders, nothing text-like leaves the phone.
5. **What if it hides something important?** Never-hold list, VIPs, importance override, undo, kill switch, and fail-open.
6. **Is long YouTube time penalized?** No. Intentionality is scored from switching, surface, engagement and the other device, and the user can declare intent.
7. **Why not a neural net?** Hours of data; interpretable Bayesian counts update instantly and show uncertainty.
8. **Accessibility service creepy?** It subscribes to scroll events only, never reads text, only for an allow-list of apps, and is optional with a fallback.
9. **What if the laptop is off?** The phone twin still works with lower confidence, and says so.
10. **What next?** Calendar-aware context, on-device federated personalization, mobile↔desktop wellbeing coaching, and a small learned model once data accumulates.

---

## 21. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| ColorOS kills background services | Data gaps, broken holds | Foreground service, auto-start, battery exemption, rebind, gap marking, soak test early |
| Android restricted settings block toggles (sideloaded app) | Can't enable listener/a11y | "Allow restricted settings" step in the guide |
| Some apps re-post cancelled notifications | Holds fail | Per-app mechanism A/B, test top apps, fail-open |
| Accessibility API surface IDs change | Surface detection fails | JSON config + other signals; treat as weak signal |
| Kotlin learning curve | Slow UI work | Use AI scaffolding; build in small increments; keep the engine plain Kotlin |
| Sync failure at demo | Embarrassment | Simulated-sync toggle, recorded backup, phone hotspot |
| Statistics bugs | Wrong claims | Seeded unit tests, replay against known persona truth |
| Scope creep (pet, extensions) | No finished core | Gates + cut lines in section 17 |
| ActivityWatch breaking | No laptop signal | `ActiveWinFallback` adapter ready |
| Privacy mistake (titles leaking) | Trust | Normalizer is the only exit path; test asserts no title/URL fields in payloads |
| Battery drain | Judges notice | Poll intervals (15–30 s), batch writes, measure |

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
3. `npm create @quick-start/electron` (electron-vite) with TypeScript; add `better-sqlite3`, `ws`.
4. Run `aw-watcher-web` extension (optional).
5. Windows: if the pet hides behind games, use borderless windowed mode.

### 22.3 AWS relay
Follow section 14.4. Add a Budgets alert at $20. Tag resources `project=dhyan`. Terminate after the event.

### 22.4 Android Studio
- Latest stable Android Studio, one Pixel emulator for UI/engine work, the Reno 14 for everything involving notifications/usage/accessibility.

---

## 23. AI-assist playbook (ChatGPT / Gemini / Antigravity)

- Always paste: section 6 (schema), the relevant module spec, and the repo layout (5.3).
- Ask for **one file/class at a time** with tests; build and run after each.
- Suggested prompts (copy and adapt):
  - *"Write pure-Kotlin `BetaStat` and `LogNormalStat` classes per section 8.4 with Welford updates, seeded sampling and unit tests."*
  - *"Write `SemiMarkovSim` per 8.7 producing mean/p10/p50/p90/P(L>0) for SHOW and SUPPRESS with N=500."*
  - *"Write a `NotificationListenerService` per 11.3 that emits `notif_posted` events without storing text, with `requestRebind` handling."*
  - *"Write the Electron pet window per 12.5 (transparent, click-through, draggable) with a state machine for the 6 states."*
  - *"Write a Node `ws` relay per 14.3: rooms keyed by hash, max 2 clients, in-memory, rate-limited, ping/pong."*
  - *"Write a persona history generator per 15.3 that emits schema events using these hidden parameters."*
- Have a second AI review each module against this spec (privacy, safety, seeded determinism).
- Do **not** paste real notification text, window titles or personal data into any AI tool.

---

## 24. Assumptions and open items

**Assumptions I made (change them freely):**
1. **[ASSUMPTION]** The event clock starts at kickoff; pre-event logging and tooling setup are allowed if the rules permit. Not confirmed, please check the official rules about pre-built tools, pre-written code, and open-source use (ActivityWatch must be credited).
2. **[ASSUMPTION]** AWS credits are USD; confirm balance and expiry.
3. **[ASSUMPTION]** Laptop is Windows 10/11 with Chrome or Edge; phone is the Oppo Reno 14 on ColorOS.
4. **[ASSUMPTION]** Outcome window 30 min; exploration ε = 5%; both configurable.
5. **[ASSUMPTION]** Digest delivery with the "cancel + repost" mechanism as the default; snooze used when predicted release is within 1–2 h.
6. **[ASSUMPTION]** Judges are a mixed panel (technical + product).

**Still to verify at build time:**
- Current ActivityWatch API/bucket names; current AWS pricing in `ap-south-1`.
- Whether Oppo ColorOS lets `cancelNotification` work for each target app.
- Foreground service type requirements for your target SDK.
- Final judging criteria and demo format.

---

## Appendix A: Default parameters (single place to tune)

| Name | Default |
|---|---|
| Monte Carlo runs N | 500 |
| Outcome window W | 30 min |
| Hierarchy strength κ | 5 |
| Beta recency decay γ | 0.995/episode |
| Lognormal prior strength n0 | 3 |
| σ floor | 0.25 |
| Delay harm κ_d | 0.5 |
| Miss penalty λ_miss | 5 |
| Release discount c_rel | 0.3 |
| Glance cost c_glance | 0.4 min |
| Always-allow importance threshold | 0.85 |
| Exploration ε | 0.05 (I < 0.4) |
| Low-confidence threshold | 0.4 |
| Compulsive-score thresholds | 0.35 / 0.65 |
| State heartbeat | 5 s |
| Context snapshot interval | 10–30 s |
| Digest interval | 120 min |
| Relay reconnect backoff | 1 s → 30 s |

## Appendix B: Glossary
- **Episode:** a trigger plus its observed outcomes over the outcome window.
- **Counterfactual:** the estimated outcome of the action not taken.
- **Propensity (π):** probability that the logged action was chosen in that context.
- **Second-screen overlap:** phone use while the laptop is in a work state.
- **Resumption delay:** time from returning to a work app to the first meaningful activity.
- **Hierarchical backoff:** using a broader bucket's statistics when the specific bucket has little data.
