import test from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { startMockIngest } from '../mock-ingest/server.mjs';
import { classifyUrl } from '../../browser-ext/domain-classifier.js';
import { DhyanBrowserSensor } from '../../browser-ext/service-worker.js';
import { validateIngest } from '../lib/contract-validate.mjs';
import { scanObjectForPrivacyViolations } from '../privacy-scan.mjs';

test('domain classifier maps URLs to 14 categories and never leaks URLs', () => {
  // Test covering taxonomy categories
  assert.equal(classifyUrl('https://github.com/dhyan/app'), 'coding');
  assert.equal(classifyUrl('https://stackoverflow.com/questions/123'), 'coding');
  assert.equal(classifyUrl('https://developer.mozilla.org/en-US/docs/Web'), 'docs');
  assert.equal(classifyUrl('https://discord.com/channels/123/456'), 'communication');
  assert.equal(classifyUrl('https://web.whatsapp.com/'), 'communication');
  assert.equal(classifyUrl('https://x.com/home'), 'social');
  assert.equal(classifyUrl('https://reddit.com/r/programming'), 'social');
  assert.equal(classifyUrl('https://www.youtube.com/watch?v=123'), 'video');
  assert.equal(classifyUrl('https://tiktok.com/@user/video/123'), 'short_video');
  assert.equal(classifyUrl('https://store.steampowered.com/app/123'), 'games');
  assert.equal(classifyUrl('https://www.amazon.com/dp/B000'), 'shopping');
  assert.equal(classifyUrl('https://news.ycombinator.com/'), 'news');
  assert.equal(classifyUrl('https://open.spotify.com/track/123'), 'music');
  assert.equal(classifyUrl('https://www.coursera.org/learn/crypto'), 'learning');
  assert.equal(classifyUrl('https://calendar.google.com/'), 'utility');
  assert.equal(classifyUrl('https://random-unknown-site-12345.org'), 'unknown');
  assert.equal(classifyUrl(null), 'unknown');
  assert.equal(classifyUrl(''), 'unknown');
});

test('browser sensor connects, authenticates with origin, and emits tab_category frames', async () => {
  const token = 'test-token-browser-32-chars-long';
  const extensionId = 'test-browser-extension-id';
  const ingest = await startMockIngest({
    port: 0,
    token,
    allowedExtensionIds: [extensionId]
  });

  const sensor = new DhyanBrowserSensor({
    port: ingest.port,
    token,
    extensionId,
    WebSocketClass: WebSocket
  });

  sensor.start();

  // Wait for authentication
  await new Promise((resolve) => {
    const check = setInterval(() => {
      if (sensor.authenticated) {
        clearInterval(check);
        resolve();
      }
    }, 20);
  });

  assert.equal(sensor.authenticated, true);
  assert.equal(ingest.stats.authSuccess, 1);

  // 1. Simulate tab navigation: navigates to github
  sensor.onTabUrlChange('https://github.com/dhyan/app/pull/123', true);

  // 2. Simulate content update: video playing and scrolling
  sensor.onContentUpdate({
    surface: 'editor',
    videoState: 'none',
    scrollCount: 15
  });

  // 3. Simulate YouTube navigation
  sensor.onTabUrlChange('https://www.youtube.com/watch?v=abcdef', true);
  sensor.onContentUpdate({
    surface: 'watch',
    videoState: 'playing',
    scrollCount: 2
  });

  // Wait for network flush
  await new Promise(r => setTimeout(r, 100));

  const events = ingest.stats.eventsReceived;
  assert.ok(events.length >= 3, `Expected at least 3 events, got ${events.length}`);

  for (const ev of events) {
    assert.equal(ev.kind, 'tab_category');
    assert.equal(ev.tabActive, true);
    assert.ok(typeof ev.domainCat === 'string');
    assert.ok(typeof ev.surface === 'string');
    assert.ok(typeof ev.videoState === 'string');
    assert.ok(typeof ev.scrollCount === 'number');

    // Schema validation
    const validRes = validateIngest(ev);
    assert.ok(validRes.valid, `Event failed schema validation: ${validRes.errors.join(', ')}`);

    // Privacy scan: guarantees NO raw URL, title, or forbidden field is in the frame
    const privacyViolations = scanObjectForPrivacyViolations(ev);
    assert.equal(privacyViolations.length, 0, `Privacy violation detected in tab_category: ${privacyViolations.join(', ')}`);
  }

  sensor.stop();
  await ingest.close();
});
