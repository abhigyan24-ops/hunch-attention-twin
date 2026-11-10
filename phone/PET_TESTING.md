# Pip test reference

## Coverage audit

The original 31 JVM tests are split across three classes, not all in PetOverlayTest:

| Class | Original coverage |
|---|---|
| PetCoreTest (11) | Facial/blink targets, speech lifetime, gravity, wall/floor restitution, spring volume/recovery, docking/tilt, dragging, frame clamp/partition, tiny bounds and invalid time. |
| PetParticleSystemTest (7) | Seeded replay, upward drift/fading/removal, expanding stationary rings, invalid inputs, bounded emissions, actual snapshot invalidation, corrupt-particle recovery. |
| PetOverlayTest (13) | 18-second sleep/wake, task suppression, transient movement focus, reaction priority/expiry, drag/dizzy behavior, sustained affection, speech expiry, monotonic clock, rub/shake classification, stale reversal windows, vertical drag and invalid positions. |

Gaps addressed by this QA change:

- Exact inclusive 900 ms versus expired 901 ms gesture windows; pixel-density equivalence; high-frequency small movement; noise/unidirectional-drag rejection; invalid classifier dimensions.
- Idle time reset by each new touch and after task end; reaction expiry must not silently extend inactivity; zero/negative reactions expire.
- Existing-session binding: null, future, expired and malformed sessions cannot activate the pet. Start is inclusive and end is exclusive. Only existing fused declared sessions drive the live wrapper; this is not inferred focus or a meditation timer.
- Real Compose pointer dispatch: inspect consumption at a child's Initial pass; inject a real button tap and a scroll through the actual overlay; verify the pet retains its own hitbox gesture; inject a head rub; advance the real Compose frame clock for inactivity/task transitions; hold/release and restore the orb.

Compose UI tests use `performTouchInput`, not semantic `performClick`/`performScrollToIndex`, because semantic actions bypass the pointer routing under test. Automatic test-clock advancement is disabled around the ongoing frame loop; tests advance frames explicitly. Exact millisecond boundaries belong in JVM tests; rendered sleep assertions allow a frame boundary after the threshold.

## Windows PowerShell commands

From this repository's `phone` directory (keep the existing Gradle wrapper):

Configure `ANDROID_HOME` to your installed Android SDK (or use an untracked `local.properties` with `sdk.dir`) before invoking Gradle. For this machine, PowerShell uses `$env:ANDROID_HOME = 'C:\Users\abhig\AppData\Local\Android\Sdk'`.

```powershell
# JVM tests for every class in the pet package.
.\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.ui.pet.*"

# A single behavior/classifier class.
.\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.ui.pet.PetOverlayTest"

# Full app JVM regression suite; independent of instrumented device tests.
.\gradlew.bat :app:testDebugUnitTest

# Compile/package the instrumented tests without running a device.
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest

# Select the intended emulator; never accidentally test every attached phone.
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.dhyan.app.ui.pet"
Remove-Item Env:ANDROID_SERIAL
```

For macOS/Linux, replace `.\gradlew.bat` with `./gradlew`, and use `ANDROID_SERIAL=emulator-5580 ./gradlew ...` for the device selection.

The serial must name an emulator/device that is actually online. Run `adb devices` first. The device task installs the debug app/test APKs on the selected target; a successful compile/package alone is not a test pass.

Explicit single-device alternative, after building the APKs:

```powershell
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -r -e package com.dhyan.app.ui.pet com.dhyan.app.test/androidx.test.runner.AndroidJUnitRunner
```

Inspect `FAILURES!!!`/instrumentation failure status as well as the test count: adb process exit alone is not proof that JUnit passed. Gradle HTML/XML reports are under `app/build/reports/tests/testDebugUnitTest`, `app/build/test-results/testDebugUnitTest`, and `app/build/reports/androidTests/connected` / `app/build/outputs/androidTest-results/connected` for the connected task.

## Setup and limits

The owner approved Compose BOM-managed `ui-test-junit4` in androidTest and `ui-test-manifest` in debug, plus AndroidJUnitRunner in androidTest. Runtime implementation dependencies, Kotlin and Compose BOM versions stay unchanged. AndroidX runner 1.7.0 is a stable release; Compose test artifacts follow this project's 2025.10.00 BOM rather than a newer testing API.

Official references: [Compose testing setup](https://developer.android.com/develop/ui/compose/testing), [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test), [device filtering in AGP](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/testing/ConnectedDeviceProvider.java).

Remaining acceptance areas include multi-pointer cancellation, orientation/inset changes, RTL, process recreation, screen-reader/haptic feel, render pixel fidelity, reduced-motion/battery profiling, and real-device refresh rate. JVM tests do not prove physical touch-through, and the pet's cosmetic FOCUSED expression is not a measured attention state. Native modal windows retain their own window priority.

Executed results for this QA session are recorded after execution in DECISIONS_pet-core.md. Physical Reno 14 behavior remains not verified on target unless explicitly recorded there.

## Results recorded in this session

- Expanded pet JVM suite: 46 tests, 0 failures (31 existing + 15 new).
- Compose instrumentation on disposable Pixel_8a API 34 emulator, explicit serial emulator-5580: `Time: 215.028`, `OK (8 tests)`. This was direct `adb am instrument`, not a successful Gradle connected-task run.
- The first Gradle connected attempt stalled during emulator System UI ANRs and was stopped; it is not a passing result. The successful retry used four cores, a smaller 480x960 framebuffer and no concurrent Gradle build.
- Full app JVM regression: 381 tests, 1 failure, 0 errors. The unchanged `Stage4BParameterRecoveryTest.rahulFourteenDayReplayRecoversPickupProbWithinEightPercent` estimates 0.7247279352031226 against required [0.54, 0.7]. Its model and threshold were not altered by this UI task.
- Debug app and instrumentation APKs assembled. Test APK packaging initially needed uncached transitive artifacts; the online retry succeeded (`BUILD SUCCESSFUL in 2m 46s`).
- Root schema sync, twin purity and persona-import checks passed. Physical device, haptic feel and performance remain not verified on target.

Subsequent startup correction: see `DECISIONS_pet-startup.md`. The owner's connected phone exposed a version-4 database / version-3 APK mismatch and a separate chart endpoint crash. The corrected APK preserves version 4. Three new instrumented regressions passed on that phone (database reopening, migration and chart drawing); 48 selected JVM tests passed. These checks supersede the earlier no-device claim only for those startup/regression scenarios, not haptic feel, battery or performance.

Run the startup regressions explicitly after building/installing both APKs:

```powershell
adb -s <serial> shell am instrument -w -r -e class com.dhyan.app.data.DatabaseCompatibilityTest,com.dhyan.app.ui.components.DistributionCanvasTest com.dhyan.app.test/androidx.test.runner.AndroidJUnitRunner
```
