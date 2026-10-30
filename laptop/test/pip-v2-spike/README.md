# Pip v2 M0 comparison harness

[VERIFIED] This is an isolated dummy-sprite prototype. It imports no production collectors, pipeline, settings or relay. It does not capture the screen. Its metrics describe this harness only. Production Pip remains unchanged.

[VERIFIED] A moves a small BrowserWindow using setBounds. B creates a workArea-sized transparent BrowserWindow per detected display and updates a DOM sprite transform. Only the primary display shows the sprite in this M0 prototype. Cross-display handoff is not implemented here and remains an M2 owner check.

[ASSUMED] Timing/sizes are in pip-config.proposed.json under spike: 2-second warmup, 5-second idle interval, 5-second scripted trajectory, 500-ms CPU samples, and an optional 60-second owner probe. There is a 90-second safety exit. All policy/config values in that file are proposals, not approved product defaults.

From repository root in PowerShell, after laptop dependencies are installed:

```powershell
# Numeric trial only, sequential A then B. Each starts a separate Electron process.
powershell -NoProfile -File laptop/test/pip-v2-spike/run-spike.ps1 -Option Both

# Owner-controlled hover / click-through experiment. Run these separately.
powershell -NoProfile -File laptop/test/pip-v2-spike/run-spike.ps1 -Option A -Interactive
powershell -NoProfile -File laptop/test/pip-v2-spike/run-spike.ps1 -Option B -Interactive

# Pure report guards and privacy boundary checks.
node --test laptop/test/pip-v2-spike/spike.test.mjs
```

[VERIFIED] Results default to `$env:TEMP\dhyan-pip-v2-m0-results\option-A.json` and option-B.json. Each process uses a separate diagnostic profile there, not Dhyan userData. Automated mode leaves the windows configured to ignore mouse input. Interactive mode enables hitbox capture and adds a controlled underlay button; it records hover callbacks and real underlay clicks. It never automatically marks either as accepted Windows behavior.

[VERIFIED] CPU is measured with app.getAppMetrics after priming its interval counter; the report separates renderer CPU from the sum of all processes in this isolated application. Frame intervals are requestAnimationFrame callback intervals, not presentation/GPU frame latency or physical input latency. The unthrottled measurement callback runs during the nominal idle phase too. The dummy does not render the production Cloud Spirit or its full animation workload. This is one trial, with other desktop workload uncontrolled.

[UNKNOWN] Owner must still test real pointer capture/release, forwarding over transparency, clicks reaching the underlay, elevated foreground windows, mixed DPI, taskbar layouts, remote desktop, sleep/wake and fullscreen. Nonzero callback/click counters alone do not prove all these conditions. See laptop/MANUAL_CHECKS.md and NOTES_pip-v2.md. Do not elevate the diagnostic application as a workaround for UIPI.
