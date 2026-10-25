import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export function checkTwinPurity(root = fileURLToPath(new URL('../', import.meta.url))) {
  let scanned = 0;
  const visit = (dir, inTwin = false) => {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory()) visit(p, inTwin || entry.name === 'twin');
      else if (entry.isFile() && inTwin) {
        scanned++;
        if (/\bimport\s+(?:android|androidx)\./.test(fs.readFileSync(p, 'utf8'))) throw new Error('Android import found under twin');
      }
    }
  };
  visit(path.join(root, 'phone'));
  return `twin-purity: PASS (${scanned} twin file(s); absent modules are not target verification)`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(checkTwinPurity(process.argv[2])); }
  catch (error) { console.error(`twin-purity: FAIL: ${error.message}`); process.exitCode = 1; }
}
