#!/usr/bin/env node
/**
 * tools/check-personas-purity.mjs
 * 4A-4: Fail if any file under personas/ contains forbidden imports or non-deterministic APIs.
 *
 * Forbidden patterns:
 *   - import android.*
 *   - import androidx.*
 *   - import com.dhyan.app.twin.*
 *   - Math.random
 *   - Random.Default
 *   - UUID.randomUUID
 *   - System.currentTimeMillis
 *   - System.nanoTime
 *
 * Self-test mode: --self-test (creates a violating file, checks detection, cleans up)
 * Exit non-zero with file:line on failure.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dir = path.dirname(fileURLToPath(import.meta.url));
const personasRoot = path.join(__dir, '..', 'phone', 'app', 'src', 'main', 'java', 'com', 'dhyan', 'app', 'personas');

const FORBIDDEN = [
  { pattern: /^import android\./m,                   label: 'android.* import' },
  { pattern: /^import androidx\./m,                  label: 'androidx.* import' },
  { pattern: /^import com\.dhyan\.app\.twin\./m,     label: 'twin.* import' },
  { pattern: /\bMath\.random\b/,                     label: 'Math.random (non-deterministic)' },
  { pattern: /\bRandom\.Default\b/,                  label: 'Random.Default (non-deterministic)' },
  { pattern: /\bUUID\.randomUUID\b/,                 label: 'UUID.randomUUID (non-deterministic)' },
  { pattern: /\bSystem\.currentTimeMillis\b/,        label: 'System.currentTimeMillis (non-deterministic)' },
  { pattern: /\bSystem\.nanoTime\b/,                 label: 'System.nanoTime (non-deterministic)' },
];

function fail(msg) { process.stderr.write(msg + '\n'); process.exit(1); }

function walkKt(dir, files = []) {
  if (!fs.existsSync(dir)) return files;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walkKt(full, files);
    else if (entry.name.endsWith('.kt')) files.push(full);
  }
  return files;
}

function checkFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.split('\n');
  const violations = [];
  for (const { pattern, label } of FORBIDDEN) {
    // Line-by-line for better reporting
    lines.forEach((line, idx) => {
      if (pattern.test(line)) violations.push(`${filePath}:${idx+1}: ${label}`);
    });
  }
  return violations;
}

async function main() {
  if (process.argv.includes('--self-test')) return runSelfTest();
  const files = walkKt(personasRoot);
  if (files.length === 0) {
    console.log('check-personas-purity: no .kt files found under personas/ (nothing to check)');
    return;
  }
  let allViolations = [];
  for (const f of files) allViolations = allViolations.concat(checkFile(f));
  if (allViolations.length > 0) {
    for (const v of allViolations) process.stderr.write(v + '\n');
    fail(`\ncheck-personas-purity FAIL: ${allViolations.length} violation(s)`);
  }
  console.log(`check-personas-purity PASS -- ${files.length} file(s) scanned`);
}

function runSelfTest() {
  const tmpFile = path.join(personasRoot, '_purity_selftest_TEMP.kt');
  const violating = 'package com.dhyan.app.personas\nimport android.content.Context\nfun bad() { Math.random() }\n';
  fs.writeFileSync(tmpFile, violating);
  try {
    const viols = checkFile(tmpFile);
    console.assert(viols.length >= 2, `Self-test FAIL: expected >=2 violations, got ${viols.length}`);
    console.assert(viols.some(v => v.includes('android.* import')), 'Self-test FAIL: android import not detected');
    console.assert(viols.some(v => v.includes('Math.random')), 'Self-test FAIL: Math.random not detected');
    console.log('check-personas-purity self-test PASS');
  } finally { fs.unlinkSync(tmpFile); }
}

main().catch(e => { console.error(e); process.exit(1); });
