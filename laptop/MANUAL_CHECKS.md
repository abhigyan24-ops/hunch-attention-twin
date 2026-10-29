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

## CX-1 update after W3
The label-once form now lets the owner choose any canonical category for a process basename. Communication apps are not work until explicitly labelled coding/docs/learning. A label takes effect after a window change (existing7A semantics); switch away and back to exercise it. Game paths remain rejected. Automated suites now validate1.1.0 fixtures; manual desktop checks above are still pending.

## 7C extension ingest checks

Automated evidence: `npm run check:s7`, including test/ingest.test.mjs, passes strict authentication, shared attacks against the real server, 15-second heartbeat expiry, mapped JSONL events and privacy canaries. The 2026-10-29 local Electron smoke rendered Pip/panel, masked token, disconnected feed wording, and normal File > Exit. Actual VS Code/browser integration and Windows firewall behavior remain **not verified on target**.

1. Launch `npm run dev`. Open Pip's panel and scroll to Extensions. Expect Local ingest is ready, port47821 and path /ingest. If unavailable, resolve OS encrypted storage or port conflict; no plaintext token fallback exists.
2. Copy the token only into your VS Code/browser extension configuration. Do not paste it into logs, reports or screenshots. Add the installed Chrome/Edge extension's actual32-character ID in the allowlist, one per line. Browser Origins outside that list must fail. VS Code uses the no-Origin token-auth path.
3. Point both extensions to ws://127.0.0.1:47821/ingest and wait for their5-second heartbeats. The panel should show Connected and last-seen times. Stop one extension: its socket disconnects or, if silent, it becomes Offline after15seconds. Resume it: old state must not survive reconnect before fresh state/heartbeat.
4. Exercise edits/saves and VS Code's explicit stuck/flowing state; no numerical stuck threshold is implemented. Browse feed/shorts/watch surfaces in mapped distracting categories. Do not collect or share raw title/URL/content. No focus percentage should appear; confidence remains rule-based, not calibrated.
5. Removing an allowed browser extension ID should disconnect its socket. Five authentication failures in60seconds block NEW loopback attempts for5minutes, so a misconfigured client may also delay another local client's reconnect; established authenticated peers continue. Correct its token and wait for the block to expire.
6. Repeat7B click-through, tray and animation checks above separately. A successful ingest attack test does not verify these desktop interactions, cloud pairing or the phone.

## 8L verification entry points

Use GATE3_8L.md for exact laptop-side Gate3 steps1–9. Run `npm run check:s8l` at repository root for production-code local tests and synthetic peer interoperability. `scripts/privacy-check.mjs` calls the shared scanner while printing counts only; `scripts/verify-sync-coverage.mjs` adds missing-event coverage to the shared checker. The new8L UI, real phone pairing, cloudTLS and device attack harness are not verified on target. Coarse state mapping is visibly gated pending CCR-8L-1 owner response; no unclassified/offline state is labelled focus. Event/command sync and authenticated ping/pong are independent of that mapping.
