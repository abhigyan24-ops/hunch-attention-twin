# Manual laptop checks

## 7A live checks

These are owner checks, not automated success claims. ActivityWatch, the real Windows foreground collector and Electron desktop are **not verified on target** until their results are recorded. ActivityWatch is the open-source collection backbone (https://activitywatch.net/).

From PowerShell:

```powershell
Set-Location C:\Users\abhig\OneDrive\Desktop\dhyan-s7\laptop
# Start your installed ActivityWatch application from the Start menu.
npm run check:aw
node scripts/live-aw-smoke.mjs
```

check:aw prints bucket IDs only and exits0 when window and afk watchers exist. The smoke runs for60seconds using the real AwAdapter, then prints one JSON summary containing only bucketIds, categoryCounts and stateChanges. Missing web/vscode watchers are allowed. If unavailable, it prints `not verified on target: ActivityWatch not running` and exits1. Send those summaries/exit codes, not raw watcher events, titles or URLs. No raw AW event is written by the smoke.

Install the Electron binary only if it is missing, then launch:

```powershell
npm exec install-electron
npm run dev
```

Expected: one Dhyan window. Starting a second instance should focus the existing window. Existing demo UI values are labelled sample data; they are not real measurements. When storage fails the panel warns that persistence is paused. Switch between a work app and another app for over3seconds to exercise debounce. Stop ActivityWatch; after15seconds the PowerShell foreground/idle fallback starts. If both collectors are unavailable, state becomes offline and one adapter_offline gap is produced for that outage. No gap may count as focus. Restart ActivityWatch to restore the primary collector. Close Dhyan normally; it waits for queued writes and attempts a final storage retry.

Use `npm run check:s7` for mocked failure/recovery, privacy and bounded-queue verification. Do not fill the real disk or delete userData to test recovery. Send a description of the observed single-instance/fallback/shutdown behavior and any warning shown; do not send events.jsonl or screenshots containing unrelated content. Check click-through/Pip behavior separately in7B; it is not verified by the7A automated suite.

## 7B live checks

Automated tests cover presentation rules and event shapes, not Windows click-through or visible animations. All checks below are **not verified on target** until the owner records the result.

```powershell
Set-Location C:\Users\abhig\OneDrive\Desktop\dhyan-s7\laptop
# Only if the Electron binary is missing:
npm exec install-electron
npm run dev
```

Pip should appear without taking keyboard focus. Open VS Code underneath it. Click a transparent area outside Pip's hitbox: the underlying window should receive the click. Hover only the Pip hitbox: mouseenter disables ignore-mouse so Pip can be clicked. Leave it: mouseleave restores ignore-mouse with forward:true. Click Pip to open a separate normal panel. Closing the panel should leave Pip and collection running. Tray: Open panel, Declare session, Pause Pip/Resume Pip, Quit. Pause Pip hides only Pip, not collection. Exclusive-fullscreen games may hide Pip; borderless-windowed is a separate owner check.

Real-state checks:
- Before observations: No data yet. A stopped callback stream becomes stale; it never shows focus.
- With collectors unavailable: a received adapter_offline gap produces Collector offline. Because7A lacks a direct current-silence diagnostic, a later ambiguous offline-valued heartbeat shows Collector status unavailable. It must not become focus.
- A fresh offline-valued rule snapshot accompanied by activity evidence shows Active but unclassified, not Collector offline. Confidence always says rule-based, not calibrated. See R7B-1 in NOTES for the diagnostic limitation.
- Storage pause must warn that recent events may exist only in memory. Do not deliberately fill the disk; automated tests inject this fault.
- Today's observed total should show No data with current7A files unless canonical focus snapshots exist. Coding app categories alone must never create focus minutes. No fake3h48m, percentages, pairing code, held sender or what-if cost may appear.
- Declare coding/120minutes/strict via form or tray. It is local only, with taskSource=declared in local UI state, canonical task/plannedMin/strict event payload, and phone sync pending. A paused write must not be described as saved.
- Game labels accept process basenames only, never filesystem paths. Known-list/user-label classification is unchanged.

To inspect all seven visual behaviors without fabricating observations, quit the running dev instance, then:

```powershell
$env:DHYAN_DEV_PET='1'
npm run dev
# After quitting the test session:
Remove-Item Env:DHYAN_DEV_PET
```

Tray -> DEV ONLY: simulate presentation -> each of deep_focus, held, drifting, distracted, recovering, fatigued, second_screen_overlap. Both views must show DEV SIMULATION, and observed totals stay no-data. Select Use real pipeline to return. This menu is absent in packaged builds and when the environment flag is unset. Simulated presentation does not call injectFeedEvent, change inference parameters, persist events or act on notifications. Remote behavior tests use synthetic inputs only; no real phone connection is claimed. Each bubble type is limited to once per10minutes, including repeated clicks; reduced-motion preference disables animation.

Send only which checks passed/failed and controlled UI wording. Do not send JSONL, unrelated window titles or notification content. 7C/8L, real pairing and phone-held actions remain pending.
