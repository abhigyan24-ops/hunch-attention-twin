#!/usr/bin/env node
/**
 * tools/rename-guard.mjs — Hunch rename safety guard
 * Verifies protected identifiers are unchanged and user-visible strings say Hunch.
 * Run: node tools/rename-guard.mjs
 */
import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
let failed = false;

function fail(msg) { console.error('FAIL:', msg); failed = true; }
function pass(msg) { console.log('PASS:', msg); }
function warn(msg) { console.warn('WARN:', msg); }

function readFile(relPath) {
  const p = join(ROOT, relPath);
  if (!existsSync(p)) { warn(`File not found (skipping): ${relPath}`); return null; }
  return readFileSync(p, 'utf8');
}

// ── 1. Android applicationId ──────────────────────────────────────────────────
const buildGradle = readFile('phone/app/build.gradle.kts');
if (buildGradle) {
  if (buildGradle.includes('applicationId = "com.dhyan.app"')) pass('Android applicationId = com.dhyan.app');
  else fail('Android applicationId was changed! Must be com.dhyan.app');
}

// ── 2. rootProject.name ───────────────────────────────────────────────────────
const settingsGradle = readFile('phone/settings.gradle.kts');
if (settingsGradle) {
  if (settingsGradle.includes('rootProject.name = "Dhyan"')) pass('rootProject.name = "Dhyan" (Gradle internal, unchanged)');
  else fail('rootProject.name changed! Must remain "Dhyan"');
}

// ── 3. Android notification channel IDs ───────────────────────────────────────
const actuatorConfig = readFile('phone/app/src/main/java/com/dhyan/app/actuator/ActuatorConfig.kt');
if (actuatorConfig) {
  for (const id of ['dhyan_digest', 'dhyan_service']) {
    if (actuatorConfig.includes(`"${id}"`)) pass(`Channel ID "${id}" unchanged`);
    else fail(`Channel ID "${id}" was changed!`);
  }
}

// ── 4. Protocol constants in contracts ────────────────────────────────────────
// test-vectors.json contains the AAD string dhyan|1| but not full HKDF info strings.
// HKDF strings are in the main codebase; we verify the AAD prefix only here.
const testVectors = readFile('contracts/sync/test-vectors.json');
if (testVectors) {
  if (testVectors.includes('dhyan|1|')) pass('Protocol AAD prefix "dhyan|1|" unchanged in test-vectors.json');
  else fail('Protocol AAD prefix "dhyan|1|" MISSING from test-vectors.json!');
  // Description field should still reference the protocol (we do NOT change it — it's a contract doc)
  if (testVectors.includes('Dhyan Sync Protocol')) pass('test-vectors.json description unchanged (Dhyan Sync Protocol — do not change)');
  else warn('test-vectors.json description changed — check manually');
} else {
  warn('contracts/sync/test-vectors.json not found');
}

// ── 5. VS Code identifiers ────────────────────────────────────────────────────
const vscodePkgRaw = readFile('vscode-ext/package.json');
if (vscodePkgRaw) {
  const pkg = JSON.parse(vscodePkgRaw);
  if (pkg.name === 'dhyan-vscode') pass('VS Code name=dhyan-vscode unchanged');
  else fail(`VS Code package name changed to "${pkg.name}"!`);
  if (pkg.publisher === 'dhyan') pass('VS Code publisher=dhyan unchanged');
  else fail(`VS Code publisher changed to "${pkg.publisher}"!`);
  // Command IDs
  const badIds = (pkg.contributes?.commands || []).map(c => c.command).filter(id => !id.startsWith('dhyan.'));
  if (badIds.length === 0) pass('All VS Code command IDs start with dhyan.');
  else fail(`VS Code command IDs changed: ${badIds.join(', ')}`);
  // Config keys
  const badKeys = Object.keys(pkg.contributes?.configuration?.properties || {}).filter(k => !k.startsWith('dhyan.'));
  if (badKeys.length === 0) pass('All VS Code config keys start with dhyan.');
  else fail(`VS Code config keys changed: ${badKeys.join(', ')}`);
  // Display name should be Hunch
  if (pkg.displayName && pkg.displayName.startsWith('Hunch')) pass('VS Code displayName starts with Hunch');
  else fail(`VS Code displayName should start with Hunch, got: ${pkg.displayName}`);
}

// ── 6. Browser extension protected ID ─────────────────────────────────────────
const swJs = readFile('browser-ext/service-worker.js');
if (swJs) {
  if (swJs.includes("'dhyan-mock-extension-id'") || swJs.includes('"dhyan-mock-extension-id"'))
    pass('Browser extension fallback ID dhyan-mock-extension-id unchanged');
  else warn('dhyan-mock-extension-id not found in service-worker.js - check manually');
}

// ── 7. Scan user-visible resource files for "Dhyan" ───────────────────────────
// Allowlist: lines that intentionally still say "dhyan" (codename notes)
const ALLOWLIST_PATTERNS = [
  /com\.dhyan/,              // package identifier
  /dhyan[._\-|]/,            // identifier prefix (dhyan. dhyan_ dhyan- dhyan|)
  /\.DhyanApplication/,      // Android application class name (identifier)
  /\.DhyanForegroundService/, // Android service class name (identifier)
  /DhyanService/,            // any Dhyan* class name used as identifier
  /DhyanVsCode/,             // VS Code sensor class name
  /DhyanStatusBar/,          // VS Code status bar class name
  /DhyanCompanion/,          // VS Code companion class name
  /DhyanInsights/,           // VS Code insights panel class name
  /DhyanTheme/,              // Android theme class name
  /DhyanDatabase/,           // Android database class name
  /DhyanMainScreen/,         // Android screen class name
  /DhyanPet/,                // Android pet class name
  /DhyanTile/,               // Android tile class name
  /com\.dhyan\.app/,         // package name
  /dhyan-vscode/,            // VS Code extension name
  /dhyan-session/,           // protocol constant
  /dhyan-pair/,              // protocol constant
  /dhyan-room/,              // protocol constant
  /dhyan-hello/,             // protocol constant
  /dhyan-rendezvous/,        // protocol constant
  /dhyan\|1\|/,              // protocol AAD
  /rootProject\.name/,       // Gradle internal
  /dhyan_digest/,            // channel id
  /dhyan_service/,           // channel id
  /dhyan_interception/,      // channel id
  /brand_codename_note/,     // the codename explanation string key
  /say \\'dhyan\\'/,         // the codename note value itself (Android XML escape)
  /still say.*dhyan/i,       // codename note prose
  /codename.*dhyan/i,        // codename explanation
  /dhyan.*for compatibility/i, // compat note
  /internal.*dhyan/i,        // internal ident note
  /retain.*dhyan/i,          // compat note
  /dhyan\.\*.*setting/i,     // VS Code setting key explanation in README
  /`dhyan\.\*/,              // backtick-quoted dhyan.* keys
  /"publisher".*dhyan/,      // VS Code publisher identifier
  /publisher.*dhyan/,        // publisher field
  /~\/\.dhyan\//,            // ~/.dhyan/ path (endpoint file identifier)
  /endpoint\.json/,          // endpoint file name
  /dhyan-mock/,              // browser ext mock id
  /DhyanApplication/,        // Android Application subclass
];

function scanForDhyan(relPath, label) {
  const content = readFile(relPath);
  if (!content) return;
  const lines = content.split('\n');
  let hitCount = 0;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!/dhyan/i.test(line)) continue;
    if (!ALLOWLIST_PATTERNS.some(pat => pat.test(line))) {
      fail(`${label}:${i+1} still contains "Dhyan": ${line.trim().slice(0, 120)}`);
      hitCount++;
    }
  }
  if (hitCount === 0) pass(`No unexpected "Dhyan" in ${label}`);
}

scanForDhyan('phone/app/src/main/res/values/strings.xml', 'strings.xml');
scanForDhyan('phone/app/src/main/AndroidManifest.xml', 'AndroidManifest.xml');
scanForDhyan('vscode-ext/package.json', 'vscode-ext/package.json');
scanForDhyan('browser-ext/manifest.json', 'browser-ext/manifest.json');

// ── 8. Brand assertions in strings.xml ────────────────────────────────────────
const stringsXml = readFile('phone/app/src/main/res/values/strings.xml');
if (stringsXml) {
  if (stringsXml.includes('>Hunch<')) pass('strings.xml app_name=Hunch');
  else fail('strings.xml app_name is not Hunch!');
  if (stringsXml.includes('your attention digital twin')) pass('strings.xml brand_descriptor present');
  else fail('strings.xml brand_descriptor missing!');
  if (stringsXml.includes('Unrelated to other products named Hunch')) pass('strings.xml brand_disclaimer present');
  else fail('strings.xml brand_disclaimer missing!');
}

// ── 9. HunchBrand.kt exists ───────────────────────────────────────────────────
const hunchBrand = readFile('phone/app/src/main/java/com/dhyan/app/ui/brand/HunchBrand.kt');
if (hunchBrand) {
  if (hunchBrand.includes('object HunchBrand')) pass('HunchBrand.kt exists with object HunchBrand');
  else fail('HunchBrand.kt missing object declaration!');
}

// ── Summary ────────────────────────────────────────────────────────────────────
if (failed) {
  console.error('\nrename-guard: FAILED ❌');
  process.exit(1);
} else {
  console.log('\nrename-guard: ALL CHECKS PASSED ✅');
}
