import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const p = path.join(dir, entry.name);
    return entry.isDirectory() ? (['node_modules', '.git'].includes(entry.name) ? [] : walk(p)) : entry.isFile() ? [p] : [];
  });
}

export function checkSchemaSync(root = fileURLToPath(new URL('../', import.meta.url))) {
  const version = fs.readFileSync(path.join(root, 'core-spec/SCHEMA_VERSION'), 'utf8').trim();
  const event = JSON.parse(fs.readFileSync(path.join(root, 'core-spec/event.schema.json'), 'utf8'));
  if (event.properties.schemaVersion.const !== version) throw new Error('SCHEMA_VERSION differs from event schema');
  const kotlinFiles = walk(root).filter(p => path.basename(p) === 'SchemaVersion.kt');
  for (const p of kotlinFiles) {
    const text = fs.readFileSync(p, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/g, '');
    const versions = [...text.matchAll(/\b(?:const\s+)?val\s+\w+\s*(?::\s*String\s*)?=\s*"(\d+\.\d+\.\d+)"/g)].map(m => m[1]);
    if (!versions.length || versions.some(v => v !== version)) throw new Error('SchemaVersion.kt is missing a matching version or differs');
  }
  return `schema-sync: PASS (${version}; ${kotlinFiles.length} Kotlin version file(s))`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(checkSchemaSync(process.argv[2])); }
  catch (error) { console.error(`schema-sync: FAIL: ${error.message}`); process.exitCode = 1; }
}
