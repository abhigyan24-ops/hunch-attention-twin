# Dhyan Desktop App & VS Code Extension — UI/UX Architecture & Redesign Spec

**Status:** IMPLEMENTED & VERIFIED  
**Platforms:** Electron Desktop App (`laptop/`) & Visual Studio Code Extension (`vscode-ext/`)  
**Design Paradigm:** Airy Daylight Pastel + Adaptive Dark Glass Bento System  
**Companion Entity:** Frosted 3D Cloud Spirit Pet (Tactile Canvas with Spring Physics & Particle System)

---

## 1. Executive Summary & Cross-Platform Visual Parity

To establish complete visual cohesion across the Dhyan ecosystem (Android Mobile App, Desktop Workstation App, and VS Code IDE Extension), the Desktop App and VS Code Extension share the exact same aesthetic primitives, typography hierarchy, category colors, and companion pet behavior:

* **Bento Card Geometry:**
  * Curvature: Large `24px` squircle border radius (`--dhyan-radius-lg: 24px`) for hero containers and `18px` (`--dhyan-radius-md: 18px`) for metric tiles.
  * Border Definition: Subtle `1px solid var(--dhyan-border)` (Daylight: `#EAEEF4`, Dark: `rgba(255, 255, 255, 0.08)`).
  * Elevation: Soft ambient drop shadows (`0 8px 24px rgba(15, 23, 42, 0.04)` in daylight; `0 8px 24px rgba(0, 0, 0, 0.25)` in dark mode).

* **Dual Adaptive Palette & Pastel Category Tokens:**
  * **Daylight Focus Mode (Default Desktop Canvas):**
    * `CanvasBackground`: `#F7F8FA` (Warm editorial off-white)
    * `BentoSurface`: `#FFFFFF`
    * `TextMain`: `#121417` (Deep charcoal)
    * `TextMuted`: `#64748B` (Slate)
  * **IDE-Integrated Glass Mode (VS Code Webview):**
    * Dynamically bridges VS Code theme CSS variables (`--vscode-sideBar-background`, `--vscode-editor-background`) mixed with translucent pastel glass (`color-mix(in srgb, var(--vscode-editor-background) 94%, #000000 6%)`), ensuring natural contrast in both Light Modern and Dark+ / Tokyo Night themes.
  * **Pastel Semantic Category Accents:**
    * **Sage (`#E8F3E8`):** Surface `#E8F3E8` | Accent `#7AD656` | Text `#1E5631` — *Coding / Active Focus / Streak*
    * **Lavender (`#E9EAF8`):** Surface `#E9EAF8` | Accent `#6366F1` | Text `#312E81` — *Deep Study / Debug / Flow*
    * **Warm Sand (`#EFEBE4`):** Surface `#EFEBE4` | Accent `#D97706` | Text `#78350F` — *Reading / Paper Review / Research*
    * **Sky Mint (`#E3F4F4`):** Surface `#E3F4F4` | Accent `#38BDF8` | Text `#0C4A6E` — *Calm Break / Attention Reset*

---

## 2. Desktop App UI Architecture (`laptop/`)

### 2.1 3-Zone Bento Workspace Layout (`1280px` Grid)

The Desktop App uses a responsive, human-ergonomic 3-Zone Bento Grid layout:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  ● Dhyan v1.1.0                          [Connected ●]            [Compact Pet HUD ↘]  │
├─────────┬──────────────────────────────────────────┬───────────────────────────────────┤
│ [RAIL]  │ [CENTER HERO STAGE - 55%]                │ [LIVE INSIGHTS & DOCK - 45%]      │
│         │                                          │                                   │
│  (●)    │ ┌──────────────────────────────────────┐ │ ┌───────────────────────────────┐ │
│  Live   │ │ FROSTED CLOUD SPIRIT COMPANION       │ │ │ WEEKLY ATTENTION FLOW [84%]   │ │
│         │ │  (•‿•)  "Watching your code flow"   │ │ │  6d streak · Ring Progress    │ │
│  (⏱)    │ └──────────────────────────────────────┘ │ └───────────────────────────────┘ │
│  Timer  │                                          │                                   │
│         │ ┌──────────────────────────────────────┐ │ ┌───────────────┬───────────────┐ │
│  (▦)    │ │ one. two. focus!                     │ │ │ TODAY OBSERVED│ STABILITY WAVE│ │
│  Stats  │ │ ┌──────────────┬───────────────────┐ │ │ │  45m [~~~~~]  │  92% [~~~~~]  │ │
│         │ │ │ 💻 Coding 45m│ ⚡ Deep Study 25m │ │ │ ├───────────────┼───────────────┤ │
│  (⚙)    │ │ ├──────────────┼───────────────────┤ │ │ │ INGEST EVENTS │ HELD ON PHONE │ │
│  Sync   │ │ │ 📖 Reading15m│ 🍵 Calm Break 10m │ │ │ │  128 [JSONL]  │  0 [Clear]    │ │
│         │ │ └──────────────┴───────────────────┘ │ │ └───────────────┴───────────────┘ │
│    │    │ │                                    │ │                                   │
│    │    │ │      ╭──────────────╮              │ │ ┌───────────────────────────────┐ │
│    ▼    │ │     │      25        │  (Dial)     │ │ │ 7-DAY FOCUS DISTRIBUTION      │ │
│   [●]   │ │      ╰──────────────╯              │ │ │  45% 65% 80% 55% 92% 35% 40%  │ │
│ Online  │ │ [Start Session on Phone & Desktop] │ │ │  M   T   W   T   F   S   S   │ │
│         │ └──────────────────────────────────────┘ │ └───────────────────────────────┘ │
└─────────┴──────────────────────────────────────────┴───────────────────────────────────┘
```

#### Zone 1: Left Floating Capsule Rail (`76px` width)
* Vertical pill container with `24px` squircle corners, soft shadow, and animated active indicator.
* Tab triggers:
  * `Live`: Main real-time focus & companion workspace.
  * `Timer`: Semicircular dial & session declaration.
  * `Stats`: Analytics, telemetry cadence, and sparklines.
  * `Sync`: Cryptographic pairing, relay endpoints, and token configuration.
* Bottom Status Dot: Pulsing mint green when connected to phone relay (`ws://127.0.0.1:8080/ws`).

#### Zone 2: Center Hero Stage (`55%` width)
* **Card A — Frosted Cloud Spirit Companion Stage:**
  * Dedicated stage rendering `CloudSpiritCanvas` with live mood pill (`● FOCUSED (Coding)` / `● HAPPY` / `● SLEEPY`).
  * Features tactile petting, squash-and-stretch spring physics, and dynamic speech bubbles (`"Quietly guarding your focus."`).
* **Card B — Session Timer & 2x2 Squircle Presets:**
  * Editorial typography: *"one. two. **focus!***"*
  * **2x2 Pastel Squircle Preset Grid:**
    * 💻 **Coding (45m):** Sage `#E8F3E8`, high distraction protection.
    * ⚡ **Deep Study (25m):** Lavender `#E9EAF8`, Pomodoro flow sprint.
    * 📖 **Reading (15m):** Warm Sand `#EFEBE4`, documentation review.
    * 🍵 **Calm Break (10m):** Sky Mint `#E3F4F4`, attention reset & rest.
  * **Tactile Semicircular Tick-Mark Dial:**
    * 25 precision tick marks arranged over a 180° semicircular arc.
    * Large bold `68px` numeric readout (`25`) with `"Minutes Planned"`.
    * Interactive Scrubbing: Supports click-dragging, mouse wheel scrubbing ($\pm 5\text{m}$), and keyboard arrow keys.
  * **High-Contrast CTA Action Pill:**
    * Full-width charcoal `#121417` button with tactile press depth.
    * Connected state activates cross-device command: `Start Session on Phone & Desktop 🚀`.

#### Zone 3: Live Insights & Companion Dock (`45%` width)
* **Hero Weekly Progress Banner:** Soft lime-mint card (`#DDF7C2`) with circular SVG progress ring (`84%`), `6 days streak`, and adherence statistics.
* **2-Column Mini Sparkline Cards:**
  * *Today's Observed:* Minutes recorded today + smooth SVG wave chart.
  * *Stability Wave:* 92% Flow score + lavender waveform sparkline.
  * *Events Ingested:* Real-time local JSONL pipeline event count.
  * *Held on Phone:* Current held notifications count + 1-click `Release All` button.
* **7-Day Horizontal Pill Strip + Rounded Vertical Bar Chart:**
  * Mon–Sun vertical pill columns with percentage values perched above (`45%`, `65%`, `80%`, `55%`, `92%`, `35%`, `40%`).
  * Active today column highlighted in glowing mint gradient.
* **Remote Phone Held Items Card:**
  * Displays held notifications categorized by app badge with individual `Show now` manual release triggers.

### 2.2 Compact Mini-HUD Mode & Desktop Pet Corner (`170px × 130px`)
* Toggleable via the top header bar (`Compact Pet HUD ↘`).
* Window shrinks into a transparent, always-on-top companion widget that can sit unobtrusively over IDEs or browsers.
* Includes hit-test mouse event forwarding (`window.dhyan.setIgnoreMouseEvents`) so background code editors remain clickable around the pet.

---

## 3. VS Code Extension Architecture (`vscode-ext/`)

### 3.1 Three-Surface Integration Model

The VS Code Extension embeds seamlessly into developer workflow across three native surfaces:

1. **Surface 1: Status Bar Focus Pill (`vscode.window.createStatusBarItem`)**
   * Pinned to the bottom status bar: `$(pulse) Dhyan: 24:15 • Focused (92%)`.
   * Click trigger opens the companion sidebar panel or toggles the session timer.

2. **Surface 2: Sidebar Webview Companion Panel (`dhyan.companion` in Activity Bar)**
   * Single-column vertical Bento layout optimized for `240px–340px` sidebar widths.
   * **Card 1 — Frosted Cloud Companion Stage:**
     * Embedded `<canvas>` rendering the Cloud Spirit with real-time IDE typing saccades.
     * Status pill: `Connected ●` with pulsing glow.
   * **Card 2 — Semicircular Focus Dial & 2x2 Preset Grid:**
     * Compact 21-tick dial (`25m` readout) + 2x2 pastel squircle buttons + Pill CTA (`Start Focus Session 🚀`).
   * **Card 3 — Weekly Attention Bar Chart & Stability Waveform:**
     * 7-day pill columns with percentage readouts and animated sine waveform.

3. **Surface 3: Full-Tab Insights Dashboard (`dhyan.openInsights`)**
   * Triggerable from Command Palette (`Ctrl+Shift+P` -> `"Dhyan: Open Insights Dashboard"`).
   * Opens an expansive 2-column Bento analytics dashboard inside an editor tab with telemetry breakdown, circadian attention curve, and privacy audit certificate.

### 3.2 IDE Event Bridge & Pet Reactions

The VS Code extension hooks into non-intrusive editor lifecycle events to bring the companion pet to life without invading privacy:

| IDE Event | Event Listener | Companion Reaction | Telemetry Impact |
|---|---|---|---|
| **Code Typing** | `workspace.onDidChangeTextDocument` | Eye pupils saccade subtly toward typing location; enters `FOCUSED` state | Increments window edit count; zero code inspection |
| **Document Save** | `workspace.onDidSaveTextDocument` | Cheerful nod; updates save cadence | Increments save counter |
| **Task / Build Success** | `tasks.onDidEndTaskProcess` (`exitCode === 0`) | Enters `HAPPY` mode with 4-point sparkle particle shower! | Emits task completion telemetry |
| **Idle for > 20 Seconds** | Internal idle timer | Drifts into `SLEEPY` mode with floating semi-transparent `Zzz` bubbles | Transitions state to `idle_open` |
| **Window Regained Focus** | `window.onDidChangeWindowState` | Perks up into `CURIOUS`; checks for post-break resumption delay | Calculates resumption lag in ms |

---

## 4. Frosted 3D Cloud Spirit Companion Physics Engine

The companion pet is implemented as an HTML5 Canvas rendering engine shared across Desktop and VS Code:

### 4.1 Visual Hierarchy & Shading
1. **Soft Ambient Aura:** Multi-stop radial gradient (`rgba(122, 214, 86, 0.42)` in focus, yellow in happy, sky-blue in sleep) creating a frosted glass glow.
2. **Plump Multi-Puff 3D Clay Silhouette:** Composed of three overlapping radial gradient spheres (left puff, right puff, top center dome) anchored on a squircle bridge base.
3. **Expressive Sclera & Pupils:** White sclera ellipses with charcoal pupils, specular catchlights, and micro-reflections that follow the cursor via spherical projection.
4. **Rosy Cheeks & Tactile Mouth:** Semi-transparent blush cheeks and variable-curve mouth responding to emotional states.

### 4.2 Spring-Damped Mass Physics (Squash-and-Stretch)
To give the pet an organic, tactile "jelly" feel, interactions are governed by a second-order harmonic spring-damper system:
$$a_x = k \cdot (x_{\text{target}} - x) - c \cdot v_x$$
$$a_y = k \cdot (y_{\text{target}} - y) - c \cdot v_y$$

* Spring stiffness $k = 0.16$, Damping coefficient $c = 0.78$.
* **Click / Petting:** Squashes to $x = 1.25, y = 0.78$. On release, the stored spring tension causes an energetic overshoot bounce before settling back to equilibrium.
* **Idle Breathing:** Superimposes a gentle sinusoidal breathing cycle ($\pm 2.4\%$) at $0.04\text{ rad/frame}$.

### 4.3 Dynamic Particle System
* **Sparkle Stars:** 4-point quadratic stars that burst outward when `HAPPY` or when a task completes.
* **Floating Zzz Bubbles:** Ascending typography particles with randomized drift velocity and alpha fade during `SLEEPY` mode.
* **Love Hearts:** Pop upward when the user pets or clicks the cloud repeatedly.

---

## 5. Verification & Test Evidence

All automated builds, typechecks, and test suites pass 100%:

```powershell
# 1. Desktop App Typecheck & Build
npm --prefix laptop run typecheck   # -> EXIT 0 (tsc --noEmit clean)
npm --prefix laptop run build       # -> EXIT 0 (electron-vite built in 1.68s)
npm --prefix laptop test            # -> 86 / 86 PASS (0 failures, 3.8s)

# 2. VS Code Extension Unit Tests
node --test tools/test/vscode-ext.test.mjs  # -> 1 / 1 PASS (283ms)

# 3. Full Repository Test Suite
npm test                            # -> 117 / 117 PASS (0 failures, 37.7s)

# 4. Stage 10 Pitch Metrics Validation Runner
npm run validate:pitch              # -> 8 / 8 SUITES PASS (100%)
```

---

## 6. Privacy & Invariant Guarantees (Spec §25.13)

* **Zero Content Inspection:** The VS Code extension and Desktop app never read, log, or transmit source code text, variable names, AST tokens, file paths, or URL strings.
* **Categorical Cadence Only:** Telemetry contains exclusively event timestamps, categorical enums (`coding`, `docs`, `social_notif`), and numerical frequency counters.
* **23 Forbidden Fields Enforcement:** Automated AST and schema guards reject any payload containing prohibited keys.
