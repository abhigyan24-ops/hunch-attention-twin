# Daylight redesign decisions

- Owner supplied free-hand presentation scope, preserving business logic, flows, tests and actions. Work is isolated on codex/daylight-ui, based on 0317fe3. No merge or push is implied by this UI request.
- Canonical UI honesty (SPEC 25.13) takes precedence over decorative reference analytics. There is no streak, weekly aggregate, completion target or trend series in InsightsUiState. Use observed totals and an explicitly labelled action share; keep missing history visible. No fake metrics or sample data were connected.
- The requested rotary duration pattern maps to the existing hours-worked input; its unit is explicit and its original slider remains available. No meditation/session timer was invented.
- Current ViewModels do not expose a new storage-paused/error analytics state; this redesign does not infer one from zero counts. Existing live/sync no-data, stale and disconnected descriptions remain intact. A richer analytics load/error contract remains a separate data-layer question.
- Existing package allow-list/debug-hold ViewModel methods have no controls in the audited Settings sheet; no behavior is inferred or silently added. All controls present at audit are retained.
- Prefer the slightly deeper lavender action token #5558D6 for small text; the reference #6366F1 remains an aesthetic cue, not a contrast guarantee. Pastel backgrounds and decorative colors are separate from accessible foregrounds.
- No runtime dependencies, fonts, bitmaps, persona data, model calculations, Room schema or contracts changed. The prior v4 database and chart endpoint crash fixes remain present.

Executed verification is appended after the checks complete.

## Executed verification (this redesign session)

- Design-system compile: `:app:compileDebugKotlin` — `BUILD SUCCESSFUL in 50s` (9 tasks, 2 executed).
- Screen compile — `BUILD SUCCESSFUL in 15s`; cloud renderer compile — `BUILD SUCCESSFUL in 6s`. No new Kotlin compiler warning; pre-existing AGP source-set configuration deprecation remains.
- Full regression command, from phone with ANDROID_HOME configured: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --continue`. Actual summary: `385 tests completed, 1 failed`, `BUILD FAILED in 41s`. The unchanged `Stage4BParameterRecoveryTest.rahulFourteenDayReplayRecoversPickupProbWithinEightPercent` estimates 0.7247279352031226 outside [0.54, 0.7] (error 0.10472793520312262 > 0.08). This predates the redesign; changing that model or weakening its test would violate the owner's requirement to preserve business logic/tests. Full-suite green is therefore not claimed.
- Final footer/font-scale adjustment verification: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.dhyan.app.ui.*' :app:assembleDebug :app:assembleDebugAndroidTest` — `BUILD SUCCESSFUL in 52s`, `79 actionable tasks: 8 executed, 71 up-to-date`. JUnit XML: 74 tests, 0 failures, 0 errors, including all 46 pet tests, palette contrast and 4 new mapping tests.
- Final emulator instrumentation, explicit emulator-5580 / Pixel_8a API 34: `adb -s emulator-5580 shell am instrument -w -r -e package com.dhyan.app com.dhyan.app.test/androidx.test.runner.AndroidJUnitRunner` — `Time: 50.689`, `OK (14 tests)`, all per-test success statuses 0. Covers 8 pet tests, 3 new design-component tests, 2 real Room compatibility tests and chart endpoint drawing. No physical device was connected for this redesigned build.
- `npm run checks`: schema-sync PASS (1.1.0), twin-purity PASS (453 files), no-persona-imports PASS (407 production files). `git diff --check` passed.
- Actual emulator screenshots inspected: Simulate hero/cloud, task grid/dial, Live with no-data states, Insights with no recorded data, dark Insights, dark Settings and Settings at 1.5× font scale. Images/logs are under the external CX6-phone-review artifact directory. Larger-text inspection is scoped to these views; it is not a universal accessibility certification.
- Final APK saved as Dhyan-daylight-debug.apk and also replaces Dhyan-spirit-debug.apk at the existing owner-facing artifact path. SHA-256: `1DF28E2F74E19286ADD62CE8B7B1FB4DB54842B7F55597E416061E50E104796F`.

Privacy-sensitive sources, ViewModels, Room and sync implementations are unchanged by this redesign. Real haptic feel, battery/performance, physical-device UI and hardware refresh rate remain **not verified on target** for this build. No push or main merge.
