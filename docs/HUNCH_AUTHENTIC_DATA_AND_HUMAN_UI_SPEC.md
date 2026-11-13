# Hunch Attention Twin — Authentic Data Pipeline & Human-First UI Specification

**Repository:** `https://github.com/abhigyan24-ops/hunch-attention-twin`  
**Components:** `engine/` (Python FastAPI Sidecar & Causal Twin), `laptop/` (Electron Dashboard & PiP), `phone/` (Android Companion), `vscode-ext/` (VS Code Sensor & Companion)  
**Version:** 2.0  
**Status:** Implemented  

---

## 1. Core Product Philosophy: Invisible Math, Visible Value

A real user does not care about hazard rate equations, propensity weights, Dirichlet distributions, Kalman gains, WebSocket acknowledgments, or telemetry schema versions. A real user cares about three concrete, life-impacting questions:
1. **When am I most likely to get distracted today, and how do I block it?**
2. **When is my peak focus window based on my real sleep and past rhythm?**
3. **What was I doing right before I got interrupted?**

All mathematical modeling, survival analysis, empirical Bayes shrinkage, and event streaming run **silently in the background**. The user-facing interfaces across Laptop, Phone, and VS Code expose only plain-English predictions and 1-tap actions.

---

## 2. UI Translation Matrix (What to Hide vs. What to Show)

| Component | Hide / Remove from User UI | Show Instead (Plain Human Language) |
| :--- | :--- | :--- |
| **Distraction Predictor** | Raw hazard rate, probability floats (`0.82`), feature vectors, model weights | **"Likely distraction around 3:15 PM"** + *"You usually switch apps after 26 mins at this hour."* |
| **Focus Timer** | Static hardcoded timers, lease IDs, bandit propensity values | **Adaptive Focus Button:** *"Start 25m Focus (Matched to your 5.5h sleep & current streak)"* |
| **Phone Dashboard** | Raw event counters, JSON payloads, WebSocket ports, service class names | **"Protected 42m of Deep Work today"** + **"3 distracting alerts held for your next break"** |
| **VS Code Sidebar** | `ed_summary` counts, batch sequence numbers, endpoint paths | **Today's Flow**, **Next Vulnerable Window**, **Where I Left Off**, and **Held for You** |
| **Cold Start (Day 1)** | Fake/mocked demo charts or synthetic telemetry numbers | **"Learning your rhythm (14 / 30 mins of real activity recorded)"** |

---

## 3. 100% Authentic SQLite Storage & Closed-Loop Telemetry Policy

### 3.1 Zero Synthetic Data Policy
- Synthetic data generators, mock seeding scripts, and demo hardcoded counters are strictly eliminated from production UI code paths.
- All persisted state lives in a real local SQLite database (`~/.hunch/hunch.db` on laptop/desktop, configurable via `HUNCH_DB_PATH` or `DHYAN_DB_PATH`; local SQLite/Room on Android).
- Every database table and column must have an active **Producer** (real hardware/system sensor) and an active **Consumer** (prediction algorithm or UI action).

### 3.2 Closed-Loop SQLite Schema

#### 1. `real_activity_buckets` (5-Minute Real Telemetry Windows)
- **Producer:** Real sensor inputs from `vscode-ext` (active keystrokes, editor focus/blur, file switches), `laptop` (foreground window transitions via active-win/system events), and `phone` (`UsageStatsManager` foreground app transitions and screen on/off events).
- **Columns:**
  - `bucket_start_ts` (INTEGER PRIMARY KEY): Epoch millisecond timestamp of the 5-minute bucket window start.
  - `hour_of_day` (INTEGER NOT NULL): 0–23 representing the local hour.
  - `day_of_week` (INTEGER NOT NULL): 0–6 representing day of the week (Sunday = 0).
  - `coding_active_seconds` (INTEGER DEFAULT 0): Measured seconds of active coding, writing, or reading within the 5m window.
  - `app_switch_count` (INTEGER DEFAULT 0): Count of foreground application or tab switches within the window.
  - `phone_unlock_count` (INTEGER DEFAULT 0): Count of phone screen unlocks recorded in this window.
  - `notifications_held_count` (INTEGER DEFAULT 0): Number of distracting notifications held during this window.
  - `was_distracted` (INTEGER DEFAULT 0): Boolean indicator (0 or 1) set if user experienced rapid non-work app thrashing or prolonged idle diversion.
- **Consumer (`DistractionPredictor.predict_today_windows()`):**
  - Evaluates rolling historical buckets to discover the user's **Peak Deep-Work Hours** (highest historical ratio of `coding_active_seconds` to interruptions) and **High-Risk Distraction Hours** (hours where `was_distracted` and `phone_unlock_count` consistently peak).

#### 2. `focus_streaks` (Real Uninterrupted Attention Spans)
- **Producer:** Recorded automatically whenever the user sustains continuous work in VS Code / Laptop until an interruption occurs (phone pickup > 45s, social media/doomscroll switch, or > 5m unplanned idle gap).
- **Columns:**
  - `streak_id` (TEXT PRIMARY KEY): Unique UUID for the streak.
  - `started_at_ts` (INTEGER NOT NULL): Start timestamp in epoch milliseconds.
  - `ended_at_ts` (INTEGER NOT NULL): End timestamp in epoch milliseconds.
  - `duration_minutes` (INTEGER NOT NULL): Duration in full minutes.
  - `hour_of_day` (INTEGER NOT NULL): Hour the streak started (0–23).
  - `broke_by_source` (TEXT NOT NULL): Cause of streak termination (`"phone_pickup"`, `"app_switch"`, `"notification"`, `"natural_break"`).
- **Consumer (`DistractionPredictor.predict_minutes_until_distraction()`):**
  - Computes the user's real rolling median streak length (`typicalStreakMinutes`).
  - Powers **Pre-Distraction Shielding**: Activates notification holding and sends a quiet micro-prompt 3 minutes *before* the user's predicted failure point (`minutesUntilLikelyDistraction <= 3`).

#### 3. `context_snapshots` (Real Interruption Recovery)
- **Producer:** Automatically captured when an active focus streak breaks, when pre-bedtime wind-down triggers, or when the user invokes "Park Intention".
- **Columns:**
  - `snapshot_id` (TEXT PRIMARY KEY): Unique UUID for the snapshot.
  - `created_at_ts` (INTEGER NOT NULL): Timestamp when context was parked.
  - `workspace_name` (TEXT): Name of the active repository or workspace folder.
  - `file_path` (TEXT): Relative or absolute path to the active file.
  - `line_number` (INTEGER DEFAULT 0): 0-indexed line number of the user's cursor.
  - `column_number` (INTEGER DEFAULT 0): 0-indexed column position.
  - `git_branch` (TEXT): Active git branch name.
  - `user_note` (TEXT): Optional user intention note or next-step reminder.
  - `resumed_at_ts` (INTEGER): Timestamp when the user clicked "Where I Left Off" to resume.
- **Consumer:**
  - Drives the 1-click **"Where I Left Off"** card in VS Code and Laptop UI.
  - Quantifies real recovery time saved (`resumed_at_ts - return_to_desk_ts`).

#### 4. `held_notifications` (Real Triage & Break Digest)
- **Producer:** `NotificationListenerService` and `NotificationTriageEngine` on Android during active focus sessions.
- **Columns:**
  - `id` (TEXT PRIMARY KEY): Unique notification hash ID.
  - `arrived_at_ts` (INTEGER NOT NULL): Timestamp when intercepted.
  - `app_label` (TEXT NOT NULL): Display name of the sending app (e.g. "Slack", "Instagram"). Never stores raw message body or sensitive text.
  - `category` (TEXT): System category string.
  - `count` (INTEGER DEFAULT 1): Count of alerts aggregated under this app.
  - `delivered_at_break` (INTEGER DEFAULT 0): 1 when released to the user upon break.
- **Consumer:**
  - Powers the **"Held for You"** break digest in Laptop, VS Code, and Phone.

---

## 4. Human-Friendly Prediction Engine API (`GET /v1/insights/human-summary`)

The Python engine sidecar exposes a consolidated human-facing endpoint consumed by Laptop, Phone, and VS Code:

```http
GET /v1/insights/human-summary?current_streak_minutes=19
```

### JSON Response Schema:
```json
{
  "calibrationStatus": {
    "isCalibrated": true,
    "realMinutesTracked": 412,
    "minMinutesRequired": 30,
    "statusLabel": "Personalized to your rhythm"
  },
  "distractionForecast": {
    "typicalStreakMinutes": 28,
    "currentStreakMinutes": 19,
    "minutesUntilLikelyDistraction": 9,
    "preDistractionShieldTrigger": false,
    "nextHighRiskWindow": "3:00 PM – 4:00 PM",
    "primaryTrigger": "Phone pickups",
    "humanHeadline": "You usually get distracted around minute 28 (~9 mins from now).",
    "humanActionLabel": "Turn on Quiet Shield for 15 mins"
  },
  "peakFocusWindow": {
    "windowLabel": "10:30 AM – 12:30 PM",
    "humanHeadline": "Your deepest focus usually happens between 10:30 AM and 12:30 PM."
  },
  "todayImpact": {
    "deepWorkMinutes": 94,
    "interruptionsBlocked": 7,
    "recommendedSessionMinutes": 25,
    "sessionReason": "Adjusted for 5h 45m sleep last night"
  }
}
```

---

## 5. Client Integration Architecture

### 5.1 Laptop Companion (`laptop/`)
- **Distraction Forecast Card**: Displays `distractionForecast.humanHeadline` and a 1-click button to activate Quiet Shield.
- **Peak Focus Card**: Surfaces `peakFocusWindow.humanHeadline`.
- **Adaptive Focus Button**: Sets planned focus minutes directly to `todayImpact.recommendedSessionMinutes` (matched to sleep & streak rhythm).
- **Calibration State**: When `isCalibrated === false`, displays honest progress: `"Learning your natural focus rhythm (X / 30 mins of real activity)"`.

### 5.2 VS Code Extension (`vscode-ext/`)
- **"Today" Tree View**: Surfaces plain-English distraction predictions, next high-risk hour, and deep work totals.
- **"Where I Left Off" View**: 1-click restore to line/file from `context_snapshots`.
- **Pre-Distraction Status Bar**: When `preDistractionShieldTrigger === true`, status bar subtly warns: `"Distraction wall in ~3m — stay in flow!"`.

### 5.3 Android Companion (`phone/`)
- **Distraction & Shield Dashboard**: Plain-English distraction prediction based on real phone unlocks and app usage.
- **Bedtime & Sleep Sync**: Synchronized with Digital Wellbeing `BedtimeManager`.
- **Privacy Ledger**: Real-time counter of held alerts and 1-tap "Forget Today's Data" action.
- **Connection Settings**: Relocated into a clean collapsible sheet, keeping technical IP/port fields hidden from daily use.
