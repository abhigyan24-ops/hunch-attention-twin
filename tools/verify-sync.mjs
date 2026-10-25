import fs from 'fs';
import readline from 'readline';

export async function verifySyncFiles(laptopFilePath, phoneFilePath) {
  const violations = [];

  async function loadEvents(filePath, deviceName) {
    const events = [];
    const idSet = new Set();
    let lastSeq = -1;

    if (!fs.existsSync(filePath)) {
      violations.push(`File does not exist: ${filePath} (${deviceName})`);
      return { events, idSet };
    }

    const fileStream = fs.createReadStream(filePath);
    const rl = readline.createInterface({ input: fileStream, crlfDelay: Infinity });

    let lineNum = 0;
    for await (const line of rl) {
      lineNum++;
      if (!line.trim()) continue;

      let ev;
      try {
        ev = JSON.parse(line);
      } catch (err) {
        violations.push(`${deviceName} line ${lineNum}: Invalid JSON: ${err.message}`);
        continue;
      }

      // Check ID
      if (!ev.id) {
        violations.push(`${deviceName} line ${lineNum}: Missing event id`);
      } else if (idSet.has(ev.id)) {
        violations.push(`${deviceName} line ${lineNum}: Duplicate event id ${ev.id}`);
      } else {
        idSet.add(ev.id);
      }

      // Check seq monotonic per device
      if (typeof ev.seq === 'number') {
        if (ev.device === deviceName) {
          if (ev.seq <= lastSeq) {
            violations.push(`${deviceName} line ${lineNum}: Non-monotonic seq ${ev.seq} (previous was ${lastSeq})`);
          }
          lastSeq = ev.seq;
        }
      }

      events.push(ev);
    }

    return { events, idSet };
  }

  const laptopData = await loadEvents(laptopFilePath, 'laptop');
  const phoneData = await loadEvents(phoneFilePath, 'phone');

  // Verify that all laptop events generated on laptop and synced to phone match
  const laptopEventMap = new Map(laptopData.events.map(e => [e.id, e]));
  const phoneEventMap = new Map(phoneData.events.map(e => [e.id, e]));

  // Compare events that exist in both
  for (const [id, lEvent] of laptopEventMap.entries()) {
    if (phoneEventMap.has(id)) {
      const pEvent = phoneEventMap.get(id);
      if (lEvent.type !== pEvent.type) {
        violations.push(`Event ${id}: type mismatch (${lEvent.type} vs ${pEvent.type})`);
      }
      if (lEvent.ts !== pEvent.ts) {
        violations.push(`Event ${id}: timestamp mismatch (${lEvent.ts} vs ${pEvent.ts})`);
      }
      if (lEvent.device !== pEvent.device) {
        violations.push(`Event ${id}: device mismatch (${lEvent.device} vs ${pEvent.device})`);
      }
    }
  }

  return {
    valid: violations.length === 0,
    laptopCount: laptopData.events.length,
    phoneCount: phoneData.events.length,
    violations
  };
}

if (process.argv[1] && process.argv[1].endsWith('tools/verify-sync.mjs')) {
  const [laptopPath, phonePath] = process.argv.slice(2);
  if (!laptopPath || !phonePath) {
    console.error('Usage: node tools/verify-sync.mjs <laptop.jsonl> <phone.jsonl>');
    process.exit(2);
  }

  const res = await verifySyncFiles(laptopPath, phonePath);
  if (res.valid) {
    console.log(`Sync Verified: ${res.laptopCount} laptop events, ${res.phoneCount} phone events, 0 violations.`);
    process.exit(0);
  } else {
    console.error(`Sync Verification Failed (${res.violations.length} violations):`);
    for (const v of res.violations) {
      console.error(`  - ${v}`);
    }
    process.exit(1);
  }
}
