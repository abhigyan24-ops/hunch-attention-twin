# Rename Dhyan to Hunch — Variant B (Phone, VS Code & Browser Extensions, Pitch & Demo Docs)

**Branch:** `rename-hunch-phone` (branched from `ci/release-fix` @ `54fe5b65deda7663f27ee9f0111029ee7ab8b5f8`)
**Scope:** Variant B (Antigravity scope) in `docs/prompts/HUNCH_RENAME_PACK.md`
**Milestone Status:** **M0 Complete — STOPPED FOR OWNER APPROVAL** (no application source, resource, manifest, `package.json`, contract, or existing docs modified)
**Brand Source of Truth:** `docs/BRAND.md`

---

## 1. Git State & Preconditions (`M0.1`)

- `[VERIFIED]` Working tree created at `C:\Users\abhig\OneDrive\Desktop\dhyan-rename-phone` on branch `rename-hunch-phone` from base commit `54fe5b65deda7663f27ee9f0111029ee7ab8b5f8` (`git rev-parse HEAD` -> exit code `0`).
- `[VERIFIED]` `git log --oneline -5`:
  - `54fe5b6 fix(desktop,engine): bundle contract assets and write user data to writable paths`
  - `abfb47e fix(desktop): load electron-updater through CommonJS default export`
  - `588b082 ci(desktop): track NSIS installer hook`
  - `2fa7666 ci(android): request supported SDK platform-tools`
  - `ab2fe0c ci(engine): resolve bundle inputs from PyInstaller SPECPATH`
- `[VERIFIED]` `git --no-pager branch --no-merged main` lists: `ci/release-fix`, `codex/desktop-setup`, `rename-hunch-phone`, `s9-phone`.
  - Per owner instruction (`A2` before M0), `rename-hunch-phone` branches from `ci/release-fix` (`54fe5b6`) and treats `s9-phone` and `codex/desktop-setup` as superseded/already covered.
- `[VERIFIED]` Repository structure pre-flight checks:
  - `docs/BRAND.md` and `docs/prompts/HUNCH_RENAME_PACK.md` did not exist prior to M0 and were created in M0 per owner decision `A1`.
  - `docs/DEMO_SCRIPT.md`, `docs/QA_CHEATSHEET.md`, slides, and `.pptx` decks do not exist in this repository (`git ls-files`); the repository uses `docs/dhyan-pitch.md` and `docs/DEMO_RUNBOOK.md`.
  - `tools/rename-guard.mjs` does not exist yet (will be created in M2 after M0/M1 approval).
- `[VERIFIED]` Local workspace note: `phone/local.properties` exists on `IIITN` for Android SDK path configuration (`Test-Path .\phone\local.properties` -> `True`). Tracked source tree in `dhyan-rename-phone` is clean relative to `54fe5b6` aside from M0 documentation/baseline additions.

---

## 2. Inventory & Classification Summary (`M0.2` – `M0.3`)

Two independent scans over the Variant B scope (`phone/`, `vscode-ext/`, `browser-ext/`, `docs/DEMO_RUNBOOK.md`, `docs/dhyan-pitch.md`):
- `git grep -n -I -i "dhyan" -- phone/ vscode-ext/ browser-ext/ docs/DEMO_RUNBOOK.md docs/dhyan-pitch.md | Measure-Object -Line` -> `1,908` lines (`exit code 0`).
- Python full-UTF-8 regex scan `(dhyan|ध्यान)` (case-insensitive) over all 422 tracked files in Variant B scope -> `1,908` hits (`exit code 0`), **0 unclassified**.
  - `[VERIFIED]` Devanagari `ध्यान` (`\u0927\u094d\u092f\u093e\u0928`) appears on exactly 2 lines (`docs/dhyan-pitch.md:4` and `vscode-ext/README.md:3`), both of which also contain ASCII `Dhyan`.

### Summary Counts per Class

| Classification | Total | Action in M1 | Breakdown by Area & Disposition |
|---|---:|---|---|
| **USER-VISIBLE** | **75** | **66 change / 9 ask first** | `phone/` change: `11`, `phone/` ask_owner: `7`; `vscode-ext/` change: `53`, `vscode-ext/` ask_owner: `2`; `browser-ext/` change: `2` |
| **IDENTIFIER** | **1,600** | Keep (`0` change) | `phone/`: `1,261` (`1,108` pkg/import, `153` symbols/channels/CSS/tags); `vscode-ext/`: `334`; `browser-ext/`: `3`; `docs/`: `2` (`dhyan-pitch.md:99,199`) |
| **PROTOCOL CONSTANT** | **11** | Keep (`0` change) | `phone/`: `SyncCrypto.kt` (`8`), `phone/app/src/test/resources/test-vectors.json` (`2`), `phone/README-sync.md:9` (`1`) |
| **STORED-DATA NAME** | **31** | Keep (`0` change) | `phone/`: `26` (Room DB, schemas, backup XMLs, SharedPrefs, Keystore aliases, salt); `vscode-ext/`: `5` (`.dhyan/endpoint.json`, SecretStorage, workspaceState) |
| **TEST FIXTURE** | **90** | **86 keep / 4 ask first** | `phone/` keep: `43`, `phone/` ask_owner (`PetOverlayComposeTest.kt`): `3`; `vscode-ext/` keep: `43`, `vscode-ext/` ask_owner (`activation.test.ts:103`): `1` |
| **DOC** | **98** | **45 change / 42 keep / 11 ask first** | `docs/` user-facing change: `42` (`dhyan-pitch.md:25`, `DEMO_RUNBOOK.md:17`); `vscode-ext/README.md` change: `3`; `phone/*.md` ask_owner_or_keep: `11`; internal code comments keep: `42` (`phone`: `27`, `vscode-ext`: `13`, `browser-ext`: `2`) |
| **HISTORICAL** | **3** | Keep (`0` change) | `phone/MANUAL_CHECKS-gate3.md:26,34,41` (`C:\Users\...\dhyan` / `dhyan-s8p` worktree paths) |
| **Grand Total** | **1,908** | — | **0 unclassified** |

---

## 3. Complete `USER-VISIBLE` Inventory (`75` hits)

### 3A. Phone (`18` hits: `11` in-bounds + `7` ask-first)

| # | `file:line` | Current Text | Status | Proposed M1 Change |
|---:|---|---|---|---|
| 1 | `phone/app/src/main/AndroidManifest.xml:33` | `android:label="Dhyan"` | In-bounds (`change`) | `android:label="@string/app_name"` (`Hunch`) |
| 2 | `phone/app/src/main/AndroidManifest.xml:59` | `android:label="Dhyan Notifications"` | In-bounds (`change`) | `android:label="@string/notif_listener_label"` (`Hunch Notifications`) |
| 3 | `phone/app/src/main/AndroidManifest.xml:70` | `android:label="Dhyan Accessibility"` | In-bounds (`change`) | `android:label="@string/a11y_service_label"` (`Hunch Accessibility`) |
| 4 | `phone/app/src/main/res/values/strings.xml:2` | `<string name="app_name">Dhyan</string>` | In-bounds (`change`) | `<string name="app_name">Hunch</string>` |
| 5 | `phone/app/src/main/res/values/strings.xml:3` | `<string name="a11y_service_description">Dhyan tracks scroll counts and app switches to model your attention. It never reads screen content.</string>` | In-bounds (`change`) | `<string name="a11y_service_description">Hunch tracks scroll counts and app switches to model your attention. It never reads screen content.</string>` |
| 6 | `phone/app/src/main/res/values/strings.xml:4` | `<string name="notif_channel_name">Dhyan Service</string>` | In-bounds (`change`) | `<string name="notif_channel_name">Hunch Service</string>` |
| 7 | `phone/app/src/main/res/values/strings.xml:5` | `<string name="notif_channel_desc">Dhyan attention monitoring runs in the foreground</string>` | In-bounds (`change`) | `<string name="notif_channel_desc">Hunch attention monitoring runs in the foreground</string>` |
| 8 | `phone/app/src/main/res/values/strings.xml:6` | `<string name="notif_title">Dhyan is active</string>` | In-bounds (`change`) | `<string name="notif_title">Hunch is active</string>` |
| 9 | `phone/app/src/main/java/com/dhyan/app/ui/DhyanMainScreen.kt:62` | `Text("Dhyan", style = MaterialTheme.typography.titleLarge)` | In-bounds (`change`) | `Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)` (`"Hunch"`; also see Question Q4 on subtitle line 63) |
| 10 | `phone/app/src/main/java/com/dhyan/app/ui/DesignPreviews.kt:32` | `Text("Dhyan", style = MaterialTheme.typography.titleLarge)` | In-bounds (`change`) | `Text(HunchBrand.PRODUCT_NAME, style = MaterialTheme.typography.titleLarge)` (`"Hunch"`) |
| 11 | `phone/app/src/main/java/com/dhyan/app/ui/viewmodel/SettingsViewModel.kt:223` | `summaryLine = "test · Dhyan debug tool",` | In-bounds (`change`) | `summaryLine = "test · ${HunchBrand.PRODUCT_NAME} debug tool",` |
| 12 | `phone/app/src/main/java/com/dhyan/app/actuator/DhyanTileService.kt:36` | `tile.label = "Dhyan"` | **`[ASK FIRST — actuator/]`** | `tile.label = getString(R.string.app_name)` (`"Hunch"`) — see Q1 |
| 13 | `phone/app/src/main/java/com/dhyan/app/actuator/digest/DigestBuilder.kt:40` | `val summary = items.joinToString(", ") { it.appCat }.ifEmpty { "Dhyan digest" }` | **`[ASK FIRST — actuator/]`** | `...ifEmpty { HunchBrand.DIGEST_FALLBACK_SUMMARY }` (`"Hunch digest"`) — see Q1 |
| 14 | `phone/app/src/main/java/com/dhyan/app/actuator/digest/DigestPoster.kt:36` | `"Dhyan digest",` | **`[ASK FIRST — actuator/]`** | `context.getString(R.string.digest_channel_name)` (`"Hunch digest"`) — see Q1 & Finding F2 |
| 15 | `phone/app/src/main/java/com/dhyan/app/actuator/digest/DigestPoster.kt:39` | `description = "Batched notifications held by Dhyan"` | **`[ASK FIRST — actuator/]`** | `description = context.getString(R.string.digest_channel_desc)` (`"Batched notifications held by Hunch"`) — see Q1 & Finding F2 |
| 16 | `phone/app/src/main/java/com/dhyan/app/twin/decision/Explainer.kt:10` | `reason=="mode_off" -> "Dhyan is off."` | **`[ASK FIRST — twin/]`** | `reason=="mode_off" -> "${TwinBrand.PRODUCT_NAME} is off."` (`"Hunch is off."`; pure Kotlin, zero `android.*` imports; note `check:no-persona-imports` / `check:twin-purity` remain clean) — see Q1 |
| 17 | `phone/app/src/main/java/com/dhyan/app/ui/pet/DhyanPetCanvas.kt:153` | `contentDescription = "Dhyan Spirit, ${emotion.name.lowercase()}" +` | **`[ASK FIRST — Mascot naming]`** | `"${HunchBrand.MASCOT_NAME}, ${emotion.name.lowercase()}"` (`"Pip, ..."`) OR `"Hunch Spirit, ..."` — see Q2 |
| 18 | `phone/app/src/main/java/com/dhyan/app/ui/pet/DhyanPetOverlay.kt:325` | `contentDescription = "Restore Dhyan Spirit"` | **`[ASK FIRST — Mascot naming]`** | `"Restore ${HunchBrand.MASCOT_NAME}"` (`"Restore Pip"`) OR `"Restore Hunch Spirit"` — see Q2 |

### 3B. Browser Extension (`2` hits: `2` in-bounds)

| # | `file:line` | Current Text | Status | Proposed M1 Change |
|---:|---|---|---|---|
| 19 | `browser-ext/manifest.json:3` | `"name": "Dhyan Browser Focus Sensor",` | In-bounds (`change`) | `"name": "Hunch Browser Focus Sensor",` |
| 20 | `browser-ext/manifest.json:5` | `"description": "Privacy-preserving telemetry sensor for Dhyan (counts and categories only, zero URLs or page text)",` | In-bounds (`change`) | `"description": "Privacy-preserving telemetry sensor for Hunch (counts and categories only, zero URLs or page text)",` |

*(Note: `[VERIFIED]` `browser-ext/manifest.json` has no `"key"`, `"action"` title, or `"options_ui"`/`"options_page"` entries.)*

### 3C. VS Code Extension (`55` hits: `53` in-bounds + `2` ask-first)

| # | `file:line` | Current Text | Status | Proposed M1 Change |
|---:|---|---|---|---|
| 21 | `vscode-ext/package.json:3` | `"displayName": "Dhyan VS Code Sensor & Attention Companion",` | In-bounds (`change`) | `"displayName": "Hunch VS Code Sensor & Attention Companion",` |
| 22 | `vscode-ext/package.json:5` | `"description": "Privacy-preserving telemetry sensor, resume assist, and quiet actuator for the Dhyan attention digital twin",` | In-bounds (`change`) | `"description": "Privacy-preserving telemetry sensor, resume assist, and quiet actuator for Hunch, your attention digital twin",` |
| 23 | `vscode-ext/package.json:34` | `"title": "Dhyan",` | In-bounds (`change`) | `"title": "Hunch",` |
| 24 | `vscode-ext/package.json:44` | `"name": "Dhyan Companion"` | In-bounds (`change`) | `"name": "Hunch Companion"` |
| 25–37 | `vscode-ext/package.json:63,67,71,75,79,83,87,91,95,99,103,107,111` | Command `"title": "Dhyan: ..."` (13 commands) | In-bounds (`change`) | Replace prefix `"Dhyan: "` -> `"Hunch: "` while keeping all 13 `"command": "dhyan.*"` IDs untouched |
| 38 | `vscode-ext/package.json:127` | `"title": "Dhyan Sensor & Twin",` | In-bounds (`change`) | `"title": "Hunch Sensor & Twin",` |
| 39 | `vscode-ext/package.json:137` | `"description": "Port of the local Dhyan ingest server (127.0.0.1)"` | In-bounds (`change`) | `"description": "Port of the local Hunch ingest server (127.0.0.1)"` |
| 40 | `vscode-ext/package.json:142` | `"description": "Authentication token shown in the Dhyan laptop app"` | In-bounds (`change`) | `"description": "Authentication token shown in the Hunch laptop app"` |
| 41 | `vscode-ext/src/companionProvider.js:68` | `<title>Dhyan Companion</title>` | In-bounds (`change`) | `<title>${BRAND.name} Companion</title>` |
| 42 | `vscode-ext/src/extension.js:289` | ``this.statusBarItem.text = `$(pulse) Dhyan: ${minutes}:00 • ${preset} (100%)`;`` | In-bounds (`change`) | ``$(pulse) ${BRAND.name}: ${minutes}:00 • ${preset} (100%)`` |
| 43 | `vscode-ext/src/extension.js:398` | `statusBar.text = '$(pulse) Dhyan: 24:15 • Focused (92%)';` | In-bounds (`change`) | `` `$(pulse) ${BRAND.name}: 24:15 • Focused (92%)` `` |
| 44 | `vscode-ext/src/extension.js:399` | `statusBar.tooltip = 'Dhyan Attention Companion — Click to view sidebar or toggle session';` | In-bounds (`change`) | `` `${BRAND.name} Attention Companion — Click to view sidebar or toggle session` `` |
| 45 | `vscode-ext/src/extension.ts:490` | `` `Dhyan attention tracking is now ${isPaused ? 'PAUSED' : 'RESUMED'}.` `` | In-bounds (`change`) | `` `${BRAND.name} attention tracking is now ${isPaused ? 'PAUSED' : 'RESUMED'}.` `` |
| 46 | `vscode-ext/src/extension.ts:500` | `const ch = vscodeApi.window.createOutputChannel('Dhyan Diagnostics');` | **`[ASK FIRST — Output channel]`** | `createOutputChannel(BRAND.diagnosticsChannelName)` (`'Hunch Diagnostics'`) — see Q3 |
| 47 | `vscode-ext/src/extension.ts:507` | `activeChannel.appendLine('=== Dhyan Extension Diagnostics ===');` | **`[ASK FIRST — Test assertion]`** | `appendLine(BRAND.diagnosticsBanner)` (`'=== Hunch Extension Diagnostics ==='`, also update `activation.test.ts:103`) — see Q3 |
| 48 | `vscode-ext/src/extension.ts:559` | `vscodeApi.window.setStatusBarMessage("Dhyan: Today's history forgotten.", 3000);` | In-bounds (`change`) | `` `${BRAND.name}: Today's history forgotten.` `` |
| 49–51 | `vscode-ext/src/features/digest.ts:73,92,115` | `Dhyan: ${len} notification...`, `Dhyan: No held notifications...`, `Dhyan: Held Notifications (${len})` | In-bounds (`change`) | `${BRAND.name}: ...` |
| 52–53 | `vscode-ext/src/features/nudge.ts:104,118` | `` `Dhyan: ${text}` ``, `` `$(bell) Dhyan: ${text}` `` | In-bounds (`change`) | `${BRAND.name}: ${text}`, `$(bell) ${BRAND.name}: ${text}` |
| 54–57 | `vscode-ext/src/features/resume.ts:158,189,209,222` | `Dhyan: No previous interrupted...`, `` `Dhyan: Resumed at ...` ``, `'Dhyan: Park Intention'`, `'Dhyan: Intention note parked locally.'` | In-bounds (`change`) | `${BRAND.name}: ...` |
| 58–64 | `vscode-ext/src/features/session.ts:88,96,141,154,174,189,238` | `Dhyan: A focus session is already...`, `'Dhyan: Start Focus Session'`, `'Dhyan: Focus session started.'`, `'Dhyan: No focus session currently active.'`, `'Dhyan: Rate your focus...'`, `'Dhyan: Rate your current energy level'`, `` `Dhyan: Session complete (${mins}m ...` `` | In-bounds (`change`) | `${BRAND.name}: ...` |
| 65–66 | `vscode-ext/src/insightsPanel.js:24,59` | `'Dhyan: Attention & Flow Insights'`, `<title>Dhyan Attention Insights</title>` | In-bounds (`change`) | `${BRAND.name}: Attention & Flow Insights`, `<title>${BRAND.name} Attention Insights</title>` |
| 67 | `vscode-ext/src/sensorLegacy.ts:308` | ``this.statusBarItem.text = `$(pulse) Dhyan: ${minutes}:00 • Deep Focus (100%)`;`` | In-bounds (`change`) | `` `$(pulse) ${BRAND.name}: ${minutes}:00 • Deep Focus (100%)` `` |
| 68–75 | `vscode-ext/src/ui/statusBar.ts:31,32,40,41,49,50,67,68` | `'$(plug) Dhyan: waiting...'`, `'Dhyan: Desktop...'`, `'$(debug-pause) Dhyan paused'`, `'Dhyan attention tracking is paused'`, `` `$(eye) Dhyan · Deep focus...` ``, `'Dhyan: In deep focus flow'`, `'$(cloud) Dhyan: remote window not supported'`, `'Dhyan runs locally on your laptop...'` | In-bounds (`change`) | All 8 status bar strings parameterised via `BRAND.name` (`'Hunch'`) |

---

## 4. `DOC` (`45` change + `11` ask-first) & `TEST FIXTURE` (`4` ask-first) Requiring Review

### 4A. `TEST FIXTURE` (`4` ask-first hits — rest `86` keep)

| `file:line` | Current Text | Dependency | Proposed Action |
|---|---|---|---|
| `phone/app/src/androidTest/java/com/dhyan/app/ui/pet/PetOverlayComposeTest.kt:44` | `if (emotion == null) "Dhyan Spirit," else "Dhyan Spirit, $emotion",` | Asserts `DhyanPetCanvas.kt:153` semantics | If Q2 approves `"Pip"` / `"Hunch Spirit"`, update fixture (`keep` only if Q2 keeps `"Dhyan Spirit"`) |
| `phone/app/src/androidTest/java/com/dhyan/app/ui/pet/PetOverlayComposeTest.kt:199,202` | `compose.onNodeWithContentDescription("Restore Dhyan Spirit")...` | Asserts `DhyanPetOverlay.kt:325` semantics | Same as above (Q2) |
| `vscode-ext/test/integration/activation.test.ts:103` | `assert.ok(joined.includes('=== Dhyan Extension Diagnostics ==='));` | Asserts `extension.ts:507` output banner | If Q3 approves `'Hunch Diagnostics'`, update fixture (`keep` only if Q3 keeps `'Dhyan Diagnostics'`) |

### 4B. User-Facing Docs (`45` change)

1. **`vscode-ext/README.md` (`3` change hits; lines `122-132` and `138-145` KEEP `dhyan.*` and `~/.dhyan/endpoint.json`):**
   - `:1` `# Dhyan VS Code Sensor & Attention Companion` -> `# Hunch VS Code Sensor & Attention Companion`
   - `:3` `...for **Dhyan (ध्यान)** — the local-first cross-device attention digital twin.` -> include `BRAND.md` descriptor/disclaimer/codename note (`...for **Hunch** (*your attention digital twin*)...` + note that internal `dhyan.*` keys remain unchanged)
   - `:9` `The Dhyan VS Code extension serves three strictly bounded responsibilities:` -> `The Hunch VS Code extension...`
2. **`docs/DEMO_RUNBOOK.md` (`17` hits — lines `1, 16, 17, 32, 33, 34, 68, 75, 90, 105, 118, 128, 130, 139, 175, 178, 181`):**
   - Note `:16` (`3. Launch **Dhyan** (`com.dhyan.app`).`): change `**Dhyan**` -> `**Hunch**`, **KEEP** `` `com.dhyan.app` ``!
   - Note `:34, 75` (`**Dhyan Companion** panel with the Cloud Spirit pet` / `the Dhyan Cloud Spirit`): change product name to `Hunch` and confirm mascot phrasing (`Pip` / `Cloud Spirit Pip`) per Q2.
   - Keep all metrics/claims (`9,072`, `< 0.5 ms`, `1.2 minutes`, `62%`, `24 minutes`, etc.) untouched per Variant B / R5.
3. **`docs/dhyan-pitch.md` (`25` `DOC|change` hits — lines `1, 4, 10, 17, 42, 44, 46, 47, 53, 68, 69, 78, 80, 116, 243, 263, 292, 327, 363, 394, 395, 405, 407, 408, 411`; keep filename `docs/dhyan-pitch.md` per R2 and keep `:99` & `:199` identifiers untouched):**
   - `:1` -> `# Hunch: your attention digital twin` (adding tagline/disclaimer per `BRAND.md`).
   - `:4` -> Remove `*Project Codename:* **Dhyan** (Hindi: *ध्यान* — Attention / Deep Focus)` from header and place the single Codename footnote at `:411` (`*Codename footnote:* Internal package names, database/storage keys, and protocol constants retain \`dhyan\` (Hindi: *ध्यान* — attention / deep focus) for compatibility.`) per Variant B (`mark "Dhyan" origin only in a single "Codename" footnote`).
   - `:10, 17, 42, 44, 46, 47, 53, 68, 69, 78, 80, 116, 243, 263, 292, 327, 363, 394, 395, 405, 407, 408` -> Rename `Dhyan` -> `Hunch` (`DHYAN` -> `HUNCH`, keeping column alignment in the `:68-73` ASCII box), leaving every claim, citation, and verified benchmark number untouched.

### 4C. Internal QA / Developer Runbooks (`11` `ask_owner_or_keep` hits)

- `phone/MANUAL_CHECKS-gate3.md:52,67,68,87` (UI instructions referencing opening/stopping `Dhyan`)
- `phone/MANUAL_CHECKS-stage9.md:17,18,19,35` (Android Settings list items referencing `Dhyan` / `Dhyan Ingest`, note `:19` says `Dhyan Ingest` whereas manifest actually has `Dhyan Accessibility`)
- `phone/PET_TESTING.md:1` (`# Dhyan Spirit test reference`)
- `phone/README-sync.md:1,46` (`# Dhyan Sync Protocol...`, `In the Dhyan app, tap the Settings pill`)
- Ask Q5 whether to leave internal Stage 8/9 manual runbooks historical/untouched or update UI app-label steps to `Hunch`.

---

## 5. Protected Identifiers Baseline (`M0.4`)

- `[VERIFIED]` Extracted directly from repository files and saved to `docs/rename/protected-identifiers.phone-ext.json`.
- Highlights:
  - **Android Build & Manifest:** `rootProject.name = "Dhyan"` (`phone/settings.gradle.kts:3`); `namespace = "com.dhyan.app"`, `applicationId = "com.dhyan.app"` (`phone/app/build.gradle.kts:7,10`); `.DhyanApplication`, `.ingest.DhyanForegroundService`, `dhyan_attention_monitoring` (`AndroidManifest.xml:34,83,88`); `com.dhyan.app.ui.SettingsActivity` (`accessibility_service_config.xml:16`); `com.dhyan.app.STOP_SERVICE` (`DhyanForegroundService.kt:45`).
  - **Android Storage & Channel IDs:** Room DB `"dhyan.db"` (`DhyanDatabase.kt:207`); backup/extraction excludes `"dhyan_secrets.xml"`, `"dhyan_device_prefs.xml"`, `"dhyan_room.db"` (`backup_rules.xml:3-5`, `data_extraction_rules.xml:4-6,9-11`); SharedPreferences `"dhyan_secrets"`, `"dhyan_device_prefs"`, `"dhyan_pet_telemetry"`; Keystore aliases `"dhyan_held_text"`, `"dhyan_secret_key_v1"`, `"dhyan_attwin_id_v1"` (`CN=DhyanPhone`), `"dhyan_sync_master_key"`; VIP default salt `"dhyan_salt_default"`; Channel IDs `"dhyan_digest"`, `"dhyan_service"`, `"dhyan_interception_test"`; WorkManager tags `"dhyan_release"`, `"dhyan_sweep"`, `"dhyan_periodic_sweep"`.
  - **Absent Android Features (verified 0 hits):** DataStore, FileProvider authorities, custom `android:scheme` deep links.
  - **Protocol Constants & Vectors:** All 8 `SyncCrypto.kt` HKDF/AAD strings (`dhyan-rendezvous-v1`, `dhyan-pair-v1`, `dhyan-pair-confirm-v1`, `dhyan-pairing-key-v1`, `dhyan-room-v1`, `dhyan-hello-mac-v1`, `dhyan-session-v1`, `dhyan|1|`); `contracts/sync/test-vectors.json` and `phone/app/src/test/resources/test-vectors.json` both have SHA-256 `00d06f1913b4b3956613cb8042b56d6d7d2e0cbf490288eb6003f818056787be`.
  - **VS Code & Browser Extensions:** `name="dhyan-vscode"`, `publisher="dhyan"`, container `"dhyan-container"`, icon `"resources/dhyan-icon.svg"`, 4 view IDs, 13 `dhyan.*` command IDs, 13 `dhyan.*` setting keys, `"~/.dhyan/endpoint.json"`, `"dhyanInsights"`, SecretStorage `"dhyan.token"` & `"dhyan.privacy.salt"`, workspaceState `"dhyan.lastSnapshot"`, CSS selector `.dhyan-squircle-btn` asserted by `tools/test/vscode-ext.test.mjs:145`; Browser `manifestKeyField: null`, fallback ID `'dhyan-mock-extension-id'`.

---

## 6. M0 Item 5 (`userData`) & M1 String Centralisation Plan (`M0.5` – `M0.6`)

- **M0.5 (Electron `userData`):** Out of Variant B scope (`laptop/` is Variant A).
- **M0.6 Centralisation Plan for M1 (pending approval):**
  1. **Android (`phone/`):**
     - Centralise resource strings in `phone/app/src/main/res/values/strings.xml`: `app_name` (`"Hunch"`), `brand_descriptor` (`"your attention digital twin"`), `brand_tagline` (`"Knows what that notification will cost you."`), `brand_disclaimer` (`"Unrelated to other products named Hunch. A working name for a prototype."`), `brand_codename_note`, `about_title` (`"About Hunch"`), `notif_listener_label` (`"Hunch Notifications"`), `a11y_service_label` (`"Hunch Accessibility"`), `a11y_service_description`, `notif_channel_name` (`"Hunch Service"`), `notif_channel_desc`, `notif_title` (`"Hunch is active"`), plus (if Q1 approves) `digest_channel_name`, `digest_channel_desc`, `qs_tile_label`, and (if Q2 approves) `pet_name` (`"Pip"`).
     - Reference `@string/...` from `AndroidManifest.xml:33,59,70` and `stringResource(...)` / `context.getString(...)` in Compose/services.
     - Add one non-resource Kotlin constants object (`com.dhyan.app.ui.brand.HunchBrand`) for non-Context callers (`SettingsViewModel.postTestNotification()`, `DesignPreviews.kt`) and (if Q1 approves updating `Explainer.kt:10` / `DigestBuilder.kt:40` without importing `ui.*` or `android.*` into `twin/`) keep a pure Kotlin constant inside `twin/decision/` so `check:twin` purity (`no android.*`, `no androidx.*`) holds!
     - Add an **About Hunch** section (`SettingsCard(stringResource(R.string.about_title))`) inside `SettingsBottomSheet.kt` displaying **Hunch**, **your attention digital twin** (descriptor), **Knows what that notification will cost you.** (tagline), **Unrelated to other products named Hunch. A working name for a prototype.** (disclaimer), and codename compatibility note (see Q4 for first-run/main-screen descriptor placement).
  2. **VS Code Extension (`vscode-ext/`):**
     - Manifest UI display strings updated directly in `vscode-ext/package.json` (`displayName`, `description`, `viewsContainers` title, `views` name, 13 command `title`s, `configuration.title` and descriptions), while keeping all IDs/keys unchanged.
     - Add ONE runtime brand module `vscode-ext/src/brand.js` (plus matching type declaration file `vscode-ext/src/brand.d.ts` so `tsc --noEmit` with `strict: true` / `NodeNext` passes without needing `allowJs: true` in `tsconfig.json`). Both pure-ESM `.js` files (`extension.js`, `companionProvider.js`, `insightsPanel.js` imported by `tools/test/vscode-ext.test.mjs`) and TypeScript `.ts` files (`extension.ts`, `sensorLegacy.ts`, `statusBar.ts`, `features/*.ts` bundled by `esbuild`) import `HUNCH_BRAND` from `./brand.js`.
  3. **Browser Extension (`browser-ext/`):**
     - Centralised directly in `browser-ext/manifest.json` (`name` and `description`); no UI HTML/options pages exist in `browser-ext/`.

---

## 7. Findings Table (G13)

| ID | Status | Evidence | Planned Action / Resolution | Test / Guard |
|---|---|---|---|---|
| **F1** | `REPRODUCED` | `phone/app/src/main/java/com/dhyan/app/twin/decision/Explainer.kt:10`, `actuator/DhyanTileService.kt:36`, `actuator/digest/DigestBuilder.kt:40`, `actuator/digest/DigestPoster.kt:36,39` contain user-visible `"Dhyan"` literals inside Variant B ask-first packages (`twin/**`, `actuator/**`). Note `Explainer` is pure Kotlin; `PipelineLatencySmokeTest.kt:20` only checks `.startsWith("Allowed:")`; `DhyanTileService` is not registered in `AndroidManifest.xml`. | Ask owner (Q1) for explicit approval to change those 5 literals in M1 while keeping `twin/` 100% pure Kotlin (`no android.*`, `no R`). | `check:twin` + `testDebugUnitTest` + M2 `rename-guard.mjs` |
| **F2** | `REPRODUCED` | `DigestPoster.kt:33` executes `if (nm.getNotificationChannel(ActuatorConfig.CHANNEL_DIGEST) != null) return` before creating `"Dhyan digest"` (`:36`) and `"Batched notifications held by Dhyan"` (`:39`). `DhyanForegroundService.kt:162-175` has no early return and always calls `createNotificationChannel`. | On Android upgrades over an existing install (Reno 14), Android preserves the old `"Dhyan digest"` channel name/description unless `createNotificationChannel()` is called again without the early return. In M1 (if Q1 approves updating `DigestPoster`), remove the `:33` early return so the channel label updates safely while retaining `CHANNEL_DIGEST` ID (`"dhyan_digest"`). | Unit test / manual owner upgrade smoke on Reno 14 |
| **F3** | `REPRODUCED` | `DhyanPetCanvas.kt:153` (`"Dhyan Spirit, ..."`), `DhyanPetOverlay.kt:325` (`"Restore Dhyan Spirit"`), `PetOverlayComposeTest.kt:44,199,202`, and `docs/DEMO_RUNBOOK.md:34,75` (`"Cloud Spirit"` / `"Dhyan Cloud Spirit"`) conflict with `docs/BRAND.md` (`Mascot: Pip (unchanged)`). Also `DhyanPetOverlay.kt:356-357` uses `"Boop Spirit"` / `"Minimize Spirit"`. | Ask owner (Q2) whether to standardize on `"Pip"` (`"Pip, ..."`, `"Restore Pip"`, updating `PetOverlayComposeTest.kt`) or `"Hunch Spirit"`. | `PetOverlayComposeTest` / M2 guard |
| **F4** | `REPRODUCED` | `git grep -n -i "onboarding\|about_..." -- phone/` -> exit code `1` (0 hits); `SettingsBottomSheet.kt` has no About card and `MainActivity.kt` / `DhyanMainScreen.kt` has no onboarding flow. Meanwhile `DhyanMainScreen.kt:63` currently displays `"A little space to focus"`. | Add an `About Hunch` card in `SettingsBottomSheet.kt` with name, descriptor, tagline, disclaimer, and codename note; confirm `DhyanMainScreen.kt:63` descriptor/first-run expectation (Q4). | M2 guard checks `strings.xml` / About UI strings for descriptor + disclaimer |
| **F5** | `REPRODUCED` | `vscode-ext/src/extension.ts:500,507` (`'Dhyan Diagnostics'`, `'=== Dhyan Extension Diagnostics ==='`) is asserted verbatim in `vscode-ext/test/integration/activation.test.ts:103`. | Ask owner (Q3) whether to update the Diagnostics channel/banner and `activation.test.ts:103` to `Hunch` in M1. | `npm --prefix vscode-ext run test:integration` |
| **F6** | `REPRODUCED` | `phone/app/src/main/res/xml/backup_rules.xml:5` and `data_extraction_rules.xml:6,11` exclude `dhyan_room.db`, whereas `DhyanDatabase.kt:207` opens `"dhyan.db"`. | **Keep untouched** (`STORED-DATA NAME`); reported for traceability — do not rename either string under R2. | `protected-identifiers.phone-ext.json` |
| **F7** | `REPRODUCED` | `vscode-ext/src/{extension,companionProvider,insightsPanel}.js` are imported by `tools/test/vscode-ext.test.mjs:4-6` AND `sensorLegacy.ts:3-5`, while `vscode-ext/tsconfig.json` has `strict: true` without `allowJs: true`. | Use `vscode-ext/src/brand.js` + `vscode-ext/src/brand.d.ts` in M1 so Node ESM tests, `tsc --noEmit`, and `esbuild` all share one source of truth cleanly. | `npm --prefix vscode-ext run lint`, `npm test` |

---

## 8. Claims Ledger (`G13`)

| Claim | Tag | Evidence (`path:line` / `command -> exit code, output`) |
|---|---|---|
| Base branch is `rename-hunch-phone` @ `54fe5b65deda7663f27ee9f0111029ee7ab8b5f8` with unmerged branch list confirmed | `[VERIFIED]` | `git status --short --branch; git log --oneline -5; git --no-pager branch --no-merged main` -> `exit code 0` (`ci/release-fix`, `codex/desktop-setup`, `rename-hunch-phone`, `s9-phone`); `git rev-parse HEAD` -> `exit code 0`, `54fe5b65deda7663f27ee9f0111029ee7ab8b5f8` |
| Variant B scope contains `1,908` `dhyan`/`ध्यान` hits with `0` unclassified | `[VERIFIED]` | `git grep -n -I -i "dhyan" -- phone/ vscode-ext/ browser-ext/ docs/DEMO_RUNBOOK.md docs/dhyan-pitch.md \| Measure-Object -Line` -> `exit code 0`, `GIT_GREP_LINES=1908`; Python classifier Counter over `m0_classified_hits.json` -> `exit code 0`, `TOTAL: 1908` |
| All protected Android, Protocol, VS Code, and Browser constants match extracted baseline | `[VERIFIED]` | Directly read `settings.gradle.kts:3`, `build.gradle.kts:7,10,58`, `AndroidManifest.xml:33-88`, `strings.xml:1-8`, `backup_rules.xml:1-6`, `data_extraction_rules.xml:1-13`, `DhyanDatabase.kt:207`, `SecretStore.kt:20-22`, `DeviceIdProvider.kt:11`, `PetDistractionController.kt:209,217`, `CryptoSession.kt:20,32`, `KeystoreKeyWrapper.kt:19`, `ActuatorConfig.kt:27-36`, `NotifCollector.kt:333`, `SettingsViewModel.kt:36,46`, `SyncCrypto.kt:64-278`, `vscode-ext/package.json:1-225`, `endpoint.ts:22,103`, `resume.ts:30`, `hash.ts:8`, `browser-ext/manifest.json:1-28`, `service-worker.js:7`; `Get-FileHash contracts\sync\test-vectors.json, phone\app\src\test\resources\test-vectors.json` -> both `00D06F19...` |
| Phone has no DataStore, FileProvider, URI scheme, or existing About/Onboarding screen | `[VERIFIED]` | `git grep -n "DataStore\|preferencesDataStore\|FileProvider\|android:scheme\|..."` -> `exit code 0` (0 phone matches); `git grep -n -i "onboarding\|about_..." -- phone/` -> `ONBOARDING_EXIT=1`; direct reads of `MainActivity.kt:1-53`, `DhyanMainScreen.kt:1-113`, `SettingsBottomSheet.kt:1-297` |
| `.dhyan-squircle-btn` in `companionProvider.js` is asserted by unit/integration tests | `[VERIFIED]` | `tools/test/vscode-ext.test.mjs:145` (`assert.ok(mockWebview.html.includes('dhyan-squircle-btn'))`) |
| Test/build suites pass after M0 | `[UNKNOWN]` | **NOT RUN in M0** (M0 is read-only discovery + inventory/baseline docs prior to owner approval; full suites run in M3) |
| Proposed M1 `Hunch` string wording for About/Settings and Pet accessibility labels | `[ASSUMED]` | Chose exact wording from `docs/BRAND.md` (`Hunch`, `your attention digital twin`, `Unrelated to other products named Hunch...`); changeable via Q1–Q5 answers before M1 |

---

## 9. Assumptions Table (`G4`)

| Item | Value / Proposal | Location | Reason | What Would Change It |
|---|---|---|---|---|
| Top-bar subtitle | `"your attention digital twin"` (or keeping `"A little space to focus"` alongside) | `DhyanMainScreen.kt:63` | `docs/BRAND.md` requires descriptor next to name on first run/About | Owner answer to Q4 |
| Mascot accessibility text | `"Pip, ${emotion}"` / `"Restore Pip"` | `DhyanPetCanvas.kt:153`, `DhyanPetOverlay.kt:325` | `docs/BRAND.md` specifies `Mascot: Pip (unchanged)` | Owner answer to Q2 |
| `dhyan-pitch.md` footnote | Single footnote at `:411`, remove top header line `:4` | `docs/dhyan-pitch.md:4,411` | Variant B: mark "Dhyan" origin only in a single "Codename" footnote | Owner preference |

---

## 10. Not Verified on Target

- Oppo Reno 14 APK upgrade install over existing data (`adb install -r`), launcher icon/label rendering, Notification/Accessibility service label refresh, `dhyan_digest` notification channel name/description update over existing install, Settings About card rendering, and persisted pairing/Room survival.
- Windows VS Code extension host activation, Command Palette display titles, sidebar Webview rendering, and Chrome/Edge MV3 Browser Extension unpacked load.
- Full test suites (scheduled in Milestone M3 after M1/M2 approval).

---

## 11. Questions for Owner (Approve Before M1)

1. **`twin/` & `actuator/` Ask-First Literals (`5` hits):** Approve updating these 5 user-visible literals in M1 while keeping `twin/` pure Kotlin (`no android.*`, `no R`) and removing the early return in `DigestPoster.kt:33` so existing Reno 14 installs get the updated channel label?
   - `phone/app/src/main/java/com/dhyan/app/twin/decision/Explainer.kt:10` -> `"Hunch is off."`
   - `phone/app/src/main/java/com/dhyan/app/actuator/DhyanTileService.kt:36` -> `getString(R.string.app_name)` (`"Hunch"`)
   - `phone/app/src/main/java/com/dhyan/app/actuator/digest/DigestBuilder.kt:40` -> `"Hunch digest"`
   - `phone/app/src/main/java/com/dhyan/app/actuator/digest/DigestPoster.kt:36,39` -> `@string/digest_channel_name` (`"Hunch digest"`) & `@string/digest_channel_desc` (`"Batched notifications held by Hunch"`)
2. **Mascot Naming (`"Pip"` vs `"Hunch Spirit"`):** `docs/BRAND.md` lists `Mascot | Pip (unchanged)`, while current UI/docs/tests say `"Dhyan Spirit"` (`DhyanPetCanvas.kt:153`, `DhyanPetOverlay.kt:325`, `PetOverlayComposeTest.kt:44,199,202`) and `"Dhyan Cloud Spirit"` (`docs/DEMO_RUNBOOK.md:34,75`). Should M1 rename those to `"Pip"` / `"Hunch's Pip"` (updating `PetOverlayComposeTest.kt`), or `"Hunch Spirit"`?
3. **VS Code Diagnostics Channel & Test (`extension.ts:500,507`, `activation.test.ts:103`):** Approve renaming `'Dhyan Diagnostics'` and `'=== Dhyan Extension Diagnostics ==='` to `'Hunch Diagnostics'` and `'=== Hunch Extension Diagnostics ==='`, and updating `activation.test.ts:103` accordingly?
4. **Phone About & First-Run Descriptor Placement (`DhyanMainScreen.kt`, `SettingsBottomSheet.kt`):** Confirm adding an **About Hunch** card inside `SettingsBottomSheet.kt` containing product name, descriptor (`your attention digital twin`), tagline, disclaimer (`Unrelated to other products named Hunch. A working name for a prototype.`), and codename note (`dhyan` kept internally). Also confirm whether `DhyanMainScreen.kt:63` (`"A little space to focus"`) should change to `"Your attention digital twin"` or keep `"A little space to focus"` in the top bar while showing the descriptor on a lightweight dismissible first-run banner + Settings About card.
5. **Internal QA Runbooks (`phone/MANUAL_CHECKS-*.md`, `PET_TESTING.md`, `README-sync.md` — `11` hits):** Leave those internal developer docs untouched as historical Stage 8/9 runbooks, or update UI menu step names (`"Dhyan"` -> `"Hunch"`) while preserving code/protocol references?

---

## 12. Files Created in M0 & Next Step

**Files Created (no existing tracked file modified):**
- `docs/prompts/HUNCH_RENAME_PACK.md`
- `docs/BRAND.md`
- `docs/rename/protected-identifiers.phone-ext.json`
- `NOTES_rename-hunch-phone.md` (this file)

**Next Step:** **STOP** here per M0 instructions until owner approves M0 classification and answers Questions 1–5, then proceed to M1 (`apply`), M2 (`tools/rename-guard.mjs` + `check:rename` fail-then-pass proof), and M3 (`verification`).
