# Dhyan UI redesign

Owner approved Phase1/2 and requested a less cluttered, calm, polished aesthetic. Branch: codex/ui-design-system, based on0c1ae44. No main edits, push or deployment. No subagents and only one Gradle build at a time.

## Scope and execution

Preserve existing ViewModels, StateFlows, model/actuator behavior and callbacks. Keep all three tabs, Twin Lab, six insight categories, settings and human SAS confirmation. No new runtime visual library. Only approved Compose preview tooling is added under the existing BOM.

Implementation order: foundation and semantic tokens; reusable stateless components; shell; Simulate/Twin Lab; Live/Digest; Insights; Settings/Pairing. Run the appropriate Kotlin compile task after every source/build-file modification, stopping and fixing failures before proceeding.

Compile records are stored outside the repository beside the UI phase reports. Final verification and limits will be appended after execution. Existing recovery-model validation failure is separate and will not be hidden or weakened by this redesign.

## Coverage and presentation boundaries

| Area | Result |
|---|---|
| Navigation | Three native Material destinations, neutral header, no legacy sample count; Activity-owned existing ViewModels retain state across tab switches and recreation. |
| Simulate | Synthetic banner, three personas, 14-day replay, task/category/VIP controls; optional attention, hours, time, urgency and repeat controls; real provider verdict and evidence. |
| Twin Lab | Existing Beta and LogNormal curves retained; actual nEff/model version shown; unavailable model never appears as a zero-loss prediction. |
| Futures | Actual simulated means and probabilities; legacy interval fields correctly labeled as the difference-in-loss 80% simulation interval; no invented suppressed-arm interval or goal. |
| Live | Actual policy, emergency stop, laptop availability, snapshot and provenance; no-data state; rule-based confidence label; BREAK says Break. |
| Digest | Show-now/release-all and Important/Noise preserved. Feedback requires a matching real recent decision; otherwise disabled with an explanation. Category/state/mechanism and real recorded outcomes remain visible. |
| Insights | Real stored action/outcome totals; all six insight areas explicitly pending where the existing ViewModel provides no analytics. Intentionality, resumption, saved minutes and ECE remain visible as unavailable analytics. |
| Settings | Policy confirmation, caution, exploration, digest interval, emergency stop, transient trusted-sender entry, pairing/SAS comparison, unpair confirmation, declared session and command history retained. Debug notification posting is debug-only. |
| Design | Sage/obsidian and warm-light Material tokens, larger typography, borderless cards, restrained static focus display, native controls, synthetic-only light/dark/large-text previews. |

No model, actuator, sync, collector, database, manifest, frozen contract or root package file is edited. The two preview dependencies use the existing Compose BOM.

### Existing data/API gaps, not inferred by the redesign

- Insights supplies counts only, without time-window/series/loading/error metadata. Counts are labeled stored totals; unavailable analytics are not fabricated.
- Live context exposes no independent phone permission-denied or collector-health status. A missing snapshot is shown as no data; laptop availability wording is preserved verbatim. No phone permission diagnosis is invented.
- Held items do not carry the decision database ID. Feedback can only be mapped when a matching recent decision exists; no zero/default ID is sent.
- Release callbacks currently request DAO state changes. The interface explicitly does not claim that the original notification was delivered.
- Settings retains its existing emergency-stop callback and controller wiring; this visual change does not claim an actuator integration fix.
- Live record timestamps are the existing ViewModel strings. The UI does not invent new refresh rates, prediction windows or confidence values.

## Executed verification (this session)

- Ran `:app:compileDebugKotlin` after every production source/build edit (and `:app:compileDebugUnitTestKotlin` for the new test). All final source compiles passed. The initial offline preview-dependency resolution failed because the approved artifacts were not cached; the online retry succeeded without version changes.
- Final command: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:assembleDebug` — `BUILD SUCCESSFUL in 37s`, `40 actionable tasks: 5 executed, 35 up-to-date`.
- `gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest` — `335 tests completed, 1 failed`, `BUILD FAILED in 2m 10s`. XML confirms 334 passed. The unchanged failure is `Stage4BParameterRecoveryTest.rahulFourteenDayReplayRecoversPickupProbWithinEightPercent`: observed 0.7247279352031226, required [0.54, 0.70]. No model, censoring rule, test threshold or recovery test was changed.
- `PaletteContrastTest.lightAndDarkTextAndActionsHaveAccessibleContrast` — 1 test, 0 failures, 0 errors. Verifies normal-text 4.5:1 contrast for light/dark body and muted text on the intended surfaces and text on action containers.
- Root `npm run checks` — schema-sync PASS (1.1.0); twin-purity PASS (453 files); no-persona-imports PASS (388 production files).
- `git diff --check` — passed.
- Final APK installed successfully in a disposable `Pixel_8a` API 34 emulator, port 5580, launched with `-read-only -no-snapshot -no-window -gpu software`. App launch and process liveness confirmed. AVD changes are temporary; no physical device commands were issued.
- Rendering inspection initially encountered emulator Android system/System UI ANR dialogs. These are not treated as a successful full visual check. A reboot retry is tracked below.

### Visual review and final presentation adjustment

The isolated emulator recovered after reboot with no Gradle workload. Inspected actual rendered light-mode Simulate, Live, Insights and Settings; dark-mode Simulate and Settings; and 150% text scaling on Simulate and Settings. No observed text overlap in these captures. Empty Live state correctly shows no attention/laptop data; Insights shows actual zero recorded decisions and pending analytics. These checks are presentation checks, not hardware, sync, actuation or trained-persona acceptance.

Visual review found that the existing `PolicyDefaults.DIGEST_INTERVAL_MIN` is 120, while the old UI offered only 15/30/45/60. The final selector displays the actual current value and includes the existing default and current value alongside those existing options, without changing policy defaults or scheduler behavior. Recompiled and rebuilt the APK, installed it successfully, and inspected the selected 120-minute value. Closed only the emulator created for this review.

Screenshots and real terminal logs are under the session artifact folder `CX6-phone-review` in the Codex visualizations directory; the final visual captures are `ui-reboot-loaded.png`, `ui-light-live.png`, `ui-light-insights.png`, `ui-light-settings-open.png`, `ui-final-dark-simulate.png`, `ui-final-dark-settings.png`, `ui-final-dark-large.png`, and `ui-dark-large-settings.png`. Earlier ANR/splash captures are retained as diagnostic evidence, not successful visual results.

Physical Reno 14 rendering, notification delivery, live pairing and cloud relay behavior: **not verified on target**. This UI commit does not close the existing model-recovery readiness blocker.

Final targeted regression: `gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest --tests 'com.dhyan.app.ui.*'` — `BUILD SUCCESSFUL in 10s`, `30 actionable tasks: 3 executed, 27 up-to-date`. JUnit XML: **24 tests, 0 failures, 0 errors** (existing UI/ViewModel/settings tests plus the new contrast test), after the digest presentation fix.

Reviewed APK artifact: `Dhyan-ui-redesign-debug.apk`, SHA-256 `68CBFEF5FDDF88791F5E3B765AB627235ECCC899E5488C29820F06E3F5E18888`. No push, merge, cloud deployment or physical-device installation was performed.
