# Spirit foundation implementation decisions

Scope: the owner's latest request is PetModels.kt and PetPhysicsEngine.kt only, plus required focused tests. No overlay, timer, gesture wiring, system-wide permission, dependency, frozen contract or business-model changes.

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
