# Spirit foundation implementation decisions

Initial milestone: PetModels.kt and PetPhysicsEngine.kt plus required focused tests. The subsequent file 3/4 milestone adds particles and procedural rendering. No overlay, timer, gesture wiring, system-wide permission, dependency, frozen contract or business-model changes.

- Coordinates are the 96.dp pet's top-left in pixels after the future host converts dp to px. Bounds describe the usable host area, not physical display dimensions including system bars.
- Unspecified visual tuning uses a 180/s² spring stiffness, 12/s damping, bounded deformation ±0.35 and 12 px edge clearance (shrinking safely on tiny hosts). These are cosmetic physics choices, not learned user parameters.
- The requested 0.985 air damping is interpreted at a 60 Hz reference and exponentiated by elapsed seconds; fixed substeps of at most 1/120 s keep motion consistent across refresh rates. Frame delta is capped at 0.033 s; no 120 FPS hardware claim is made.
- Wall restitution is 0.65, floor restitution 0.55, ground sliding friction 0.82. Low-energy floor rebounds settle below 45 px/s to avoid perpetual jitter. Edge capture requires speed below 120 px/s inside a band at most 48 px; inward tilt is ±18 degrees.
- Non-finite/non-positive deltas and invalid host dimensions do not advance simulation. Non-finite constructor coordinates/velocities recover to zero. Dragged positions stay under gesture control, bounded to the host; integration resumes on release.
- Gravity stops while docked. Disabling gravity elsewhere leaves damped inertia, suitable for the future meditation renderer. No real meditation session is inferred from coding/studying or another declared task.
- Facial targets are opaque, pastel, Material-inspired presentation values independent of composition. A blink closes only the eye aperture; the renderer will select arcs/rotating pupils from the emotion enum.
- Pet speech is transient app-authored UI text, never raw notification content and never persisted or logged by these files.

## Executed verification

- Models: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin` — `BUILD SUCCESSFUL in 1m 11s`.
- Physics: same compile command — `BUILD SUCCESSFUL in 32s`.
- Tests: `gradlew.bat --offline --max-workers=1 :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --tests com.dhyan.app.ui.pet.PetCoreTest` — `BUILD SUCCESSFUL in 59s`; 11 tests, 0 failures, 0 errors.
- No dependencies or Gradle files changed. There were no new Kotlin warnings; the existing AGP `android.sourceset.disallowProvider=false` configuration deprecation remains outside this two-file task.
- Rendering, gestures, frame-clock host integration and hardware refresh rate are not verified on target in this foundation-only task. No push or main-branch edit.

## Files 3 and 4: particles and character

- `ParticleEmitter` is seeded cosmetic state, bounded to 128 live particles and 32 per emission. Coordinates/velocities use pixels/seconds. Invalid deltas do not advance it; expired or non-finite particles are removed. Update replaces data-class entries so Compose snapshot readers observe motion; a real SnapshotStateObserver regression test verifies invalidation.
- All four particle types render through a DrawScope helper using paths, circles and vector Z glyphs. The character is a Canvas with a 96.dp default, radial aura, droplet gradient, procedural sprout, two catchlights per pupil, spring-interpolated gaze/face, counter-rotating dizzy spirals and a measured-text speech pill. No Android Paint, bitmap, external asset or new dependency.
- Look target and pet center must be in the same pixel coordinate space. An unchanged look target allows seeded 1.5–3.5-second saccades; updates reset the idle delay. Sleep and meditation suppress saccades. Inactive settled characters avoid continuous frame redraw; running effects are cancelled when composition exits. Preview mode freezes frame animation for inspectable static expressions.
- The requested character signature contains neither breathing phase nor horizontal velocity. Its meditation rings are ambient presentation, not a guided 4–7–8 timer; the sprout responds to deformation and gaze, not an invented velocity value. Explicit breathing/session/gesture event binding remains the overlay milestone.
- Speech lifetime follows the supplied duration, fades over 180 ms and is ellipsized to two measured lines. It is transient app-authored UI text. A controller should clear a finished bubble before repeating the same value; unrelated recomposition does not restart its timer.
- Added Curious, Happy, Love, Meditating, Sleepy and Dizzy previews on the requested dark background. Previews compile; actual renderer visuals and hardware refresh behavior are not verified on target in this file-only task.

Executed this milestone:

- Particle file `:app:compileDebugKotlin` — `BUILD SUCCESSFUL in 27s`.
- Canvas file `:app:compileDebugKotlin` — `BUILD SUCCESSFUL in 6s`; no new Kotlin warnings.
- `:app:compileDebugUnitTestKotlin :app:testDebugUnitTest --tests 'com.dhyan.app.ui.pet.*'` — `BUILD SUCCESSFUL in 6s`, `30 actionable tasks: 5 executed, 25 up-to-date`; 18 tests, 0 failures, 0 errors (11 core + 7 particle).
- The existing AGP configuration deprecation is unchanged. No APK, real-device or full-suite success is claimed by these targeted results.

## File 5: root overlay orchestration

- Added the requested `DhyanPetOverlay(isTaskActive, modifier, content)` with integration comments referencing the real MainActivity, DhyanTheme, DhyanMainScreen and SettingsViewModel session source. MainActivity and existing screen/business/session wiring are unchanged in this file-generation milestone.
- The ancestor observes Initial-pass pointer events and never consumes them. Only the 96.dp pet (or minimized 28.dp orb) owns gestures; its touch-target expansion is locally disabled so invisible expanded hit regions do not steal app input. Captured drags retain the original pet pointer; a second pointer cancels the pet gesture without taking that pointer. Physical touch-through is not verified on target by the unit tests.
- LayoutCoordinates converts moving-pet pointer positions into stable root coordinates for VelocityTracker; only transient UI state is retained. No touch history, coordinates, speech, tokens or raw content are logged/persisted. The minimized boolean alone is saveable.
- The full-root host reserves safe-drawing insets plus Dhyan's 64.dp toolbar and 80.dp navigation bands. Tiny hosts that cannot fit the pet omit the pet layer. Physics uses physical left/right coordinates in either layout direction. Native dialogs keep their own window priority; this is not a system-wide overlay.
- One LaunchedEffect(Unit)/withFrameNanos loop updates physics and gesture particles, with delta capped at 0.033 seconds and the existing engine's fixed substeps. Character-owned ambient particles remain separately owned and are never advanced twice. A second cancellable effect gives seeded 2.5–5.5-second blink intervals.
- Priority is direct reaction/drag, active task, recent screen movement, then idle. Active tasks select cosmetic FOCUSED and weightless physics; no meditation, streak or inferred focus measurement is fabricated. Idle sleep begins at 18 seconds. HAPPY lasts 1.5 s, LOVE 2 s (sustained by rubbing), DIZZY 2.5 s; post-fling SURPRISED lasts 0.7 s.
- Petting requires three horizontal direction changes in 900 ms, sufficient travel, and limited vertical/net displacement. Five reversals plus vigorous travel take DIZZY priority. A horizontal drag-intent zone preserves room for head rubbing; vertical drag uses the existing touch slop. Fling components are bounded to ±4000 px/s. Haptics/particles fire once per gesture reaction, not every move.
- Holding and releasing a stationary pet for 600 ms minimizes it. Touch and accessibility restoration both wake it; semantic boop/minimize/restore actions are supplied. Speech remains app-authored (including Yay! and Whoa!) and expires without repeated-move announcement spam.

Executed after every production edit: compile success in 27s, 2m 12s, and the final combined compile/test run below. No new Kotlin warnings; the pre-existing AGP deprecation remains unchanged.

Final command: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --tests 'com.dhyan.app.ui.pet.*'` — `BUILD SUCCESSFUL in 7s`, `30 actionable tasks: 5 executed, 25 up-to-date`. JUnit: **31 tests, 0 failures, 0 errors** (11 physics/models, 7 particles, 13 overlay behavior/gesture classifier). Real haptics, touch dispatch, fling feel and renderer performance are **not verified on target**. No APK/full-suite/device/push/merge claim is made.

## Owner-approved QA expansion and live app wiring

The owner explicitly approved Compose UI-test dependencies, AndroidJUnitRunner and MainActivity wiring after the dependency guardrail was raised. Added BOM-managed ui-test-junit4 in androidTest, ui-test-manifest in debug and stable AndroidX runner 1.7.0 in androidTest. No runtime implementation dependency, Kotlin version, Compose BOM, frozen contract or root package change.

MainActivity now wraps the existing DhyanMainScreen inside DhyanPetOverlay and DhyanTheme. Task activity comes from LiveContextHolder's existing fused declared-session source, covering phone declarations as well as eligible laptop declarations. The UI refreshes the existing source while a declaration exists so its own staleness rules remain authoritative; start/duration metadata is checked against the clock at one-second UI granularity. Null, future, expired, non-finite and non-positive declarations cannot manufacture a task. This does not create a meditation timer, alter focus inference, persist a task, or change actuation.

PetEdgeCaseTest adds ten JVM tests for gesture timing boundaries, density/event granularity, jitter, unidirectional drag, invalid dimensions, touch-reset inactivity and reaction expiry. PetSessionBindingTest adds five JVM tests for the live wrapper's explicit-session predicate. The original 31 tests remain unchanged. Fresh expanded JVM run: BUILD SUCCESSFUL in 41s; 46 pet tests, 0 failures.

PetOverlayComposeTest adds eight real Compose tests using pointer injection and explicit test-clock advancement: child Initial-pass consumption/button click, scrolling, head rub without displacement, idle sleep, underlying-touch wake, task suppression, pet-only consumption, and minimize/restore. Semantic click/scroll shortcuts are deliberately not used for pass-through tests. Instrumentation source compiled successfully in 38s. Device results are recorded only after execution below.

Command reference and scenario coverage are in phone/PET_TESTING.md. Tests will target only a newly launched read-only emulator; the physical phone, cloud relay, and Antigravity's worktrees remain untouched. Earlier milestone-only verification limits above are historical, not substituted for the new QA run.

Executed QA results:

- Full JVM regression command `:app:testDebugUnitTest --continue` (alongside APK assembly) executed 381 tests: 1 failure, 0 errors. Existing unchanged `Stage4BParameterRecoveryTest.rahulFourteenDayReplayRecoversPickupProbWithinEightPercent` reports 0.7247279352031226 outside [0.54, 0.7]; error 0.10472793520312262 exceeds 0.08. No model, threshold or twin code was changed to hide this failure.
- Debug app APK assembled. Offline test APK packaging needed uncached transitive test artifacts; online `:app:assembleDebugAndroidTest` succeeded: `BUILD SUCCESSFUL in 2m 46s`, `55 actionable tasks: 7 executed, 48 up-to-date`.
- First `connectedDebugAndroidTest` attempt stalled during severe emulator System UI ANRs and was stopped. Its failed Gradle result is not a test pass. No physical device was selected.
- Disposable Pixel_8a API 34 retry, emulator-5580, four cores and smaller 480x960 framebuffer: direct `adb -s emulator-5580 shell am instrument -w -r -e package com.dhyan.app.ui.pet com.dhyan.app.test/androidx.test.runner.AndroidJUnitRunner` completed with `Time: 215.028`, `OK (8 tests)`, `INSTRUMENTATION_CODE: -1`. All eight tests reported success status 0. This verifies real Compose dispatch on the emulator; it is not a successful Gradle connected-task claim.
- MainActivity was launched on that emulator; screenshot pet-qa-app.png shows the actual root screen with the companion. Initial emulator launch attempts were delayed by System UI ANRs. No claim about target performance follows from the screenshot.
- `npm run checks`: schema-sync PASS (1.1.0), twin-purity PASS (453 files), no-persona-imports PASS (401 files).
- Final compile retry requires ANDROID_HOME because this worktree has no local.properties. An initial invocation without SDK configuration failed before compilation; rerun uses this machine's installed SDK. Final result is recorded below after completion.

Artifacts/logs are under the external CX6-phone-review artifact folder: pet-qa-build-unit.log, pet-qa-test-apk-online.log, pet-qa-instrumented.log, pet-qa-instrumented-retry.log, pet-qa-app.png and Dhyan-spirit-debug.apk. The temporary emulator was shut down after testing. Physical Reno 14, real haptics, battery usage and hardware refresh rate remain **not verified on target**.

Final SDK-configured command: `gradlew.bat --offline --max-workers=1 :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.dhyan.app.ui.pet.*'` — `BUILD SUCCESSFUL in 4m 6s`, `30 actionable tasks: 5 executed, 25 up-to-date`. JUnit XML: 46 tests, 0 failures, 0 errors. No new Kotlin warning; the existing AGP source-set deprecation remains. Only feature-branch changes; no push or merge.
