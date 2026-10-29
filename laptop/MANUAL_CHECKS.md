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
