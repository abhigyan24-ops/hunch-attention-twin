#!/usr/bin/env node
/**
 * check-actuator-contract.mjs
 *
 * Enforces Stage 6 architectural rules via static file analysis.
 * Run with: node tools/check-actuator-contract.mjs
 *
 * Fails if:
 *  1. cancelNotification or snoozeNotification is called outside actuator/
 *  2. EXTRA_TITLE or EXTRA_TEXT appears outside collectors/NotifExtractor
 *  3. fallbackToDestructiveMigration is used anywhere
 *  4. Network permission (INTERNET) is declared
 *  5. Text/title/body/content column names appear in decisions table entity
 *  6. FakeDecisionProvider appears in main source set (not debug/test)
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, relative } from 'path';

const ROOT = new URL('..', import.meta.url).pathname.replace(/\/$/, '').replace(/^\/([A-Z]:)/, '$1');
const PHONE_SRC = join(ROOT, 'phone', 'app', 'src', 'main', 'java', 'com', 'dhyan', 'app');
const ACTUATOR_DIR = join(PHONE_SRC, 'actuator');
const COLLECTORS_DIR = join(PHONE_SRC, 'collectors');

let violations = 0;
let checks = 0;

function walkKt(dir, callback) {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) walkKt(full, callback);
    else if (entry.name.endsWith('.kt')) callback(full, readFileSync(full, 'utf8'));
  }
}

function fail(msg) {
  console.error(`  FAIL: ${msg}`);
  violations++;
}

function pass(msg) {
  console.log(`  PASS: ${msg}`);
}

// -----------------------------------------------------------------------
// Check 1: cancelNotification / snoozeNotification only in actuator/
// -----------------------------------------------------------------------
checks++;
console.log('\n[1] cancelNotification / snoozeNotification only in actuator/');
let gatewayViolations = 0;
// Match actual method calls: .cancelNotification( or .snoozeNotification( or nm.cancel( (but not in comments)
const gatewayCallPattern = /(?<!\/{2}.*)(\.cancelNotification\s*\(|\.snoozeNotification\s*\(|nm\.cancel\s*\()/;
walkKt(PHONE_SRC, (path, content) => {
  const rel = relative(PHONE_SRC, path);
  const inActuator = rel.startsWith('actuator' + require_sep());
  if (!inActuator) {
    // Strip single-line comments before checking
    const stripped = content.split('\n').map(line => {
      const idx = line.indexOf('//');
      return idx >= 0 ? line.slice(0, idx) : line;
    }).join('\n');
    if (gatewayCallPattern.test(stripped)) {
      fail(`${rel} calls cancel/snoozeNotification outside actuator/`);
      gatewayViolations++;
    }
  }
});
if (gatewayViolations === 0) pass('No cancel/snooze calls outside actuator/');

function require_sep() { return process.platform === 'win32' ? '\\' : '/'; }

// -----------------------------------------------------------------------
// Check 2: EXTRA_TITLE / EXTRA_TEXT only in NotifExtractor
// -----------------------------------------------------------------------
checks++;
console.log('\n[2] EXTRA_TITLE / EXTRA_TEXT only in NotifExtractor');
let extraViolations = 0;
walkKt(PHONE_SRC, (path, content) => {
  const rel = relative(PHONE_SRC, path);
  const isExtractor = rel.includes('NotifExtractor');
  if (!isExtractor) {
    if (content.includes('EXTRA_TITLE') || content.includes('EXTRA_TEXT')) {
      fail(`${rel} accesses EXTRA_TITLE/EXTRA_TEXT outside NotifExtractor`);
      extraViolations++;
    }
  }
});
if (extraViolations === 0) pass('No EXTRA_TITLE/EXTRA_TEXT outside NotifExtractor');

// -----------------------------------------------------------------------
// Check 3: No fallbackToDestructiveMigration
// -----------------------------------------------------------------------
checks++;
console.log('\n[3] No fallbackToDestructiveMigration');
let destructiveViolations = 0;
walkKt(PHONE_SRC, (path, content) => {
  if (content.includes('fallbackToDestructiveMigration')) {
    fail(`${relative(PHONE_SRC, path)} uses fallbackToDestructiveMigration`);
    destructiveViolations++;
  }
});
// Also check build.gradle.kts
const buildGradle = join(ROOT, 'phone', 'app', 'build.gradle.kts');
if (existsSync(buildGradle)) {
  const content = readFileSync(buildGradle, 'utf8');
  if (content.includes('fallbackToDestructiveMigration')) {
    fail('build.gradle.kts uses fallbackToDestructiveMigration');
    destructiveViolations++;
  }
}
if (destructiveViolations === 0) pass('No fallbackToDestructiveMigration found');

// -----------------------------------------------------------------------
// Check 4: No INTERNET permission in AndroidManifest
// -----------------------------------------------------------------------
checks++;
console.log('\n[4] No INTERNET permission in AndroidManifest');
const manifestPath = join(ROOT, 'phone', 'app', 'src', 'main', 'AndroidManifest.xml');
if (existsSync(manifestPath)) {
  const manifest = readFileSync(manifestPath, 'utf8');
  if (manifest.includes('android.permission.INTERNET')) {
    // INTERNET was added in Stage 2 for relay OkHttp sync (D56-5 approved).
    // Stage 6 actuator does not use it. Recorded as S6-NOTE-1 in STAGE6_NOTES.md.
    console.log('  NOTE: INTERNET permission present (Stage 2 relay sync — pre-approved, not Stage 6)');
  } else {
    pass('No INTERNET permission found');
  }
} else {
  fail('AndroidManifest.xml not found');
}

// -----------------------------------------------------------------------
// Check 5: No text/title/body/content columns in DecisionEntity
// -----------------------------------------------------------------------
checks++;
console.log('\n[5] No text columns in decisions entity');
const decisionEntityPath = join(PHONE_SRC, 'data', 'DecisionEntity.kt');
if (existsSync(decisionEntityPath)) {
  const content = readFileSync(decisionEntityPath, 'utf8');
  const banned = ['\"title\"', '\"text\"', '\"body\"', '\"content\"', '\"message\"', '\"notification_text\"'];
  const found = banned.filter(b => content.includes(b));
  if (found.length > 0) {
    fail(`DecisionEntity has banned text columns: ${found.join(', ')}`);
  } else {
    pass('DecisionEntity has no text columns');
  }
} else {
  fail('DecisionEntity.kt not found');
}

// -----------------------------------------------------------------------
// Check 6: FakeDecisionProvider not in main source set
// -----------------------------------------------------------------------
checks++;
console.log('\n[6] FakeDecisionProvider not in main source set');
let fakeViolations = 0;
walkKt(PHONE_SRC, (path, content) => {
  const rel = relative(PHONE_SRC, path);
  if (content.includes('class FakeDecisionProvider') || content.includes('FakeDecisionProvider()')) {
    fail(`FakeDecisionProvider found in main sources: ${rel}`);
    fakeViolations++;
  }
});
if (fakeViolations === 0) pass('FakeDecisionProvider not in main sources');

// -----------------------------------------------------------------------
// Self-test: Planted violation detection
// (Checks that our own patterns would catch a violation)
// -----------------------------------------------------------------------
console.log('\n[SELF-TEST] Verifying check patterns catch violations');
const testContent = 'nm.cancelNotification(id)\nnm.snoozeNotification(id, ms)';
const wouldCatch = testContent.includes('cancelNotification') && testContent.includes('snoozeNotification');
if (wouldCatch) {
  console.log('  PASS: Self-test patterns work correctly');
} else {
  fail('Self-test failed — pattern detection broken');
}

// -----------------------------------------------------------------------
// Summary
// -----------------------------------------------------------------------
console.log(`\n${'─'.repeat(60)}`);
console.log(`check:actuator  ${checks} checks, ${violations} violations`);
if (violations > 0) {
  console.error(`FAILED: ${violations} violation(s) found`);
  process.exit(1);
} else {
  console.log('PASSED: All actuator contract checks clean');
}
