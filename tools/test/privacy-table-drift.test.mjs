import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

describe('Privacy Table Drift Verification', () => {
  const root = path.resolve(import.meta.dirname, '../..');
  const privacyDocPath = path.join(root, 'docs', 'PRIVACY.md');
  const vscodeSchemaPath = path.join(root, 'vscode-ext', 'src', 'privacy', 'schema.ts');

  test('docs/PRIVACY.md exists and contains valid JSON privacy manifest', () => {
    assert.equal(fs.existsSync(privacyDocPath), true);
    const content = fs.readFileSync(privacyDocPath, 'utf8');
    const match = content.match(/```json:privacy-manifest\s*([\s\S]*?)\s*```/);
    assert.ok(match, 'Missing ```json:privacy-manifest code fence in docs/PRIVACY.md');
    const manifest = JSON.parse(match[1]);
    assert.equal(manifest.contractVersion, '1.0.0');
    assert.ok(Array.isArray(manifest.vscode.records));
    assert.ok(Array.isArray(manifest.vscode.forbidden));
  });

  test('Documented forbidden fields are not permitted in VS Code event schemas', () => {
    const content = fs.readFileSync(privacyDocPath, 'utf8');
    const match = content.match(/```json:privacy-manifest\s*([\s\S]*?)\s*```/);
    const manifest = JSON.parse(match[1]);
    const schemaSrc = fs.readFileSync(vscodeSchemaPath, 'utf8');

    // Assert forbidden items do not exist as allowed schema properties
    for (const forbidden of manifest.vscode.forbidden) {
      assert.equal(schemaSrc.includes(`${forbidden}:`), false, `Forbidden field '${forbidden}' found in schema.ts!`);
    }
  });

  test('README privacy table matches documented boundaries', () => {
    const readme = fs.readFileSync(path.join(root, 'README.md'), 'utf8');
    assert.ok(readme.includes('Privacy at a Glance'), 'README missing Privacy table');
    assert.ok(readme.includes('Zero Telemetry') || readme.includes('No accounts, no telemetry'), 'README missing zero-telemetry guarantee');
  });
});
