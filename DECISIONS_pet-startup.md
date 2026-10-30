# Pet APK startup compatibility fix — 2026-10-30

## Evidence and scope

The owner reported immediate termination after installing the Spirit APK. With the owner's connected phone, the crash buffer identified `IllegalStateException: A migration from 4 to 3 was required but not found`. The supplied UI branch still used Room version 3, while the installed phone database was already version 4. A clean emulator install had not exercised that upgrade/replacement path.

Aligned only DhyanDatabase.kt, EventEntity.kt and the exported version-4 schema with the existing `fix-sync-blockers` branch at 4cbf07d. This preserves its canonical unique event indices and registered 3-to-4 migration; no downgrade migration, destructive fallback, database clear or schema invention was introduced. This is a compatibility correction on codex/spirit-core, not a merge/push or acceptance of the whole sync branch.

After opening the corrected database, a second actual phone crash identified `ArrayIndexOutOfBoundsException: length=80; index=80` in LogNormalDistributionCanvas (BayesianCurve.kt). The drawing loop includes indices 0..80, but its sample array only held 80 elements. Allocated 81 samples and evaluated the inclusive right endpoint. Probability/model parameters and inference are unchanged.

## Regression coverage

- DatabaseCompatibilityTest creates separate synthetic version-3/version-4 databases from the committed Room schemas, then opens them using the real Room implementation and checks that a synthetic setting survives. It never opens or deletes the owner's dhyan.db. Schema assets are androidTest-only; no new dependency.
- DistributionCanvasTest forces actual Canvas rendering through captureToImage, exercising the chart's right endpoint that previously crashed.
- Selected JVM regression includes all 46 pet tests and the two existing migration tests: 48 tests, 0 failures, 0 errors.
- Final compile/APK command: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.dhyan.app.ui.pet.*' --tests 'com.dhyan.app.data.DhyanDatabaseMigrationTest' :app:assembleDebug :app:assembleDebugAndroidTest` — `BUILD SUCCESSFUL in 55s`, `79 actionable tasks: 14 executed, 65 up-to-date`.
- `npm run checks`: schema-sync PASS (1.1.0), twin-purity PASS (453 files), no-persona-imports PASS (403 production files).

## Deployment and limits

ADB initially rejected some app/test installations with INSTALL_FAILED_VERIFICATION_FAILURE. No verifier/security setting was changed or bypassed. After owner interaction, the final debug app installed with Success and am start reported a successful COLD launch (TotalTime 1622 ms). The owner's app data was not cleared.

The updated artifact replaces CX6-phone-review/Dhyan-spirit-debug.apk. Device regression and sustained-launch results are recorded below after execution. The prior full-suite Stage4BParameterRecoveryTest failure is not fixed or rerun as part of this scoped crash correction. The inherited 3-to-4 migration's window-function compatibility on API 26/27 remains not verified on target; no claim about those devices follows from this phone check.

## Executed device regressions

The first device regression invocation passed chart rendering but exposed a fixture-parser error in the two new database tests: Room omits the optional indices member for tables without indices. Corrected the fixture reader to accept that documented shape; no production schema was changed. Rebuilt instrumentation: `BUILD SUCCESSFUL in 8s`, `55 actionable tasks: 4 executed, 51 up-to-date`.

Final direct instrumentation on the owner's connected phone (reported API 36), selecting DatabaseCompatibilityTest and DistributionCanvasTest: `Time: 1.402`, `OK (3 tests)`, each test success status 0. The separate test APK installation eventually succeeded after owner interaction. Test log: pet-startup-regression-device-final.log. No raw notification records or database exports were captured.

After instrumentation, relaunched MainActivity successfully (TotalTime 1105 ms). The same app process was still running after the observation interval (more than 30 seconds); its crash buffer contained zero fatal exceptions. This verifies the observed startup faults on the connected phone, not long-duration survival or all application flows. Saved APK SHA-256 matches the final assembled debug APK. No push or main-branch edit.
