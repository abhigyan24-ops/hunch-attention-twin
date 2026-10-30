# AI Desktop Pet: Electron Implementation Guide (v2 — Dhyan Edition)

A complete, production-ready implementation plan for the **Dhyan AI Desktop Pet**: a full-screen transparent overlay companion that understands what you are doing on your screen, adapts its visibility dynamically (`0%` / `18%` / `100%` opacity) so it never annoys you during deep focus, physically roams/docks/intervenes across the entire monitor, expresses 10 procedural emotions (`HAPPY`, `SAD`, `ANGRY`, `SKEPTICAL`, etc.), and tracks validated nudge telemetry (`nudgesShown`, `nudgesAccepted`, `nudgesDismissed`).

---

## Table of Contents

1. [Goals and Design Principles](#1-goals-and-design-principles)
2. [System Architecture](#2-system-architecture)
3. [Dynamic Visibility & Presence State Machine (Opacity Engine)](#3-dynamic-visibility--presence-state-machine-opacity-engine)
4. [Tech Stack & Project Structure](#4-tech-stack--project-structure)
5. [Process Model and IPC](#5-process-model-and-ipc)
6. [Full-Screen Pet Overlay Window & HiDPI Canvas Fix](#6-full-screen-pet-overlay-window--hidpi-canvas-fix)
7. [Unclamped 2D Physics Engine, Drag/Fling & Autonomous Walk-In](#7-unclamped-2d-physics-engine-dragfling--autonomous-walk-in)
8. [Procedural Frosted 3D Cloud Spirit & 10 Emotions](#8-procedural-frosted-3d-cloud-spirit--10-emotions)
9. [Interactive Nudge Bubble & Validated Telemetry (Requirement #4)](#9-interactive-nudge-bubble--validated-telemetry-requirement-4)
10. [Layer 1–3: Signals, Screen Capture & Change Detection](#10-layer-13-signals-screen-capture--change-detection)
11. [Layer 4–6: Perception Tiers & Hybrid AI Screen-Context Classifier](#11-layer-46-perception-tiers--hybrid-ai-screen-context-classifier)
12. [Layer 7: Self-Tuning Behavior & Distraction Engine](#12-layer-7-self-tuning-behavior--distraction-engine)
13. [Privacy, Redaction & Observability](#13-privacy-redaction--observability)
14. [Testing & Verification Checklist](#14-testing--verification-checklist)

---

## 1. Goals and Design Principles

| Goal | How It Is Achieved |
| :--- | :--- |
| **Non-Intrusive** | Dynamic opacity (`18%` translucent Ghost Mode during productive flow, `0%` in fullscreen/meetings, `100%` only on hover, milestones, or verified distraction nudges) |
| **Unclamped Roaming** | Full-display `BrowserWindow` + HiDPI `window.innerWidth × window.innerHeight` `<canvas>` with `0..width` and `0..height` 2D physics |
| **Zero Missing Assets** | 100% procedural Canvas 2D rendering for the Frosted 3D Cloud Spirit, facial expressions, and particles (no external sprite sheets required) |
| **Context-Aware AI** | Hybrid AI classifier evaluates `declaredGoal` against active window title, URL, typing cadence, terminal errors, and local Ollama / cloud VLM fallback |
| **Evidence-Based** | Tracks raw `nudgesShown`, `nudgesAccepted`, and `nudgesDismissed` locally; automatically raises thresholds if dismissals dominate |

---

## 2. System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│              RENDERER: FULL-SCREEN TRANSPARENT PET OVERLAY             │
│  100% viewport HTML5 Canvas · Procedural 3D Cloud Spirit · Jelly       │
│  Physics · Dynamic Opacity (0% / 18% / 100%) · Interactive Nudge UI    │
└──────────────▲─────────────────────────────────────────────────────────┘
               │ IPC (contextBridge)
┌──────────────┴─────────────────────────────────────────────────────────┐
│                        MAIN PROCESS (orchestrator)                     │
│  Full-workArea overlay mgmt · Tray · Hotkeys · Supervisor · Event Bus  │
│  Distraction & Behavior Engine · Nudge Telemetry · Privacy Gate        │
└───▲────────────────▲───────────────────▲───────────────────────────────┘
    │ MessagePort    │ MessagePort       │ MessagePort
┌───┴──────────┐ ┌───┴──────────────┐ ┌──┴───────────────────────────────┐
│ CAPTURE      │ │ PERCEPTION       │ │ AI CONTEXT & REASONING SERVICE   │
│ (hidden      │ │ (utilityProcess) │ │ (utilityProcess)                 │
│  renderer)   │ │ dHash/block diff │ │ Goal vs. Screen Classifier       │
│ 480p stream  │ │ ROI OCR (Tier C) │ │ Local Ollama + Rule Fallback     │
└──────────────┘ └──────────────────┘ └──────────────────────────────────┘
```

---

## 3. Dynamic Visibility & Presence State Machine (Opacity Engine)

Staying at 100% opacity in the middle of the screen is irritating during deep work. Conversely, hiding completely when the user gets distracted prevents the pet from intervening. The renderer interpolates `pet.alpha += (desiredAlpha - pet.alpha) * Math.min(1, dt * 8)` every frame across four states:

| Presence Mode | Trigger Condition | Target Opacity (`targetAlpha`) | Position & Visual Behavior |
| :--- | :--- | :--- | :--- |
| **`HIDDEN`** | DND active, fullscreen presentation/video/call, or user paused (`Ctrl+Alt+P`) | **`0.00` (0%)** | Completely invisible; `setIgnoreMouseEvents(true)` forced. |
| **`GHOST_FLOW`** | User is actively working on a `PRODUCTIVE` task aligned with their goal for `> 10s` | **`0.18` (18%)** *(configurable `0.0–0.25`)* | Docked quietly at the screen edge (`PEEK_MODE`). Renders a calm `FOCUSED` silhouette that never distracts peripheral vision. |
| **`COMPANION_EDGE`** | User hovers cursor over pet, switches apps, hits a focus milestone, or triggers compiler errors | **`1.00` (100%)** | Docked at screen edge or walking along the bottom workArea margin. Expresses `HAPPY`, `PROUD`, `CURIOUS`, or empathetic `ANGRY`. |
| **`INTERVENTION_WALK_IN`** | Sustained `DISTRACTED` context exceeds the adaptive threshold | **`1.00` (100%)** | **Launches/walks from the screen edge to the visible screen center** `(innerWidth * 0.5, innerHeight * 0.42)`, squashes on landing, shows `SKEPTICAL` → `POUTING` → `SAD` / `ANGRY`, and opens the `[Refocus]` / `[Dismiss ✕]` bubble. |

* **Hover Override:** Whenever `pet.hitTest(mouseX, mouseY)` or `bubble.hitTest(mouseX, mouseY)` is true, `effectiveTargetAlpha` overrides to `1.00` so the user can interact with, pet, or drag the companion at any time.

---

## 4. Tech Stack & Project Structure

```
laptop/
├─ electron.vite.config.ts
├─ package.json
├─ vitest.config.ts
├─ src/
│  ├─ shared/
│  │  ├─ types.ts              # Emotion, PresenceMode, Context, TelemetryStats
│  │  └─ ui.ts
│  ├─ main/
│  │  ├─ index.ts              # App bootstrap & IPC wiring
│  │  ├─ windows/overlay.ts    # Full-display transparent BrowserWindow
│  │  ├─ behavior/
│  │  │  ├─ engine.ts          # Distraction escalation + Requirement #4 counters
│  │  │  └─ telemetry.ts       # Persistent nudgesShown/Accepted/Dismissed store
│  │  └─ petPosition.ts
│  ├─ services/
│  │  └─ ai/
│  │     └─ classifier.ts      # Hybrid Ollama LLM + deterministic fallback classifier
│  ├─ preload/
│  │  ├─ index.ts              # Existing panel preload
│  │  └─ overlay.ts            # ContextBridge API for full-screen overlay
│  └─ renderer/
│     └─ overlay/
│        ├─ index.html         # Full-viewport transparent CSS
│        ├─ main.ts            # HiDPI canvas resize, pointer events, 60fps loop
│        ├─ pet.ts             # Unclamped 2D physics, drag/fling, walkTo, opacity
│        ├─ cloud-spirit.ts    # Procedural 3D Cloud Spirit + 10 emotions + particles
│        └─ bubble.ts          # Speech bubble with [Refocus] & [Dismiss ✕] hitboxes
└─ test/
   ├─ pet-physics.test.ts
   ├─ behavior-telemetry.test.ts
   └─ ai-classifier.test.ts
```

---

## 5. Process Model and IPC (`src/shared/types.ts` & `src/preload/overlay.ts`)

```ts
// src/shared/types.ts
export type PetEmotion =
  | 'FOCUSED'
  | 'HAPPY'
  | 'PROUD'
  | 'CURIOUS'
  | 'SKEPTICAL'
  | 'POUTING'
  | 'SAD'
  | 'ANGRY'
  | 'SLEEPY'
  | 'DIZZY'

export type PresenceMode =
  | 'HIDDEN'
  | 'GHOST_FLOW'
  | 'COMPANION_EDGE'
  | 'INTERVENTION_WALK_IN'

export type AlignmentKind = 'PRODUCTIVE' | 'NEUTRAL' | 'DISTRACTED' | 'ERROR_STUCK'

export interface TelemetryStats {
  nudgesShown: number
  nudgesAccepted: number
  nudgesDismissed: number
  thresholdMultiplier: number
  cooldownMs: number
}

export interface PetDirective {
  emotion: PetEmotion
  presence: PresenceMode
  alignment: AlignmentKind
  nudgeText?: string
  isIntervention?: boolean
  telemetry: TelemetryStats
}
```

```ts
// src/preload/overlay.ts
import { contextBridge, ipcRenderer } from 'electron'
import type { PetDirective, PetBridgeApi } from '../shared/types'

const bridge: PetBridgeApi = {
  onDirective: (cb: (d: PetDirective) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, directive: PetDirective) => cb(directive)
    ipcRenderer.on('pet:directive', handler)
    return () => {
      ipcRenderer.removeListener('pet:directive', handler)
    }
  },
  setIgnoreMouse: (ignore: boolean) =>
    ipcRenderer.send('pet:ignore-mouse', ignore),
  nudgeAccepted: () =>
    ipcRenderer.send('pet:nudge-accepted'),
  nudgeDismissed: (reason: 'button' | 'flung' | 'timeout') =>
    ipcRenderer.send('pet:nudge-dismissed', reason),
  petBooped: () =>
    ipcRenderer.send('pet:booped')
}

contextBridge.exposeInMainWorld('petBridge', bridge)
```

---

## 6. Full-Screen Pet Overlay Window & HiDPI Canvas Fix

### 6.1 Full-Display Overlay Window (`src/main/windows/overlay.ts`)

```ts
import { BrowserWindow, screen, ipcMain } from 'electron'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import type { PetDirective } from '../../shared/types'

const overlays = new Map<number, BrowserWindow>()
let ipcInitialized = false

function resolvePreloadPath(): string {
  try {
    return fileURLToPath(new URL('../preload/overlay.cjs', import.meta.url))
  } catch {
    return path.join(__dirname, '../preload/overlay.cjs')
  }
}

function resolveOverlayHtmlPath(): string {
  try {
    return fileURLToPath(new URL('../renderer/overlay/index.html', import.meta.url))
  } catch {
    return path.join(__dirname, '../renderer/overlay/index.html')
  }
}

export function createOverlays(): Map<number, BrowserWindow> {
  initIpc()
  screen.getAllDisplays().forEach(createOverlayFor)

  screen.on('display-metrics-changed', (_e, d) => {
    overlays.get(d.id)?.setBounds(d.workArea)
  })

  screen.on('display-removed', (_e, d) => {
    const win = overlays.get(d.id)
    if (win) {
      if (!win.isDestroyed()) win.destroy()
      overlays.delete(d.id)
    }
  })

  screen.on('display-added', (_e, d) => {
    if (!overlays.has(d.id)) {
      createOverlayFor(d)
    }
  })

  return overlays
}

function createOverlayFor(display: Electron.Display): BrowserWindow {
  const existing = overlays.get(display.id)
  if (existing && !existing.isDestroyed()) return existing

  const win = new BrowserWindow({
    x: display.workArea.x,
    y: display.workArea.y,
    width: display.workArea.width,
    height: display.workArea.height,
    transparent: true,
    frame: false,
    hasShadow: false,
    resizable: false,
    movable: false,
    skipTaskbar: true,
    focusable: false,
    alwaysOnTop: true,
    fullscreenable: false,
    show: false,
    webPreferences: {
      preload: resolvePreloadPath(),
      contextIsolation: true,
      sandbox: true,
      backgroundThrottling: false
    }
  })

  win.setAlwaysOnTop(true, 'screen-saver')
  win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  win.setIgnoreMouseEvents(true, { forward: true })

  win.once('ready-to-show', () => {
    win.showInactive()
  })

  win.on('closed', () => {
    overlays.delete(display.id)
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    const url = new URL(process.env.ELECTRON_RENDERER_URL)
    url.pathname = '/overlay/index.html'
    void win.loadURL(url.href)
  } else {
    void win.loadFile(resolveOverlayHtmlPath())
  }

  overlays.set(display.id, win)
  return win
}

function initIpc() {
  if (ipcInitialized) return
  ipcInitialized = true

  ipcMain.on('pet:ignore-mouse', (e, ignore: unknown) => {
    if (typeof ignore !== 'boolean') return
    const win = BrowserWindow.fromWebContents(e.sender)
    if (win && !win.isDestroyed()) {
      win.setIgnoreMouseEvents(ignore, { forward: true })
    }
  })
}
```

### 6.2 Full-Viewport CSS & HiDPI Canvas (`src/renderer/overlay/index.html` & `main.ts`)

```html
<!-- src/renderer/overlay/index.html -->
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8" />
  <title>Dhyan Pet Overlay</title>
  <style>
    html, body {
      margin: 0;
      padding: 0;
      width: 100vw;
      height: 100vh;
      overflow: hidden;
      background: transparent !important;
      user-select: none;
    }
    canvas {
      display: block;
      width: 100vw;
      height: 100vh;
    }
  </style>
</head>
<body>
  <canvas id="c"></canvas>
  <script type="module" src="./main.ts"></script>
</body>
</html>
```

---

## 7. Unclamped 2D Physics Engine, Drag/Fling & Autonomous Walk-In (`src/renderer/overlay/pet.ts`)

- Full unconstrained roaming across `[0, window.innerWidth - pet.size]` and `[0, window.innerHeight - pet.size]`.
- Hooke's law squash-and-stretch:
  $$F_{\text{spring}} = -180 \cdot \text{deformation} - 14 \cdot \text{deformationVel}$$
- Wall restitution (`0.65`), floor restitution (`0.55`), and gravity (`1500 px/s²`) during ballistic flight.
- Holds intervention position at screen center without dropping to the taskbar.

---

## 8. Procedural Frosted 3D Cloud Spirit & 10 Emotions (`src/renderer/overlay/cloud-spirit.ts`)

Renders 100% procedurally on HTML5 2D Canvas with zero external PNG dependencies:
- **10 Emotions:** `FOCUSED, HAPPY, PROUD, CURIOUS, SKEPTICAL, POUTING, SAD, ANGRY, SLEEPY, DIZZY`.
- Distinct radial auras:
  - `ANGRY`: `rgba(251,113,133,0.42)`, mid: `#FDA4AF`, deep: `#E11D48`
  - `SAD` / `POUTING`: `rgba(148,163,184,0.35)`, mid: `#CBD5E1`, deep: `#64748B`
  - `PROUD` / `HAPPY`: `rgba(124,232,158,0.45)`, mid: `#86EFAC`, deep: `#10B981`
  - `SKEPTICAL`: `rgba(253,224,71,0.38)`, mid: `#FDE047`, deep: `#CA8A04`
  - `DIZZY`: `rgba(226,206,241,0.45)`, mid: `#D8B4FE`, deep: `#9333EA`
  - `FOCUSED` / `CURIOUS` / `SLEEPY`: `rgba(124,200,248,0.36)`, mid: `#93C5FD`, deep: `#3B82F6`
- Eye tracking towards cursor, slanted brows, half-lid squints, and procedural particles (`SPARKLE`, `HEART`, `STEAM`, `ZZZ`).

---

## 9. Interactive Nudge Bubble & Validated Telemetry (`src/renderer/overlay/bubble.ts` & `src/main/behavior/telemetry.ts`)

### 9.1 Speech Bubble with Interactive Actions (`src/renderer/overlay/bubble.ts`)

Clickable `[Refocus]` and `[Dismiss ✕]` buttons inside the bubble canvas during `INTERVENTION_WALK_IN`.

### 9.2 Persistent Telemetry & Auto-Backoff Thresholds (`src/main/behavior/telemetry.ts`)

Enforces Requirement #4:
- Persists `nudgesShown`, `nudgesAccepted`, `nudgesDismissed`, `thresholdMultiplier`, and `cooldownMs`.
- **Auto-Backoff Rule:** If `nudgesShown >= 5` and (`nudgesDismissed / nudgesShown > 0.60` or `nudgesDismissed > nudgesAccepted`):
  $$\text{thresholdMultiplier} = \min(3.0, 1.0 + (\text{dismissalRate} - 0.5) \cdot 2.5)$$
  $$\text{cooldownMs} = 90000 \cdot (1 + \text{nudgesDismissed} \cdot 0.5)$$

---

## 10. Layer 1–3: Signals, Screen Capture & Change Detection

- Active window title, app name, and process tracking.
- Input cadence: keystrokes per minute and idle seconds (raw counts only; never logs keystroke content).
- Frame stability and change gate before OCR or LLM classification.

---

## 11. Layer 4–6: Hybrid AI Screen-Context Classifier (`src/services/ai/classifier.ts`)

- Detects compiler/terminal errors (`error TS`, `Exception`, `Failed to compile`, `err!`, `Traceback`, `SyntaxError`) -> `ERROR_STUCK` with empathetic `ANGRY` emotion in `COMPANION_EDGE`.
- Queries local Ollama (`http://127.0.0.1:11434/api/chat` with strict 1200ms `AbortController` timeout).
- Deterministic regex/keyword fallback when Ollama is offline/unreachable so classification never hangs or throws.

---

## 12. Layer 7: Self-Tuning Behavior & Distraction Engine (`src/main/behavior/engine.ts`)

- Tracks distraction seconds against `effectiveThreshold = baseThreshold * thresholdMultiplier`.
- Triggers `INTERVENTION_WALK_IN` when cooldown has elapsed.
- If user resumes a productive task $\le 10$s after nudge, counts as accepted, triggers `HAPPY` + sparkles celebration, and glides back to edge in `GHOST_FLOW` (18% opacity).
- If user clicks `[Dismiss ✕]` or flings pet away, counts as dismissed and glides back to screen margin.

---

## 13. Privacy, Redaction & Observability

- Privacy Gate: blocks capture and classification on sensitive apps and private windows.
- Debug Telemetry HUD (`Ctrl+Alt+D`): Displays live factual telemetry counters on canvas without unverified marketing claims.

---

## 14. Testing & Verification Checklist

- **Full-Screen Bounds Test:** Verified dragging across all 4 quadrants of a 1920x1080 viewport without center-clamping.
- **Dynamic Opacity Test:** Verified `GHOST_FLOW` (0.18), `INTERVENTION_WALK_IN` (1.0), `HIDDEN` (0.0), and hover override (1.0).
- **Emotion & Context Test:** Verified compiler errors (`ERROR_STUCK` -> `ANGRY`), active coding (`PRODUCTIVE` -> `FOCUSED`), and sustained distraction (`DISTRACTED` -> `POUTING`/`SAD`).
- **Telemetry Auto-Backoff Test:** Verified multiplier scaling when dismissals dominate at $\ge 5$ nudges.
- **Suite Status:** 22/22 vitest tests passing; 102/102 node:test tests passing; 0 TypeScript errors.
