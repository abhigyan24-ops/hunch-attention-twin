import test from 'node:test';
import assert from 'node:assert/strict';
import { startMockRelay } from '../mock-relay/server.mjs';
import { runIngestAttackSuite } from '../mock-ingest/attack-suite.mjs';
import { MockPeer } from '../mock-peer/index.mjs';

test('mock-ingest handles all attack cases correctly', async () => {
  const attackResults = await runIngestAttackSuite();
  assert.ok(attackResults.passed >= 6, `Expected at least 6 passed attack tests, got ${attackResults.passed}`);
  assert.equal(attackResults.failed, 0);
});

test('mock-relay and mock-peers execute complete authenticated pairing and session communication', async () => {
  const relay = await startMockRelay({ port: 0 });
  const relayUrl = `ws://127.0.0.1:${relay.port}/ws`;

  const code = '234567';

  const laptop = new MockPeer({ role: 'laptop', relayUrl });
  const phone = new MockPeer({ role: 'phone', relayUrl });

  // 1. Run pairing
  const [laptopPairRes, phonePairRes] = await Promise.all([
    laptop.pair({ code }),
    phone.pair({ code })
  ]);

  // Both should have identical SAS digits, PK, and session room ID
  assert.equal(laptopPairRes.sas, phonePairRes.sas);
  assert.equal(laptopPairRes.sid, phonePairRes.sid);
  assert.equal(laptopPairRes.PK.toString('hex'), phonePairRes.PK.toString('hex'));

  laptop.close();
  phone.close();

  // 2. Connect to permanent session room
  const sid = laptopPairRes.sid;
  const PK = laptopPairRes.PK;

  const laptopSession = new MockPeer({ role: 'laptop', relayUrl });
  const phoneSession = new MockPeer({ role: 'phone', relayUrl });

  await Promise.all([
    laptopSession.connectSession({ sid, PK }),
    phoneSession.connectSession({ sid, PK })
  ]);

  // Verify both derived matching session keys
  assert.equal(laptopSession.kL2P.toString('hex'), phoneSession.kL2P.toString('hex'));
  assert.equal(laptopSession.kP2L.toString('hex'), phoneSession.kP2L.toString('hex'));

  // 3. Normal encrypted message transmission (laptop to phone)
  const phoneReceived = new Promise((resolve) => {
    phoneSession.onMessageCallback = (t, body) => {
      resolve({ t, body });
    };
  });

  laptopSession.sendFrame('ping', { t0: 1729864000000 });
  const receivedMsg = await phoneReceived;
  assert.equal(receivedMsg.t, 'ping');
  assert.equal(receivedMsg.body.b.t0, 1729864000000);

  // 4. Attack tests on session:
  // Attack A: Flipped ciphertext byte
  laptopSession.sendFrame('ping', { t0: 1729864000001 }, { tamper: true });
  await new Promise(r => setTimeout(r, 50));
  assert.ok((phoneSession.rejections.get('tag_failed') || 0) >= 1);

  // Attack B: Replayed counter (send with ctr = 1 which was already processed)
  laptopSession.sendFrame('ping', { t0: 1729864000002 }, { ctr: 1 });
  await new Promise(r => setTimeout(r, 50));
  assert.ok((phoneSession.rejections.get('replay_or_old_counter') || 0) >= 1);

  // Attack C: Wrong direction (laptop sending d = 'p2l')
  laptopSession.sendFrame('pong', { t0: 1 }, { d: 'p2l' });
  await new Promise(r => setTimeout(r, 50));
  assert.ok((phoneSession.rejections.get('wrong_direction') || 0) >= 1);

  // Attack D: Wrong key
  laptopSession.sendFrame('ping', { t0: 1729864000003 }, { wrongKey: true });
  await new Promise(r => setTimeout(r, 50));
  assert.ok((phoneSession.rejections.get('tag_failed') || 0) >= 2);

  laptopSession.close();
  phoneSession.close();
  await relay.close();
});
