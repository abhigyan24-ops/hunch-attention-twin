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
