import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

export function checkTwinContract(root = fileURLToPath(new URL('../', import.meta.url))) {
  const contractDir = path.join(root, 'phone', 'app', 'build', 'contract');
  const snapshotsFile = path.join(contractDir, 'context-snapshots.json');
  const configFile = path.join(contractDir, 'twin-config.json');

  if (!fs.existsSync(snapshotsFile) || !fs.existsSync(configFile)) {
    throw new Error(
      `Contract files not found in ${contractDir}.\n` +
      `Run the Gradle unit tests first (e.g. .\\gradlew.bat :app:testDebugUnitTest from phone/).`
    );
  }

  // 1. Validate context snapshots against core-spec schema
  const schemaPath = path.join(root, 'core-spec', 'context-snapshot.schema.json');
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));

  const ajv = new Ajv2020({ allErrors: true, strict: true });
  addFormats(ajv);
  const validateSnapshot = ajv.compile(schema);

  const snapshots = JSON.parse(fs.readFileSync(snapshotsFile, 'utf8'));
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    throw new Error('context-snapshots.json must contain a non-empty array of snapshots');
  }

  for (let i = 0; i < snapshots.length; i++) {
    const s = snapshots[i];
    const valid = validateSnapshot(s);
    if (!valid) {
      const errMsgs = validateSnapshot.errors.map(e => `${e.instancePath || '/'} ${e.message}`).join('; ');
      throw new Error(`Snapshot index ${i} failed schema validation: ${errMsgs}`);
    }
  }

  // 2. Validate twin-config against core-spec default-params.json (Appendix A)
  const defaultParamsPath = path.join(root, 'core-spec', 'default-params.json');
  const defaultParams = JSON.parse(fs.readFileSync(defaultParamsPath, 'utf8'));
  const twinConfig = JSON.parse(fs.readFileSync(configFile, 'utf8'));

  const expectedValues = {
    monteCarloN: 500,
    windowMin: 30.0,
    kappa: 5.0,
    gamma: 0.995,
    lognormPriorN: 3.0,
    sigmaMin: 0.25,
    cGlanceMin: 0.4
  };

  for (const [key, expected] of Object.entries(expectedValues)) {
    const actual = twinConfig[key];
    if (actual === undefined) {
      throw new Error(`Missing key '${key}' in exported twin-config.json`);
    }
    if (Math.abs(actual - expected) > 1e-6) {
      throw new Error(`TwinConfig mismatch for '${key}': expected ${expected}, got ${actual}`);
    }
  }

  return `twin-contract: PASS (${snapshots.length} snapshot(s) validated against schema; TwinConfig defaults match Appendix A)`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    console.log(checkTwinContract(process.argv[2]));
  } catch (error) {
    console.error(`twin-contract: FAIL: ${error.message}`);
    process.exitCode = 1;
  }
}
