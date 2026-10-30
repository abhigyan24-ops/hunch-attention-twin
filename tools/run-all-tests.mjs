import { execSync } from 'node:child_process';
import path from 'node:path';

function run(label, cmd) {
  console.log(`\n================================================================`);
  console.log(`  [TEST SUITE] ${label}`);
  console.log(`  Command: ${cmd}`);
  console.log(`================================================================`);
  try {
    execSync(cmd, { stdio: 'inherit', encoding: 'utf8' });
    console.log(`--> [PASS] ${label}`);
    return true;
  } catch (err) {
    console.error(`--> [FAIL] ${label}`);
    return false;
  }
}

async function main() {
  console.log('################################################################');
  console.log('       DHYAN FULL-STACK COMPREHENSIVE VERIFICATION SUITE       ');
  console.log('  Testing: Laptop App & Pet, VS Code Extension, Relay, & Hardware');
  console.log('################################################################');

  const results = [];

  // 1. Laptop Application & Frosted Pet Tests
  results.push({
    name: 'Laptop App & Pet State Machine Tests (86 Tests)',
    passed: run('Laptop App & Pet State Machine', 'npm --prefix laptop test')
  });

  // 2. Laptop TypeScript Purity Check
  results.push({
    name: 'Laptop App TypeScript Typecheck',
    passed: run('Laptop App Typecheck', 'npm --prefix laptop run typecheck')
  });

  // 3. VS Code Extension & Companion Webview Tests
  results.push({
    name: 'VS Code Extension Sensor & Frosted Pet Webview',
    passed: run('VS Code Extension & Companion Pet', 'node --test tools/test/vscode-ext.test.mjs')
  });

  // 4. Shared Contract, Settlement & Purity Checks
  results.push({
    name: 'Core Spec Purity & Schema Synchronization',
    passed: run('Purity & Governance Checks', 'npm run checks')
  });

  results.push({
    name: 'Sync Cryptography & Settlement Contract Tests',
    passed: run('Sync Cryptography & Settlement', 'node --test tools/test/contract-*.test.mjs')
  });

  // 5. Target Hardware Live Check (Oppo Reno 14)
  console.log(`\n================================================================`);
  console.log(`  [HARDWARE CHECK] Checking Target Device (Oppo Reno 14)...`);
  console.log(`================================================================`);

  const adbPath = `${process.env.LOCALAPPDATA}\\Android\\Sdk\\platform-tools\\adb.exe`;
  let devicesOutput = '';
  try {
    devicesOutput = execSync(`"${adbPath}" devices`, { encoding: 'utf8' });
  } catch (_) {
    devicesOutput = '';
  }

  const phoneConnected = devicesOutput.includes('GAZ9KV9DLN6DIZGE') || (devicesOutput.match(/\n[^\s]+\s+device\b/) !== null);

  if (phoneConnected) {
    console.log('  -> Physical Android Device Detected! Running live Gate 3 test suite...');
    results.push({
      name: 'Target Hardware (Oppo Reno 14) Live Gate 3 Pairing & Sync',
      passed: run('Target Hardware Gate 3 Live Verification', 'node tools/gate3-live-test.mjs')
    });
  } else {
    console.log('  -> Note: Physical phone (GAZ9KV9DLN6DIZGE) is not currently detected via ADB.');
    console.log('     Please connect the phone via USB with USB Debugging enabled to run live hardware tests.');
    results.push({
      name: 'Target Hardware (Oppo Reno 14)',
      passed: null,
      note: 'Skipped - device not connected'
    });
  }

  // Summary Report
  console.log('\n\n================================================================');
  console.log('                      VERIFICATION SUMMARY                      ');
  console.log('================================================================');

  let allPassed = true;
  for (const r of results) {
    const status = r.passed === true ? '[PASS]' : (r.passed === false ? '[FAIL]' : '[SKIP]');
    console.log(`  ${status.padEnd(8)} ${r.name}${r.note ? ` (${r.note})` : ''}`);
    if (r.passed === false) allPassed = false;
  }
  console.log('================================================================');

  if (!allPassed) {
    console.error('\nOne or more test suites failed.');
    process.exit(1);
  } else {
    console.log('\nAll executed test suites PASSED cleanly!');
  }
}

main();
