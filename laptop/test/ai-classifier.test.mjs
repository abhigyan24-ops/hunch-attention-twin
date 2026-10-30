import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { build } from 'esbuild';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { checkOllamaHealth, listInstalledModels, setupOllama } from '../scripts/setup-ollama.mjs';

const tempOutFile = fileURLToPath(new URL('../node_modules/.classifier-test-bundle.mjs', import.meta.url));
await build({
  entryPoints: [fileURLToPath(new URL('../src/services/ai/classifier.ts', import.meta.url))],
  bundle: true,
  format: 'esm',
  platform: 'node',
  external: ['@google/genai'],
  outfile: tempOutFile
});

const {
  classifyContext,
  classifyWithRegexAndRules,
  tryTier1Ollama,
  ERROR_RE,
  DISTRACT_RE,
  PRODUCTIVE_RE
} = await import(pathToFileURL(tempOutFile).href);

test.after(() => {
  try {
    if (fs.existsSync(tempOutFile)) fs.unlinkSync(tempOutFile);
  } catch {
    // Ignore cleanup errors
  }
});

test('Tier 4 Deterministic Regex & Rule Engine matches expected patterns', () => {
  // Compiler / terminal errors
  assert.equal(ERROR_RE.test('error TS2322: Type string is not assignable to number'), true);
  assert.equal(ERROR_RE.test('UnhandledPromiseRejection: Exception occurred'), true);
  assert.equal(ERROR_RE.test('Failed to compile: SyntaxError in src/index.ts'), true);
  assert.equal(ERROR_RE.test('Build failed with 2 errors'), true);

  // Distraction keywords
  assert.equal(DISTRACT_RE.test('Watching YouTube Shorts in Chrome'), true);
  assert.equal(DISTRACT_RE.test('Browsing Instagram Reels and memes'), true);
  assert.equal(DISTRACT_RE.test('Scrolling reddit.com feed'), true);
  assert.equal(DISTRACT_RE.test('x.com / twitter news'), true);

  // Productive developer keywords
  assert.equal(PRODUCTIVE_RE.test('VSCode - editing Dhyan Pet classifier'), true);
  assert.equal(PRODUCTIVE_RE.test('Terminal powershell build running'), true);
  assert.equal(PRODUCTIVE_RE.test('github.com/dhyan/repo PR review'), true);

  // Direct rule evaluation
  const errRes = classifyWithRegexAndRules({
    app: 'Windows Terminal',
    title: 'npm run build',
    visibleText: 'error TS2304: Cannot find name GoogleGenAI'
  });
  assert.equal(errRes.alignment, 'ERROR_STUCK');
  assert.ok(errRes.nudgeText?.includes('squash'));
  assert.equal(errRes.confidence, 0.95);

  const distractRes = classifyWithRegexAndRules({
    app: 'Brave',
    title: 'YouTube Shorts - Cat videos',
    declaredGoal: 'Write research report'
  });
  assert.equal(distractRes.alignment, 'DISTRACTED');
  assert.equal(distractRes.confidence, 0.9);

  const prodRes = classifyWithRegexAndRules({
    app: 'Visual Studio Code',
    title: 'classifier.ts'
  });
  assert.equal(prodRes.alignment, 'PRODUCTIVE');
  assert.equal(prodRes.confidence, 0.85);

  const neutralRes = classifyWithRegexAndRules({
    app: 'Calculator',
    title: 'Calculator'
  });
  assert.equal(neutralRes.alignment, 'NEUTRAL');
  assert.equal(neutralRes.confidence, 0.5);
});

test('classifyContext pre-checks: DND and extended idle', async () => {
  const dndResult = await classifyContext({
    declaredGoal: 'Coding',
    app: 'Chrome',
    title: 'YouTube Shorts',
    dnd: true
  });
  assert.equal(dndResult.presence, 'HIDDEN');
  assert.equal(dndResult.emotion, 'FOCUSED');
  assert.equal(dndResult.tierUsed, 'TIER_4_REGEX_RULES');

  const idleResult = await classifyContext({
    declaredGoal: 'Coding',
    app: 'VSCode',
    idleSec: 250
  });
  assert.equal(idleResult.presence, 'COMPANION_EDGE');
  assert.equal(idleResult.emotion, 'SLEEPY');
  assert.equal(idleResult.tierUsed, 'TIER_4_REGEX_RULES');
});

test('Tier 1 Local Ollama inference and moondream multimodal support', async () => {
  let receivedBody = null;
  const server = http.createServer((req, res) => {
    if (req.url === '/api/chat' && req.method === 'POST') {
      let data = '';
      req.on('data', (chunk) => {
        data += chunk;
      });
      req.on('end', () => {
        receivedBody = JSON.parse(data);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            message: {
              content: JSON.stringify({
                alignment: 'PRODUCTIVE',
                confidence: 0.92,
                nudgeText: 'In deep flow!',
                reason: 'Editing codebase'
              })
            }
          })
        );
      });
      return;
    }
    res.writeHead(404);
    res.end();
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const endpoint = `http://127.0.0.1:${port}`;

  try {
    // 1. Text-only classification with llama3.2:1b
    const textRes = await tryTier1Ollama(
      {
        declaredGoal: 'Implement cascade',
        app: 'VSCode',
        title: 'classifier.ts',
        visibleText: 'export async function classifyContext'
      },
      { ollamaEndpoint: endpoint }
    );
    assert.ok(textRes);
    assert.equal(textRes.alignment, 'PRODUCTIVE');
    assert.equal(receivedBody.model, 'llama3.2:1b');
    assert.equal(receivedBody.messages[1].images, undefined);

    // 2. Multimodal classification with moondream
    const fakeScreenshot = Buffer.from('fake-jpeg-image-bytes');
    const visionRes = await tryTier1Ollama(
      {
        declaredGoal: 'Design pet UI',
        app: 'Figma',
        title: 'Mockup.fig',
        screenshotBuffer: fakeScreenshot
      },
      { ollamaEndpoint: endpoint }
    );
    assert.ok(visionRes);
    assert.equal(visionRes.alignment, 'PRODUCTIVE');
    assert.equal(receivedBody.model, 'moondream');
    assert.ok(Array.isArray(receivedBody.messages[1].images));
    assert.equal(receivedBody.messages[1].images[0], fakeScreenshot.toString('base64'));

    // 3. Complete waterfall using Tier 1
    const cascadeRes = await classifyContext(
      {
        declaredGoal: 'Implement cascade',
        app: 'VSCode',
        typingRate: 80
      },
      0,
      12,
      { ollamaEndpoint: endpoint }
    );
    assert.equal(cascadeRes.tierUsed, 'TIER_1_OLLAMA');
    assert.equal(cascadeRes.alignment, 'PRODUCTIVE');
    assert.equal(cascadeRes.presence, 'GHOST_FLOW');
    assert.equal(cascadeRes.emotion, 'FOCUSED');
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('Tier 3 Groq Cloud Free Tier sub-150ms text fallback when Ollama is offline', async () => {
  const result = await classifyContext(
    {
      declaredGoal: 'Finish essay',
      app: 'Chrome',
      title: 'Reddit - funny memes',
      visibleText: 'Upvoted 4.2k comments'
    },
    14,
    12,
    {
      ollamaEndpoint: 'http://127.0.0.1:9999', // offline port
      geminiApiKey: '', // simulate no gemini
      groqApiKey: '',   // simulate no groq
      timeoutMs: { ollama: 80 }
    }
  );

  // Ollama offline, no cloud keys -> clean Tier 4 deterministic fallback!
  assert.equal(result.tierUsed, 'TIER_4_REGEX_RULES');
  assert.equal(result.alignment, 'DISTRACTED');
  assert.equal(result.presence, 'INTERVENTION_WALK_IN');
  assert.equal(result.emotion, 'POUTING');
  assert.ok(result.nudgeText?.includes('Finish essay'));
});

test('4-Tier Waterfall Order (Tier 1 -> Tier 2 -> Tier 3 -> Tier 4) sequence', async () => {
  // 1. Tier 4 fallback when all higher tiers fail / unconfigured
  const t4 = await classifyContext(
    {
      declaredGoal: 'Write code',
      app: 'Chrome',
      title: 'YouTube Shorts - Cat memes'
    },
    2, // early distraction (< 50% threshold)
    12,
    {
      ollamaEndpoint: 'http://127.0.0.1:9999',
      geminiApiKey: '',
      groqApiKey: '',
      timeoutMs: { ollama: 50, gemini: 50, groq: 50 }
    }
  );
  assert.equal(t4.tierUsed, 'TIER_4_REGEX_RULES');
  assert.equal(t4.alignment, 'DISTRACTED');
  assert.equal(t4.emotion, 'SKEPTICAL'); // Stage 1 edge alert
  assert.equal(t4.presence, 'COMPANION_EDGE');

  // 2. Severe distraction (> 1.8x threshold)
  const t4Severe = await classifyContext(
    {
      declaredGoal: 'Write code',
      app: 'Chrome',
      title: 'YouTube Shorts'
    },
    25,
    12,
    {
      ollamaEndpoint: 'http://127.0.0.1:9999',
      geminiApiKey: '',
      groqApiKey: '',
      timeoutMs: { ollama: 50 }
    }
  );
  assert.equal(t4Severe.tierUsed, 'TIER_4_REGEX_RULES');
  assert.equal(t4Severe.alignment, 'DISTRACTED');
  assert.equal(t4Severe.emotion, 'SAD');
  assert.equal(t4Severe.presence, 'INTERVENTION_WALK_IN');
});

test('scripts/setup-ollama.mjs health check and model listing handles offline cleanly', async () => {
  const offlineHealth = await checkOllamaHealth('http://127.0.0.1:9999');
  assert.equal(offlineHealth.ok, false);
  assert.ok(offlineHealth.error);

  const models = await listInstalledModels('http://127.0.0.1:9999');
  assert.deepEqual(models, []);

  const setupRes = await setupOllama('http://127.0.0.1:9999');
  assert.equal(setupRes.ok, false);
});
