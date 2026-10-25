import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export function checkNoPersonaImports(root = fileURLToPath(new URL('../', import.meta.url))) {
  let scanned = 0;
  // Inspect owned source/config/docs, not generated Gradle locks, dependencies,
  // binaries or build output. In particular, Windows can lock Gradle cache files.
  const generated = new Set(['node_modules', '.gradle', '.kotlin', '.cache', '.git', 'build', 'out', 'dist']);
  const visit = dir => {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory() && !generated.has(entry.name)) visit(p);
      else if (entry.isFile()) {
        scanned++;
        if (/tools[\\/]+personas\b/.test(fs.readFileSync(p, 'utf8'))) throw new Error('Production module references tools/personas');
      }
    }
  };
  visit(path.join(root, 'phone')); visit(path.join(root, 'laptop'));
  return `no-persona-imports: PASS (${scanned} production file(s); absent modules are not target verification)`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(checkNoPersonaImports(process.argv[2])); }
  catch (error) { console.error(`no-persona-imports: FAIL: ${error.message}`); process.exitCode = 1; }
}
