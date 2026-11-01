import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { EndpointDiscovery } from '../../src/link/endpoint.ts';

describe('Endpoint Discovery (~/.dhyan/endpoint.json)', () => {
  let tempDir: string;
  let endpointFile: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-endpoint-test-'));
    endpointFile = path.join(tempDir, 'endpoint.json');
  });

  afterEach(() => {
    try {
      fs.rmSync(tempDir, { recursive: true, force: true });
    } catch {}
  });

  it('silently returns null if endpoint file does not exist', () => {
    const discovery = new EndpointDiscovery(endpointFile);
    assert.equal(discovery.readEndpoint(), null);
    discovery.dispose();
  });

  it('reads and parses valid endpoint file', () => {
    const discovery = new EndpointDiscovery(endpointFile);
    fs.writeFileSync(endpointFile, JSON.stringify({
      v: 1,
      port: 54321,
      token: 'secret-endpoint-token-123',
      pid: 1234
    }), 'utf8');

    const info = discovery.readEndpoint();
    assert.ok(info);
    assert.equal(info.port, 54321);
    assert.equal(info.token, 'secret-endpoint-token-123');
    discovery.dispose();
  });

  it('handles empty or corrupt JSON file silently', () => {
    const discovery = new EndpointDiscovery(endpointFile);
    fs.writeFileSync(endpointFile, '   ', 'utf8');
    assert.equal(discovery.readEndpoint(), null);

    fs.writeFileSync(endpointFile, '{ broken json', 'utf8');
    assert.equal(discovery.readEndpoint(), null);
    discovery.dispose();
  });
});
