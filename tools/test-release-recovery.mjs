import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
const read = p => readFileSync(new URL('../' + p, import.meta.url), 'utf8');
test('manual builds cannot publish and releases start as drafts', () => {
 const workflow = read('.github/workflows/release.yml');
 assert.match(workflow, /if: needs.meta.outputs.is_tag == 'true'/);
 assert.match(workflow, /draft: true/);
 assert.doesNotMatch(workflow, /keytool -genkeypair/);
});
test('extension CommonJS entry is explicitly identified', () => {
 const manifest = JSON.parse(read('vscode-ext/package.json'));
 assert.equal(manifest.main, './dist/extension.cjs');
 assert.match(read('vscode-ext/esbuild.js'), /outfile: 'dist\/extension.cjs'/);
});
