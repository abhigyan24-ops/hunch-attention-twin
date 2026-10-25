import fs from 'fs';
import path from 'path';
import readline from 'readline';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

const FORBIDDEN_FIELDS = JSON.parse(fs.readFileSync(path.join(ROOT, 'core-spec/forbidden-fields.json'), 'utf8'));
const FORBIDDEN_SET = new Set(FORBIDDEN_FIELDS);

const CANARY_PATTERNS = [
  /https?:\/\/[^\s"]+/i,
  /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/,
  /CANARY_[A-Z0-9_]+/i
];

export function scanObjectForPrivacyViolations(obj, currentPath = '') {
  const violations = [];
  if (obj === null || typeof obj !== 'object') return violations;

  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      violations.push(...scanObjectForPrivacyViolations(obj[i], `${currentPath}[${i}]`));
    }
    return violations;
  }

  for (const [key, val] of Object.entries(obj)) {
    const fullKey = currentPath ? `${currentPath}.${key}` : key;

    // Check forbidden key
    if (FORBIDDEN_SET.has(key)) {
      violations.push(`Forbidden field name detected: "${fullKey}"`);
    }

    // Check canary strings in string values
    if (typeof val === 'string') {
      for (const pattern of CANARY_PATTERNS) {
        if (pattern.test(val)) {
          violations.push(`Canary pattern match in "${fullKey}": "${val.slice(0, 32)}..."`);
        }
      }
    } else if (typeof val === 'object' && val !== null) {
      violations.push(...scanObjectForPrivacyViolations(val, fullKey));
    }
  }

  return violations;
}

export async function scanFileForPrivacy(filePath) {
  const violations = [];
  if (!fs.existsSync(filePath)) return [`File not found: ${filePath}`];

  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.split(/\r?\n/);

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    try {
      const parsed = JSON.parse(line);
      const v = scanObjectForPrivacyViolations(parsed, `line:${i + 1}`);
      violations.push(...v);
    } catch (_) {
      // Not JSON line: check regex on raw line
      for (const pattern of CANARY_PATTERNS) {
        if (pattern.test(line)) {
          violations.push(`Line ${i + 1}: Canary pattern match in raw text: "${line.slice(0, 32)}..."`);
        }
      }
    }
  }

  return violations;
}

if (process.argv[1] && process.argv[1].endsWith('tools/privacy-scan.mjs')) {
  const targetPaths = process.argv.slice(2);
  if (targetPaths.length === 0) {
    console.error('Usage: node tools/privacy-scan.mjs <file-or-dir> [...]');
    process.exit(2);
  }

  let totalViolations = 0;
  for (const target of targetPaths) {
    const stat = fs.statSync(target);
    const files = stat.isDirectory()
      ? fs.readdirSync(target).filter(f => !f.startsWith('.')).map(f => path.join(target, f))
      : [target];

    for (const f of files) {
      const violations = await scanFileForPrivacy(f);
      if (violations.length > 0) {
        console.error(`Privacy violations in ${f}:`);
        for (const v of violations) {
          console.error(`  - ${v}`);
        }
        totalViolations += violations.length;
      }
    }
  }

  if (totalViolations === 0) {
    console.log('Privacy scan clean. Zero violations detected.');
    process.exit(0);
  } else {
    console.error(`Privacy scan failed with ${totalViolations} violations.`);
    process.exit(1);
  }
}
