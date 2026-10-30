import fs from 'node:fs';
import { validateEvent } from './validate-events.mjs';

const filePath = process.argv[2] || 'phone/app/build/reports/canonical-phone-events.jsonl';
if (!fs.existsSync(filePath)) {
  console.error(`File not found: ${filePath}`);
  process.exit(1);
}

const content = fs.readFileSync(filePath, 'utf8');
const lines = content.split('\n').map(l => l.trim()).filter(Boolean);

console.log(`Validating ${lines.length} canonical phone events from ${filePath}...`);

let passed = 0;
let failed = 0;

for (let i = 0; i < lines.length; i++) {
  let event;
  try {
    event = JSON.parse(lines[i]);
  } catch (err) {
    console.error(`Line ${i + 1}: Invalid JSON`);
    failed++;
    continue;
  }

  const result = validateEvent(event);
  if (!result.valid) {
    console.error(`Line ${i + 1} (${event.type}): REJECTED with errors: ${result.errors.join(', ')}`);
    failed++;
  } else {
    console.log(`Line ${i + 1} (${event.type}): VALID`);
    passed++;
  }
}

console.log(`Validation summary: ${passed} passed, ${failed} failed.`);
if (failed > 0) {
  process.exit(1);
}
