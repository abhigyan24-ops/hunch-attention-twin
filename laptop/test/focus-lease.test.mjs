import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import { build } from 'esbuild';
import { fileURLToPath, pathToFileURL } from 'node:url';

const tempOutFile = fileURLToPath(new URL('../node_modules/.sidecar-test-bundle.mjs', import.meta.url));
await build({
  entryPoints: [fileURLToPath(new URL('../src/main/sidecar.ts', import.meta.url))],
  bundle: true,
  format: 'esm',
  platform: 'node',
  external: ['electron'],
  outfile: tempOutFile,
});

const { SidecarManager } = await import(pathToFileURL(tempOutFile).href);

test.after(() => {
  try {
    if (fs.existsSync(tempOutFile)) fs.unlinkSync(tempOutFile);
  } catch {
    // Ignore cleanup
  }
});

test('SidecarManager handles focus lease broadcast and state transitions', async () => {
  const receivedBodies = [];
  const server = http.createServer((req, res) => {
    if (req.url === '/twin/lease' && req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        receivedBodies.push(JSON.parse(body));
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok', lease: JSON.parse(body) }));
      });
    } else {
      res.writeHead(404);
      res.end();
    }
  });

  await new Promise(resolve => server.listen(8765, '127.0.0.1', resolve));

  const manager = new SidecarManager(process.cwd());
  let phoneBroadcasts = [];
  manager.setPhoneLeaseSender((focus, leaseMs) => {
    phoneBroadcasts.push({ focus, leaseMs });
  });

  try {
    // 1. Transition to deep focus
    manager.updateFocusState(true);
    await new Promise(r => setTimeout(r, 100));

    assert.equal(phoneBroadcasts.length, 1);
    assert.equal(phoneBroadcasts[0].focus, 'deep');
    assert.equal(phoneBroadcasts[0].leaseMs, 90000);

    assert.equal(receivedBodies.length, 1);
    assert.equal(receivedBodies[0].focus, 'deep');
    assert.equal(receivedBodies[0].leaseMs, 90000);

    // 2. Transition back to normal (pause or distraction)
    manager.updateFocusState(false);
    await new Promise(r => setTimeout(r, 100));

    assert.equal(phoneBroadcasts.length, 2);
    assert.equal(phoneBroadcasts[1].focus, 'normal');
    assert.equal(phoneBroadcasts[1].leaseMs, 0);

    assert.equal(receivedBodies.length, 2);
    assert.equal(receivedBodies[1].focus, 'normal');
    assert.equal(receivedBodies[1].leaseMs, 0);
  } finally {
    await manager.stop();
    await new Promise(resolve => server.close(resolve));
  }
});
