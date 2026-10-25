# Stage gates

All ten stages in spec 17 remain planned; no feature is removed. Stage 1 is pending: owner verification required. Local Stage 1A–1F code verification is complete; the hardware/cloud/schema-approval exit gate is not complete.

| Stage | Status | Commands run / target / result | Evidence | Known failures | Unverified assumptions | Downstream blocked |
|---|---|---|---|---|---|---|
| 1 | pending: owner verification required | Root 55/55 + checks PASS; relay 12/12 + local pair PASS; laptop typecheck/build + 8/8 PASS; Android build + fresh JUnit 1/1 PASS | Exact Stage 1F commands/output below; HANDOFF_STATUS.md | Initial root failures resolved; GUI binary incomplete | Four owner checks below; Node 20 target and GUI not verified on target | Full Stage 1 exit and dependent live integration |
| 2 | complete (local) | Android build PASS; 33 files implemented: collectors (Notif, Usage, Screen, A11y), data layer (Room DB, 9 entities, 9 DAOs, RoomParamPersistence), ingest (AppCategorizer, Normalizer, ClockOffset, ForegroundService, BootReceiver); manifest + resources | Commit fa46a0c; STAGE_GATES.md; HANDOFF_STATUS.md | None | Not verified on target (Oppo Reno 14 / ColorOS background survival) | Stage 4B integration |
| 3 | complete | 45/45 JUnit tests PASS; Gate 1 warm latency median < 10ms; twin-purity PASS (132 files, 0 Android imports); twin-contract PASS (7 snapshots validated) | Commit 34e1594; STAGE3_NOTES.md; docs/TWIN_MODEL_CONTRACT.md | None | Prior tables and initial hyperparameters S3-1..S3-16; Reno 14 hardware check not verified on target | Stage 4B integration |
| 4A | complete | 57/57 JUnit tests PASS; check-personas-purity PASS (22 files); check:persona-data PASS (5 fixtures, 12,567 events); deterministic SHA-256; temporal guard passes | Commit 6c62fcf / ae65431; STAGE4A_NOTES.md; docs/PERSONA_WORLD.md | None | A4-1..A4-12 generator assumptions; not verified on target | Stage 4B (EpisodeBuilder, EpisodeCloser, learning wiring) |
| 4B | complete | 119/119 phone JUnit tests PASS (57 Personas, 45 Twin Core, 16 Stage 4B, 1 Schema); 14-day Rahul replay recovers p_pickup within 0.0084 (spec limit <= 0.08); two notifs 3 min apart closes first as superseded; check:twin PASS (167 files); check:twin-contract PASS | Commit main; STAGE4B_NOTES.md; Stage4BParameterRecoveryTest; EpisodeBuilderTest | None | not verified on target (Oppo Reno 14) | Stage 5 (Decision, CostFunction, Safety & Counterfactuals) |
| 5 | pending | Not run | None yet | No stage failure established | not verified on target | Stage 6 integration |
| 6 | pending | Not run | None yet | No stage failure established | not verified on target | Stage 7 integration |
| 7 | pending | Not run | None yet | No stage failure established | not verified on target | Stage 8 integration |
| 8 | pending | Not run | None yet | No stage failure established | not verified on target | Stage 9 integration |
| 9 | pending | Not run | None yet | No stage failure established | not verified on target | Stage 10 integration |
| 10 | pending | Not run | None yet | No stage failure established | not verified on target | Final delivery |

For each completed gate record exact commands, actual target/device and versions, output summary, named tests or relevant logs, failures, unverified assumptions, and blocked downstream work. Stop dependent integration when a gate fails. Automated checks on empty module directories do not establish platform readiness.

## Dated post-demo completion checklist

No work has been declared postponed beyond the demo. If scheduling changes, add each affected feature without deleting its traceability row:

| Date | Feature / requirement ID | Owner | Dependency | Acceptance test | Status |
|---|---|---|---|---|---|

Stage 9 VS Code extension, browser extension, Windows notifications, all Insights charts, and all other planned features require explicit completion/test evidence.

## Stage 1F close-out — 2026-10-25, Asia/Calcutta

All final command groups below exited 0. Root tests include all 41 Stage 1A tests plus 14 persona/dataset/guard tests. Tests prove only the named local behavior; synthetic fixtures are not user measurements. Root package.json and every core-spec file were hash-compared and remain unchanged. No phone/laptop/relay functional source was changed in Stage 1F.

The initial root test run had 50 passes/4 failures: locked generated Gradle-cache reads (EBUSY) and sandbox temporary rename restrictions (EPERM). The only pre-existing functional change fixes tools/check-no-persona-imports.mjs to inspect owned sources/config/docs while excluding generated/dependency directories; a regression test still rejects source imports. Atomic dataset writes were retained, and filesystem-restricted tests passed with approved execution. Initial exact output is in docs/verification/STAGE_1F_INITIAL_FAILURES.md. The earlier full root-check invocation also completed successfully, but scanned 16307 generated/dependency/source files; the corrected guard reports 53 owned files.

## Owner verification required

| Check | Status | Owner action / acceptance evidence | Blocks |
|---|---|---|---|
| Live cloud WSS echo | pending: owner verification required | Deploy/review the relay on the chosen host; run node relay/test-client.mjs wss://<live-host>/ws --pair; record endpoint, TLS target and actual PAIR PASS | Stage 1 cloud exit; live cross-device integration |
| Reno 14 APK install | pending: owner verification required | Install phone/app/build/outputs/apk/debug/app-debug.apk on the actual Reno 14; record Android/ColorOS version and visible Dhyan/schema screen | Stage 1 hardware exit; Stage 2 hardware acceptance |
| Live ActivityWatch buckets | pending: owner verification required | Start AW window/AFK watchers; cd laptop; npm run check:aw; retain bucket IDs and exit 0 (no event contents) | Live laptop collection validation |
| Owner schema freeze | pending: owner verification required | Review core-spec 1.0.0 plus the open D1–D12 contracts in DECISIONS_PENDING.md and explicitly approve the baseline or request a contract decision | Shared-contract signoff; dependent semantics |

Additional unverified work: Electron GUI binary/interactive click-through and fullscreen compatibility, relay under Node 20, cloud Linux/Caddy/systemd, authenticated pairing and Stage 8 crypto. None is inferred from a local Node 24 test. No live OpenAI API dataset request was made; only offline and mocked request paths were tested. The ~2000-row API dataset and semantic label review remain planned.

## Synthetic offline verification

Commands: node tools/personas/validate-personas.mjs; node tools/dataset/generate_notifications.mjs --dry-run; node tools/dataset/validate-dataset.mjs. Exit 0:

```text
rahul: PASS
anika: PASS
kabir: PASS
SYNTHETIC dataset: wrote 20 deduplicated valid rows.
Label counts: {"urgent":4,"important":4,"normal":4,"engagement_bait":4,"promo":4}
SYNTHETIC dataset: PASS; rows=20
Label counts: {"urgent":4,"important":4,"normal":4,"engagement_bait":4,"promo":4}
```

Final recursive installed-tree scan (following local dependency directory links), exit 0:

```text
Native .node addons: 0
node-gyp directories: 0
```

The fresh Android report phone/app/build/test-results/testDebugUnitTest/TEST-com.dhyan.app.SchemaVersionTest.xml confirms tests=1, failures=0, errors=0, skipped=0 and timestamp 2026-10-25T10:25:55.432Z. The APK is 12748826 bytes. No device installation or GUI launch is implied.

## Exact final verification commands and outputs

### Root tests

Working directory: Repository root. Exit code: 0.

```powershell
npm test
```

Exact terminal output:

```text

> test
> node --test tools/test/*.test.mjs

✔ valid fixture: app_background.json (12.4848ms)
✔ valid fixture: app_foreground.json (1.3865ms)
✔ valid fixture: declared_session.json (1.1999ms)
✔ valid fixture: edit_burst.json (1.5705ms)
✔ valid fixture: feedback.json (1.348ms)
✔ valid fixture: gap.json (1.2332ms)
✔ valid fixture: idle_end.json (1.011ms)
✔ valid fixture: idle_start.json (1.141ms)
✔ valid fixture: notif_posted.json (1.1353ms)
✔ valid fixture: notif_removed.json (1.2889ms)
✔ valid fixture: pickup.json (1.1874ms)
✔ valid fixture: resume_first_edit.json (1.0192ms)
✔ valid fixture: screen_off.json (1.0461ms)
✔ valid fixture: screen_on.json (5.2395ms)
✔ valid fixture: scroll_burst.json (1.0113ms)
✔ valid fixture: state_snapshot.json (1.1048ms)
✔ valid fixture: surface_change.json (1.0506ms)
✔ valid fixture: tab_category.json (1.0311ms)
✔ valid fixture: unlock.json (1.001ms)
✔ valid fixture: window_change.json (1.0665ms)
✔ invalid fixture: bad-schema-version.json (1.1118ms)
✔ invalid fixture: extra-payload-property.json (0.975ms)
✔ invalid fixture: missing-field.json (1.1591ms)
✔ invalid fixture: negative-duration.json (0.8767ms)
✔ invalid fixture: nested-forbidden-key.json (0.9043ms)
✔ invalid fixture: oversized-payload.json (1.376ms)
✔ invalid fixture: unknown-enum.json (1.2984ms)
✔ invalid fixture: urgency-out-of-range.json (1.1386ms)
✔ exactly 20 distinct event types and 8 negative fixtures (13.3755ms)
✔ required envelope and payload fields cannot be omitted for any event type (42.7228ms)
✔ all forbidden keys detected recursively, including arrays; diagnostics contain no content (23.2342ms)
✔ 8 KB boundary measures UTF-8 bytes of the entire event (2.3668ms)
✔ negative durations, counts, timestamps, enum values, and extra fields rejected (41.4168ms)
✔ ingestedTs optional at emission; source-specific payload and UUID enforced (1.2355ms)
✔ ContextSnapshot exact shape, enums, active declaration, and closed nested properties (2.1357ms)
✔ exact taxonomy and starter maps (1.1347ms)
✔ all 20 Appendix A rows, symbols, units, ranges, and priors preserved (14.4299ms)
✔ relay strict frame variants and command-specific allowlists (164.0209ms)
✔ all three checks pass on repository and reject violations in isolated scratch trees (15338.4252ms)
✔ root hygiene and governance preserve stage boundaries and complete spec inventory (202.9373ms)
✔ validator CLI returns failure without leaking input values (1067.0522ms)
✔ hidden persona rahul validates (3.9449ms)
✔ hidden persona anika validates (2.5333ms)
✔ hidden persona kabir validates (1.3114ms)
✔ Rahul exactly preserves every 15.1 parameter (2.6608ms)
✔ assumed personas disclose assumptions and intended synthetic regimes (5.8757ms)
✔ persona invalid probabilities, sigmas, hours, multipliers, extras and omissions fail (9.778ms)
✔ production trees do not reference hidden ground truth (33.7955ms)
✔ persona import guard excludes generated caches but still rejects owned source (13.1077ms)
✔ dry run makes zero network calls without credentials and writes 20 covering rows (15.0874ms)
✔ dry-run and validator CLIs work from another cwd with credentials absent (525.8447ms)
✔ dedup normalizes case/whitespace and rejects conflicting labels or ID collisions (0.6015ms)
✔ dataset rejects non-synthetic rows, unknown labels/senders/categories, extra fields and missing coverage (0.319ms)
✔ mocked Responses API uses fixed synthetic prompt, chosen model and no stored response (9.9082ms)
✔ live mode validates credentials/model and rejects invalid responses without writing (3.12ms)
ℹ tests 55
ℹ suites 0
ℹ pass 55
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 17838.5232
```

### Root checks

Working directory: Repository root. Exit code: 0.

```powershell
npm run checks
```

Exact terminal output:

```text

> checks
> node tools/check-schema-sync.mjs && node tools/check-twin-purity.mjs && node tools/check-no-persona-imports.mjs

schema-sync: PASS (1.0.0; 1 Kotlin version file(s))
twin-purity: PASS (5 twin file(s); absent modules are not target verification)
no-persona-imports: PASS (53 production file(s); absent modules are not target verification)
```

### Relay tests

Working directory: relay/. Exit code: 0.

```powershell
npm test
```

Exact terminal output:

```text

> test
> node --test test/*.test.js

✔ GET /healthz returns 200 ok (181.1883ms)
✔ invalid room, device and path close 4001 (28.3491ms)
✔ pair forwards bidirectionally without echo or cross-room leakage (45.4315ms)
✔ same-role replacement closes old socket 4002; old close cannot remove new role (9.1999ms)
✔ invalid JSON, array, null, binary, malformed UTF-8 close 4003 (14.2416ms)
✔ unknown type, extra content, bad schema and reserved ciphertext close 4004 (10.4738ms)
✔ oversized frame closes 1009 (6.4104ms)
✔ deep invalid JSON structure is rejected without overflowing privacy traversal (17.3271ms)
✔ sliding one-second rate limit closes 4008 and resets after window (47.571ms)
✔ fake clock expires idle rooms and active valid frames refresh TTL (12.9218ms)
✔ logs contain only short room, role, allowed type and byteLength; rejected input never leaks (10.145ms)
✔ commands respect expiry and optional peer authorization hook (8.8134ms)
ℹ tests 12
ℹ suites 0
ℹ pass 12
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 4207.3964
```

### Relay pair CLI

Working directory: relay/. Exit code: 0.

```powershell
Temporary createRelayServer({port:0}) plus CLI command printed below
```

Exact terminal output:

```text
Command: node test-client.mjs ws://127.0.0.1:61477/ws --pair
PAIR PASS: hello, ping, pong, state forwarded in both directions.
```

### Laptop typecheck, build and tests

Working directory: laptop/. Exit code: 0.

```powershell
npm run typecheck && npm run build && npm test
```

Exact terminal output:

```text

> dhyan-laptop@0.1.0 typecheck
> tsc --noEmit


> dhyan-laptop@0.1.0 build
> electron-vite build

vite v7.3.7 building ssr environment for production...
transforming...
✓ 1 modules transformed.
rendering chunks...
out/main/index.js  1.54 kB
✓ built in 386ms
vite v7.3.7 building ssr environment for production...
transforming...
✓ 1 modules transformed.
rendering chunks...
out/preload/index.cjs  0.39 kB
✓ built in 17ms
vite v7.3.7 building client environment for production...
transforming...
✓ 27 modules transformed.
rendering chunks...
../../out/renderer/index.html                   0.54 kB
../../out/renderer/assets/index-Bgb2EUS8.css    0.22 kB
../../out/renderer/assets/index-B14l9LBe.js   642.20 kB
✓ built in 1.57s

> dhyan-laptop@0.1.0 test
> node --test test/*.test.mjs

✔ Electron installer resolves the same addon-free extractor (5.6237ms)
✔ installer adapter rejects relative paths (0.7888ms)
✔ Windows installer adapter extracts a real archive with literal spaced paths (4039.4629ms)
✔ Code.exe and code.exe map to coding (0.7078ms)
✔ unknown processes map to unknown (0.3861ms)
✔ github.com maps to coding (0.336ms)
✔ unknown domains map to unknown (0.2709ms)
✔ canonical SCHEMA_VERSION is 1.0.0 (3.5694ms)
ℹ tests 8
ℹ suites 0
ℹ pass 8
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 4691.8366
```

### Phone initial cached build/test invocation

Working directory: phone/. Exit code: 0.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME='C:\Users\abhig\AppData\Local\Android\Sdk'
.\gradlew.bat --gradle-user-home .gradle --no-daemon --offline assembleDebug testDebugUnitTest
```

Exact terminal output:

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.4.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:processDebugNavigationResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:generateDebugRFile UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:generateDebugGlobalSynthetics UP-TO-DATE
> Task :app:processDebugJavaRes UP-TO-DATE
> Task :app:mergeDebugJavaResource UP-TO-DATE
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE
> Task :app:mergeLibDexDebug UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:compileDebugNavigationResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:dexBuilderDebug UP-TO-DATE
> Task :app:mergeProjectDexDebug UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols UP-TO-DATE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:packageDebug UP-TO-DATE
> Task :app:createDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assembleDebug UP-TO-DATE
> Task :app:bundleDebugClassesToRuntimeJar UP-TO-DATE
> Task :app:bundleDebugClassesToCompileJar UP-TO-DATE
> Task :app:kspDebugUnitTestKotlin SKIPPED
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest UP-TO-DATE

BUILD SUCCESSFUL in 44s
43 actionable tasks: 43 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.4.1/userguide/configuration_cache_enabling.html
```

### Phone build and fresh JUnit execution

Working directory: phone/. Exit code: 0.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME='C:\Users\abhig\AppData\Local\Android\Sdk'
.\gradlew.bat --gradle-user-home .gradle --no-daemon --offline assembleDebug testDebugUnitTest --rerun
```

Exact terminal output:

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.4.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:processDebugNavigationResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:generateDebugRFile UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:generateDebugGlobalSynthetics UP-TO-DATE
> Task :app:processDebugJavaRes UP-TO-DATE
> Task :app:mergeDebugJavaResource UP-TO-DATE
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE
> Task :app:mergeLibDexDebug UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:compileDebugNavigationResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:dexBuilderDebug UP-TO-DATE
> Task :app:mergeProjectDexDebug UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols UP-TO-DATE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:packageDebug UP-TO-DATE
> Task :app:createDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assembleDebug UP-TO-DATE
> Task :app:bundleDebugClassesToRuntimeJar UP-TO-DATE
> Task :app:bundleDebugClassesToCompileJar UP-TO-DATE
> Task :app:kspDebugUnitTestKotlin SKIPPED
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 24s
434 actionable tasks: 1 executed, 42 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.4.1/userguide/configuration_cache_enabling.html
```

---

## Stage 2 & 3 Close-out — 2026-10-25, Asia/Calcutta

### Stage 2: Phone Collectors & Data Layer
- **Implementation:** 33 files committed in `fa46a0c`.
  - AndroidManifest.xml: all required permissions (POST_NOTIFICATIONS, PACKAGE_USAGE_STATS, BIND_NOTIFICATION_LISTENER_SERVICE, BIND_ACCESSIBILITY_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, RECEIVE_BOOT_COMPLETED).
  - Collectors: `NotifCollector` (privacy-safe, salted SHA-256 sender hash, MessagingStyle extraction), `UsageCollector` (20s polling via UsageStatsManager), `ScreenReceiver` (screen_on, screen_off, pickup/USER_PRESENT), `A11yCollector` (scroll burst counts every 15s, canRetrieveWindowContent=false).
  - Ingest: `AppCategorizer` (prefix map + runtime overrides), `Normalizer` (category-only mapping, strips all text), `ClockOffset` (relay skew compensation), `DhyanForegroundService` (dataSync persistent service), `BootReceiver`.
  - Data: Room database (`DhyanDatabase`), 9 entities (`EventEntity`, `ParamEntity`, `AppLabelEntity`, `HeldNotifEntity`, `DecisionEntity`, `FeedbackEntity`, `VipEntity`, `SyncCursorEntity`, `SettingsEntity`), 9 DAOs, and `RoomParamPersistence` bridge.
- **Verification:** Android build and KSP compile exit 0. Physical Reno 14 background process survival: `not verified on target`.

### Stage 3: Twin Core (In-Memory Hot Path)
- **Implementation:** 49 files committed in `34e1594`.
  - RNG & samplers: `xoshiro256**` with splitmix64 seeding, 3 CRN stream derivation (EPISTEMIC, SELF, NOTIF, SELF_UNCOUPLED), zero-allocation Box-Muller, Gamma (Ahrens-Dieter boost), Beta, Dirichlet, LogNormal.
  - Parameters & stats: Welford running LogNormal, decayed Beta, decayed Dirichlet, Gamma hazard, hierarchical backoff (FULL -> PARENT -> GRANDPARENT -> PRIOR), canonical key builders, prior table S3-1..S3-16.
  - In-memory store: `InMemoryParamStore` with atomic snapshot resolution (`ModelSnapshot`), versioning, async persistence flush.
  - Simulator: `PairedMonteCarlo` competing risks SHOW vs SUPPRESS with CRN, single-owner attribution, `TwinEngine` fail-open facade (`Ready` vs `NotReady`), uncertainty diagnostics.
- **Verification:**
  - `gradle.bat :app:testDebugUnitTest --tests "com.dhyan.app.twin.*"`: **45/45 PASS**
  - Gate 1 demo benchmark: warm median latency = 7.07 ms (< 10 ms hot-path target).
  - `node tools/check-twin-purity.mjs`: **PASS (132 twin files, 0 Android imports)**
  - `node tools/check-twin-contract.mjs`: **PASS (7 snapshots validated against schema; TwinConfig defaults match Appendix A)**

---

## Stage 4A Close-out — 2026-10-25, Asia/Calcutta

### Stage 4A: Personas, History Generator, Replay, Temporal Guard
- **Implementation:** 33 files committed in `6c62fcf`, merged to main in `ae65431`.
  - `PersonaDef`, `PersonaLoader` (strict JSON parser, duplicate/range checks), `PersonaCatalog` (Rahul, Anika, Kabir bundled).
  - `GenRng`: splitmix64 deterministic generator with independent stream forking.
  - `HistoryGenerator`: 14-day deterministic event simulation with synthetic:true, v4-shaped deterministic UUIDs, reference-time ordering with clock skew.
  - Ground truth: `TruthRecord` with true SHOW/SUPPRESS counterfactuals (`lShowMin`, `lSuppressMin`, `deltaLMin`), `LoggingPolicyEngine` (`ShowAll`, `RandomizedHold`), `TrueEffect` simulator.
  - Replay & temporal guard: `ReplayDriver` (tsAdj ordering, dedup, tolerance), `ReplayClock` (`VirtualClock`, `ScaledRealtimeClock`), `TemporalGuard` (`GuardedEventLog` strictly enforces ts < t0, throws `TemporalLeakException`), `FastForward`.
  - Export: `EventJsonlWriter`, `TruthJsonWriter`, `DeterministicJson`, `EventJsonlReader`.
- **Verification:**
  - `gradle.bat :app:testDebugUnitTest --tests "com.dhyan.app.personas.*"`: **57/57 PASS**
  - `node tools/check-personas-purity.mjs`: **PASS (22 files scanned, 0 forbidden imports/APIs)**
  - `node tools/check-persona-data.mjs`: **PASS (5 fixtures, 12,567 events validated against event schema)**
  - Deterministic fixture SHA-256 verified identical across runs:
    - Rahul: `1719c7f4d8cd284a43375a234da526dca605af02497e6cd77e3d855a56a026a2`
    - Anika: `82b4ccf4f60c6df503910ad2349ad4957b9a01b0a7935bf354031a7216c6c738`
    - Kabir: `35920678b04b7df4adfd18db6ffd15d9b6f81ef41c6ffbce92dc330d9d608b9d`
  - Total combined Kotlin unit tests on main: **103/103 PASS (0 failures, 0 errors)**
  - Full repository checks: `npm run checks`, `npm run check:twin`, `npm run check:twin-contract`, `npm run check:personas`, `npm test` all **PASS (55/55 Node tests pass)**.


