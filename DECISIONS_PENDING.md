# Decisions pending — consolidated Stage 1A–1F

Owner clarification on 2026-10-25 authorizes initialization of AGENTS.md, docs/SPEC.md, and governance files from the supplied guidance and attachment. These are additional to the original create-only list. npm install necessarily generates package-lock.json; retain it for reproducible development dependencies.

## Clarifications applied

- Hour bands: night, morning, afternoon, evening, late (8.3).
- Active declaredSession: { task, plannedMin, strict, startedTs }; inactive: null (owner clarification supplements the attachment's null-only example).
- declare_session args: { task, plannedMin, strict }; show_now args: { notifKey } (owner clarification).
- notifCat remains a nonempty string, as permitted by owner; no invented closed enum. Examples are synthetic classification tokens.
- ingestedTs is optional in emission schema as explicitly requested in Task 1A, despite 25.9's all-events wording. Receiver persistence must populate it.

## Open contract decisions (resolve before dependent integration)

| ID | Unspecified detail | Current conservative contract / pending action |
|---|---|---|
| D1 | videoState vocabulary | Optional property is false-schema: any supplied value is rejected. Owner must define enum before browser integration. |
| D2 | encrypted fields, key agreement, nonce/metadata/replay design | Reserved marker only, with no data fields. No encryption capability. Section 25.8 overrides security claims based on PBKDF2 of a short code. Stage 8 must resolve. |
| D3 | end_session/kill_switch arguments | Empty objects only; reject all unapproved arguments. Define any enable/disable or session identifiers before actuation. |
| D4 | command acknowledgement wire format and authorization identity | No new frame invented. ack remains telemetry upToSeq only; command acknowledgements must be explicitly specified before integration. |
| D5 | optional protocol fields and exact state.declared shape | Flat frames use all listed fields; state requires at least one device state. declared currently shares nullable active-session shape, conservatively reflecting owner clarification. Confirm before peer integration. |
| D6 | gap.deviceFrom/deviceTo semantics and reason vocabulary | Device enum phone/laptop; reason is a nonempty classification token. Confirm whether from/to were intended as interval endpoints rather than device names. |
| D7 | notifCat/reason classification token registry | Nonempty strings only, never raw content. Define canonical values and enforcement before production collection. |
| D8 | task vocabulary in declared_session | Reuse ContextSnapshot.taskType enum; no free-text tasks. Confirm whether additional categorized tasks are needed. |
| D9 | clock changes, identity reset, late events, stale thresholds, retention and frame byte caps | Documented responsibilities, no invented thresholds or runtime implementation. Section 25.9 requires explicit decisions before persistence/sync. |
| D10 | starter package/process/domain maps | Starter rules only; uncertain Android package kept in androidPackageNotes with verify=true. Installed packages and classification accuracy not verified on target. |
| D11 | source/type provenance | Collector source or persona allowed for each type. Confirm whether system replay should preserve original source and whether persona requires synthetic=true. |
| D12 | Stage 1A freeze vs unresolved contracts | 1.0.0 is a reviewable baseline, not permission to silently fill pending fields. Gate remains pending for dependent contracts and all unimplemented Stage 1 work. |

All future features remain in scope. No post-demo postponement has been selected; STAGE_GATES.md contains the required dated completion template.


## Stage 1F consolidation — 2026-10-25

The owner explicitly authorized merging Stage 1B–1E notes here and deleting temporary DECISIONS_1*.md files. The sections below preserve those notes verbatim. Their historical command results refer to their original task sessions; current Stage 1F command results are in STAGE_GATES.md. No canonical schema was changed or approved by this consolidation.

### Still pending for owner/dependent work

- Live cloud WSS echo, Reno 14 APK install, live ActivityWatch buckets and explicit owner schema freeze: pending: owner verification required. Stage 1 is not closed by local tests alone.
- D1–D12 above remain pending where they require clarification. In particular: optional videoState vocabulary; encrypted frame/crypto/pairing design; command acknowledgement/authorization/replay/idempotency; gap endpoint semantics; stale-context/retention/identity reset and recovery policies.
- Electron GUI binary and Windows click-through/fullscreen behavior remain unverified. The addon-free Windows extraction adapter and repository build layout are explicit prototype constraints, not a packaged cross-platform installer.
- Kabir/Anika parameter choices below are fictional test assumptions, never measured user results or canonical population priors. Live API dataset generation/semantic label quality are not verified; only offline/mock paths are tested.
- Room/JSONL persistence, all seven Pip behaviors, collectors, 14-day persona replay, model calibration/counterfactual estimation, extensions and every later product feature remain in scope.

### Consolidation provenance

| Former note file | SHA-256 before removal |
|---|---|
| relay/DECISIONS_1B.md | b5fe36365464ae55325a48c0a7a41293579143ee5f81702d665318c80284507c |
| laptop/DECISIONS_1C.md | 3568400ea975ed147c067b05405fcfb9e2186b511cb1d2d4e47f7eec0c8c8a1f |
| phone/DECISIONS_1D.md | 7259b805d33c2039031e392cd97f0058855f10c6e83c363248b58e85b856fce7 |
| tools/DECISIONS_1E.md | ea05084a735d8dcc5b0bf1fb539aa9abf72bc98c98bc5e46534ea4a2e87f967f |

### Preserved source: relay/DECISIONS_1B.md

# Stage 1B decisions and limits

- Frozen Stage 1A schema is enforced with local Ajv dependencies alongside ws; root package and core-spec remain untouched. Invalid known-type frames use 4004. This strengthens the task's type allowlist to satisfy 25.8; arbitrary JSON is never forwarded.
- encrypted is a reserved marker only. Actual Stage 8 ciphertext forwarding awaits the shared contract; no opaque field names or crypto format invented.
- Flat frame shape follows the canonical schema. Type ping/pong are application messages, distinct from WebSocket control frames. Binary messages and invalid UTF-8 close 4003.
- Idle means time since join or last valid application frame. Invalid frames do not refresh activity. Rooms retain no bodies and expire at TTL; a new role join reuses the room until expiry. Case-insensitive hex room identifiers normalize to lowercase. Replacement close handlers cannot remove the new role.
- Room hash possession and opposite-role membership implement prototype routing only, not authenticated identity. Optional authorizeCommand can restrict commands; expiry is checked. Client actuation must add confirmed peer identity, replay/idempotency and acknowledgements before integration. No undefined command-ack frame invented.
- The relay has no storage; disconnect/restart loses rooms. Sender has no acknowledgement from relay, and no offline forwarding guarantee. Receiver clients remain responsible for event dedup/backfill and command handling under 25.9.
- Deployment scripts/templates are prepared, not executed against cloud. Cloud WSS, Node 20, Linux service/Caddy, Stage 8 crypto: not verified on target. Local execution uses the runtime recorded in the handoff.


### Preserved source: laptop/DECISIONS_1C.md

# Stage 1C decisions

- Scaffold only: JSONL functions throw explicit TODO errors; no persistence success or recovery is implied. State defaults to offline, and all seven Pip behaviors remain planned. Relay client is an interface, not connected telemetry.
- The main process reads canonical SCHEMA_VERSION; sandboxed preload receives that value through an additional process argument and exposes only the schema and boolean click-through method. IPC validates sender/frame and boolean type.
- RawLaptopEvent excludes content fields and carries categories/counts/known process identifiers only. No ActivityWatch event endpoint is queried by check:aw. Existing ActivityWatch itself may store content; it is outside this adapter boundary.
- Process/domain matching is case-insensitive exact matching; unrecognized values return unknown. No URL parsing or path storage.
- Rollup is overridden with @rollup/wasm-node to avoid native .node addons. Electron and esbuild executables are required tooling, not Node C++ addons. No better-sqlite3 or node-gyp dependency is declared. A recursive installed-tree scan must confirm zero .node files.
- Electron-vite builds main/preload/renderer; preload explicitly uses index.cjs under the ESM package. Tests bundle the actual normalizer with esbuild before importing it under node:test, so Node 20 needs no TypeScript loader.
- Packaged installer layout is not implemented: out/main reads core-spec from the repository. Windows click-through is scaffolded per 12.5; behavior over fullscreen games and interactive Electron UI not verified on target.
- Dependencies are pinned by local package-lock.json. Official setup reference: https://electron-vite.org/guide/ .

## Installation limitation observed in this session

The default npm install stalled in Electron 38.8.6 postinstall with a zero-byte runtime download and was interrupted. A direct official GitHub download timed out after 180004 ms with 41955996/136432758 bytes. Four bounded range downloads also timed out (one reported 10063488/23619191 bytes after 300004 ms). No partial archive was extracted or trusted. To complete source/build dependency setup, npm install was run with ELECTRON_SKIP_BINARY_DOWNLOAD=1. Electron's GUI runtime is not installed; npm run dev cannot be considered ready until npm exec install-electron successfully downloads the current version. No Electron GUI execution is claimed. Download fragments are ignored under .cache/.

## Patched Electron and zero-add-on installer

An audit of the initial Electron 38 dependency reported two high-severity packages. Electron was updated to 45.0.0; its stock Rust installer extractor shipped nine .node files. The explicit zero-.node constraint therefore requires overriding @electron-internal/extract-zip with vendor/electron-extractor. That Windows-only adapter exports the same extract(zipPath,{dir}) API, calls the operating system's Expand-Archive with absolute paths passed through environment variables, and hides its child window. It only runs after the official Electron downloader validates the archive checksum; it is not a general untrusted-archive API. A real small archive round-trip test covers the adapter. Rollup still uses WebAssembly. No native Node addon is used.

Final npm install (without the skip flag, with patched Electron and the adapter) completed with zero audit vulnerabilities. The Electron 45 package uses an explicit install-electron command rather than the earlier package's postinstall; its GUI binary remains uninstalled. Recovery: run npm exec install-electron, then npm run dev when the official archive can be downloaded. Download attempts for Electron 38 were setup troubleshooting only, and no archive fragments were extracted. This does not block TypeScript/build/unit tests, but GUI readiness remains unverified.

The extractor is also declared as a direct local development dependency for reproducible resolution. Electron's override is relative to its package directory (`file:../../vendor/electron-extractor`); a stale initial relative-link lock entry was regenerated. Clean `npm ci`, typecheck, build and all eight tests passed, including an assertion that Electron resolves the same adapter. The final scan follows directory links and found zero `.node` files and zero `node-gyp` directories.


### Preserved source: phone/DECISIONS_1D.md

# Stage 1D decisions

- Installed Android SDK discovered at C:/Users/abhig/AppData/Local/Android/Sdk: platform android-37.0 (source.properties API 37.0), Build Tools 36.0.0. compileSdk uses release(37) minorApiLevel=0 to match the installed platform identifier; targetSdk=37, minSdk=26. Fallback 34 was not needed.
- AGP 9.2.1 supports API 37.0 and Gradle 9.4.1; Gradle 9.4.1 is installed on PATH. Built-in Kotlin is used (no obsolete kotlin-android plugin); Compose compiler 2.3.10 and KSP 2.3.10 are explicit. See official references: https://developer.android.com/build/releases/agp-9-2-0-release-notes and https://developer.android.com/build/migrate-to-built-in-kotlin and https://kotlinlang.org/docs/ksp-overview.html .
- Wrapper generated with installed Gradle 9.4.1 using an initial settings-only project, then Android build configuration added. Distribution is the official Gradle 9.4.1 binary archive.
- Commands use installed JDK 21.0.11 via JAVA_HOME and a workspace-local Gradle user home. Default java launcher/Gradle native cache and sandbox localhost daemon access initially failed; direct JDK and approved daemon access used. No machine-specific path committed in local.properties. Set ANDROID_HOME to your SDK when building outside Android Studio.
- All required package folders are present; twin contains placeholders only and no Android imports. No collectors/services/onboarding are implemented. Source manifest declares only INTERNET and POST_NOTIFICATIONS; library manifests may add components during dependency manifest merging, so no service behavior is implemented or claimed.
- Room/KSP, WorkManager, Coroutines, OkHttp, Koin and Compose/Material 3 are declared for later modules; no telemetry is persisted, synced or actuated. UI only shows Dhyan and the schema. Notification runtime request is not part of this skeleton.
- Hardware behavior, Reno 14 compatibility, listener/actuation and emulator UI are not verified on target. Actual build/test results are recorded in HANDOFF_STATUS.md and this task's verification notes after execution.

## Verified build result

The wrapper's official archive download timed out. The existing C:/gradle-9.4.1-bin.zip matched the official SHA-256 2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb fetched from services.gradle.org, so it was copied into the workspace wrapper cache. Wrapper configuration now pins that checksum. A retry with incorrectly quoted PowerShell -D options failed with Task '.gradle.internal.http.connectionTimeout=15000' not found; quoting corrected it.

Actual wrapper command (JAVA_HOME=JDK 21.0.11, ANDROID_HOME=installed SDK, JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true): gradlew.bat --gradle-user-home .gradle --no-daemon '-Dorg.gradle.internal.http.connectionTimeout=15000' '-Dorg.gradle.internal.http.socketTimeout=15000' assembleDebug testDebugUnitTest. Exit 0: BUILD SUCCESSFUL in 5m 3s; 43 actionable tasks: 43 executed. SchemaVersionTest XML confirms 1 test, 0 failures, 0 errors, 0 skipped. Debug APK: app/build/outputs/apk/debug/app-debug.apk, 12748826 bytes.

Build warning: Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so. This did not fail the build. No APK installation, emulator execution, device UI or notification check was run; not verified on target.


### Preserved source: tools/DECISIONS_1E.md

# Stage 1E persona and dataset decisions

## Provenance and schema

- Rahul: all values in pickupProb, dwell, resume, fatigueMultiplier, focusPeaks, selfInitiatedPerHour and importantSenders exactly match 15.1. The extra required role is developer from 15.2. No assumed flag is needed for his specified values.
- Kabir: only selfInitiatedPerHour=4.5 is fixed by 15.2. Assumed pickup probabilities social_notif|coding=0.10, msg_known|coding=0.12, promo|coding=0.03; dwell mu/sigma social=2.0/0.7, short_video=2.3/0.8, games=3.2/0.6; resume coding=1.5/0.5, gaming=0.8/0.4; fatigue=[1,1.2,1.5]; peaks=[10,15,22]; importantSenders=[family_a,teammate_a].
- Anika: every numeric value is assumed: pickup lecture_watch|studying=0.04, shorts|studying=0.78, social_notif|studying=0.58, msg_known|studying=0.38, promo|studying=0.08; dwell learning=3.0/0.35, short_video=3.2/0.85, social=2.3/0.7 (mu/sigma); resume studying=1.7/0.6; fatigue=[1,1.15,1.4]; peaks=[21,22,23,0]; selfInitiatedPerHour=1.2; importantSenders=[family_a,study_group_a].
- lecture_watch and shorts are descriptive hidden-fixture contexts, not changes to canonical category/surface enums. Low lecture pickup is a drift proxy, not an implemented intentionality model. All duration logs use minutes. No synthetic probability is a calibrated real-person result.
- Persona _notes is a nonempty string. Role is a nonempty string; context keys are nonempty strings. Unknown top-level/distribution fields are rejected. Hidden ground truth is never imported into production modules; root check-no-persona-imports remains unchanged.

## Dataset boundary and generation

- The owner's explicit synthetic dataset request permits fictional title/body fields only in this development dataset format. It does not relax the event/relay privacy boundary. No real notification, URL, contact, title, keystroke or user log is accepted as generator input. The generator uses only a fixed synthetic prompt, count and selected model.
- Language tokens are EN and Hinglish. Dry run is 20 fixed rows: 10 of each language and four per label, covering all seven sender types. These balanced counts are test fixture design, not a population distribution or classifier benchmark. Sender/package tokens are synthetic examples, not installed-app observations.
- IDs and normalized (NFKC, case, whitespace) title/body content are deduplicated. Conflicting duplicate identities/labels are rejected instead of silently relabeled. Validation precedes writing. CLI diagnostics contain only fixed error codes/counts; no body, API response or key is logged.
- Live mode uses Responses API strict structured output, store=false, fixed endpoint and no imported user data. It requires OPENAI_API_KEY and explicit OPENAI_MODEL or --model; no model guessed. Default requested size is 2000 (15.4), fetched in bounded batches. --count permits 20..2000. Live API generation and model compatibility are not verified on target; only offline/mock tests are executed. No API key is needed for --dry-run and it must make zero network calls.
- Exact local mocked API behavior is tested without network; it is not proof of a successful paid API run. Labels are developer-assigned/generated categories requiring independent semantic review before evaluating importance/urgency. No accuracy, calibration, urgent-delay or focus-savings claims are made.
- Stage 1F will consolidate this file into root DECISIONS_PENDING.md and remove the temporary file, retaining every assumption.

## Stage 1F corrective finding — test-driven change

The first root test run failed 4/54 tests: two source-guard checks encountered EBUSY reading locked generated Gradle cache files, and two dataset-write tests encountered sandbox EPERM when atomically renaming files in sandbox temporary directories. The root full-check command was also scanning generated cache/dependency files. The persona-import guard now excludes conventional generated/dependency directories (node_modules, .gradle, .kotlin, .cache, .git, build, out, dist) while checking every owned source/config/doc file. A regression test proves generated references are excluded and a phone/collectors source reference still fails. This is the only pre-existing functional-code change in Stage 1F and was made only after observed test failures. Dataset atomic writes were not weakened; root tests were rerun with approved filesystem access for the EPERM failures. Exact initial/final outputs are retained in the Stage 1F evidence.

---

## Stage 3 Decisions and Assumptions (S3-1 through S3-16)

Documented in `STAGE3_NOTES.md` and reflected in `docs/TWIN_MODEL_CONTRACT.md`:

| ID | Assumption | Value / Rule | Code Location |
|---|---|---|---|
| **S3-1** | Notification pickup latency prior | LogNormal(mu = ln(1.0 min), sigma = 0.9) | `ModelConfig.pickupLatencyMeanLog/Sigma` |
| **S3-2** | Baseline hazard prior | Gamma(shape = 1.5, rate = 1.0 / hour) (mean 1.5 pickups/hour) | `PriorTable.baselineHazardShape/Rate` |
| **S3-3** | Fallback dwell prior | LogNormal(ln(8.0 min), 0.8) | `PriorTable.dwellPrior` |
| **S3-4** | Fallback resume prior | LogNormal(ln(5.0 min), 0.6) | `PriorTable.resumePrior` |
| **S3-5** | Destination list & Dirichlet prior | 7 destinations: `social/feed`, `short_video/shorts`, `video/watch`, `communication/chat`, `news/feed`, `shopping/other`, `games/other`. Base count 1.0, +3.0 on notifying app destination | `PriorTable.destinations`, `PriorTable.dirichletAlphas` |
| **S3-6** | `urgencyBand` thresholds | `low` < 0.33, `med` < 0.66, `high` >= 0.66 | `NotifClass.urgencyBand` |
| **S3-7** | `repeatBand` thresholds | `R0` (0 in 5m), `R1` (1 in 5m), `R2plus` (>= 2 in 5m) | `NotifClass.repeatBand` |
| **S3-8** | `fatigueBand` from fatigue index | `low` < 0.33, `med` < 0.66, `high` >= 0.66 | `FatigueIndex.band` |
| **S3-9** | `nEff` effective sample size | `nEff = n_full + 0.5 * max(0, n_parent - n_full)` | `HierarchyResolution.computeNEff` |
| **S3-10** | Evidence strength metric | `evidenceStrength = nEff / (nEff + 10.0)` in [0, 1) | `InMemoryParamStore.resolve` |
| **S3-11** | Competing risks tie-break rule | When `t_notif == t_self`, tie goes to notification event | `PairedMonteCarlo.simulate` |
| **S3-12** | CRN random stream separation | 4 independent streams: `EPISTEMIC`, `SELF`, `NOTIF`, `SELF_UNCOUPLED` derived via splitmix64 | `RngStreams.stream` |
| **S3-13** | Fail-open rule (spec 25.5) | If `InMemoryParamStore.isLoaded` is false, `TwinEngine` returns `NotReady(MODEL_NOT_LOADED)`. Callers MUST treat as ALLOW | `TwinEngine.simulate` |
| **S3-14** | Single-owner episode rule | At most one distraction episode per arm per window. Earliest event wins. No double-counting | `PairedMonteCarlo.simulate` |
| **S3-15** | Truncation flag behavior | `truncateCostsAtWindow` caps total loss at `windowMin - t_onset`. Default is false | `PairedMonteCarlo.simulate` |
| **S3-16** | Hot path binder thread latency | JIT compiler pre-warmed via `TwinWarmup` so binder thread execution has median < 10 ms | `LatencyBenchmark.kt` |

---

## Stage 4A Decisions and Assumptions (A4-1 through A4-12)

Documented in `STAGE4A_NOTES.md` and `docs/PERSONA_WORLD.md`:

| ID | Assumption | Implementation Rule |
|---|---|---|
| **A4-1** | Persona JSON durations | In natural-log minutes (mu, sigma), matching spec 15.1. |
| **A4-2** | Notification arrival rates | Generator defaults in `PersonaDefaults` (not in original persona JSON): rates per class per hour, destination weights, work blocks, pickup latency. |
| **A4-3** | Hour-band hazard modulation | Multiplier x0.6 inside `focusPeaks` hours, x1.0 normal, x1.2 late band; composite multiplicatively when overlapping. |
| **A4-4** | Fatigue multiplier | Multiplies pickup ODDS (not probability). `odds' = odds * multiplier`. |
| **A4-5** | Glance cost and window | `c_glance = 0.4 min` and `W = 30.0 min`, matching twin contract defaults. |
| **A4-6** | Single-owner competing risks | Earliest qualifying event wins; tie goes to notification; costs not truncated at W by default (`truncateCostsAtWindow = false`). |
| **A4-7** | In-session & overlapping arrivals | Notifications during active dwell/resume marked `inSession=true`; overlapping W marked `trialEligible=false, exclusionReason=overlapping_window`. Emitted as posted, excluded from effect estimation. |
| **A4-8** | Self-initiated events | Outside notification windows: Poisson process with hour-modulated rate. Inside windows: trial's shared draw. |
| **A4-9** | Logging policies | `ShowAll` (default, pi=1.0) and `RandomizedHold(eps, maxImportance)`. Held notifications realize SUPPRESS arm. Propensity logged honestly. |
| **A4-10** | Simulation worlds | `MATCHED` (exact competing risks model matching twin contract) and `MILD_MISSPEC` (drift +-15% across days, 10% heavy-tail dwell x3). |
| **A4-11** | Laptop timeline | Generated as work-state windows, `window_change`, `idle_start/end`, and `resume_first_edit`. `edit_burst` deferred. |
| **A4-12** | Test fixtures | Written to `phone/app/build/dhyan-fixtures/` during test execution, never checked into git. |

