import assert from 'node:assert/strict';
import { PrivacyGate } from '../../src/privacy/gate.ts';
import { EVENT_SCHEMAS } from '../../src/privacy/schema.ts';

describe('PrivacyGate and No-Leak Invariants', () => {
  const CANARY_SECRET = 'SECRET_TOKEN_CANARY_X99';
  const CANARY_FILE_PATH = 'C:\\Users\\Developer\\PrivateProject\\sensitive.ts';
  const CANARY_UNIX_PATH = '/home/user/workspace/super_secret.key';
  const CANARY_BRANCH = 'feature/top-secret-branch';
  const CANARY_CMD = 'git commit -m "leaked private commit message"';
  const CANARY_INTENTION = 'Thinking about private personal thoughts';

  const canaryList = [
    CANARY_SECRET,
    CANARY_FILE_PATH,
    CANARY_UNIX_PATH,
    CANARY_BRANCH,
    CANARY_CMD,
    CANARY_INTENTION
  ];

  it('rejects unknown event types', () => {
    const raw = {
      type: 'malicious_event',
      secret: CANARY_SECRET,
      data: 123
    };
    const sanitized = PrivacyGate.sanitize(raw);
    assert.equal(sanitized, null, 'Unknown event type must return null');
  });

  it('rejects non-object inputs', () => {
    assert.equal(PrivacyGate.sanitize(null), null);
    assert.equal(PrivacyGate.sanitize(undefined), null);
    assert.equal(PrivacyGate.sanitize('string'), null);
    assert.equal(PrivacyGate.sanitize(123), null);
    assert.equal(PrivacyGate.sanitize([]), null);
  });

  it('sanitizes every defined event type in a property-style loop without leaking forbidden fields', () => {
    for (const eventType of Object.keys(EVENT_SCHEMAS)) {
      // Inject all prohibited canaries into root and nested fields
      const dirtyEvent: Record<string, unknown> = {
        type: eventType,
        kind: eventType,
        secretField: CANARY_SECRET,
        path: CANARY_FILE_PATH,
        filePath: CANARY_UNIX_PATH,
        branchName: CANARY_BRANCH,
        terminalCommand: CANARY_CMD,
        rawText: 'console.log("leaked code")',
        intentionNote: CANARY_INTENTION,
        // valid schema fields with safe values:
        ts: Date.now(),
        mono: 12345,
        seq: 1
      };

      // Add dummy valid fields for specific event types
      if (eventType === 'hello') {
        dirtyEvent.v = 1;
        dirtyEvent.instanceId = 'test-inst-123';
        dirtyEvent.windowId = 'win-1';
        dirtyEvent.vscodeVersion = '1.93.0';
        dirtyEvent.platform = 'win32';
        dirtyEvent.remote = false;
        dirtyEvent.capabilities = {
          windowStateActive: true,
          forbiddenNested: CANARY_SECRET
        };
      } else if (eventType === 'ed_summary') {
        dirtyEvent.windowStart = 1000;
        dirtyEvent.durMs = 10000;
        dirtyEvent.focusedMs = 9000;
        dirtyEvent.activeMs = 8000;
        dirtyEvent.taskKey = 'a1b2c3d4e5f60718';
        dirtyEvent.taskType = 'coding';
        dirtyEvent.lang = 'typescript';
        dirtyEvent.typingChars = 120;
        dirtyEvent.deletions = 5;
        dirtyEvent.dirtyDocs = 2;
        dirtyEvent.openTabs = 4;
        dirtyEvent.gapP50Ms = 240;
        dirtyEvent.gapP90Ms = 850;
        dirtyEvent.longPauseRatio = 0.05;
      } else if (eventType === 'ed_break') {
        dirtyEvent.kind = 'save_green';
        dirtyEvent.confidence = 0.7;
      } else if (eventType === 'ed_resume') {
        dirtyEvent.awayMs = 180000;
        dirtyEvent.assist = true;
        dirtyEvent.propensity = 0.8;
        dirtyEvent.experiment = true;
        dirtyEvent.used = false;
        dirtyEvent.lagMs = 4200;
        dirtyEvent.resumed = true;
        dirtyEvent.taskKey = 'a1b2c3d4e5f60718';
        dirtyEvent.load = {
          dirtyDocs: 1,
          openTabs: 3,
          debugPaused: false,
          uncommitted: 0,
          taskType: 'coding',
          nestedSecret: CANARY_SECRET
        };
      }

      const sanitized = PrivacyGate.sanitize(dirtyEvent);
      assert.ok(sanitized, `Sanitizer returned null for valid event type: ${eventType}`);

      // Serialize to string to detect any sub-string leak
      const serialized = JSON.stringify(sanitized);

      for (const canary of canaryList) {
        assert.ok(
          !serialized.includes(canary),
          `Privacy leak detected! Event '${eventType}' contained canary '${canary}' in output: ${serialized}`
        );
      }

      // Assert forbidden fields were dropped
      assert.equal(sanitized.secretField, undefined);
      assert.equal(sanitized.path, undefined);
      assert.equal(sanitized.filePath, undefined);
      assert.equal(sanitized.branchName, undefined);
      assert.equal(sanitized.terminalCommand, undefined);
      assert.equal(sanitized.rawText, undefined);
      assert.equal(sanitized.intentionNote, undefined);
    }
  });

  it('drops string values exceeding maxStringLength', () => {
    const raw = {
      type: 'ed_summary',
      taskKey: 'this_string_is_way_longer_than_the_allowed_16_chars_limit',
      lang: 'typescript',
      ts: 1000
    };
    const sanitized = PrivacyGate.sanitize(raw);
    assert.ok(sanitized);
    assert.equal(sanitized.taskKey, undefined, 'Overlong taskKey must be dropped');
    assert.equal(sanitized.lang, 'typescript');
  });

  it('drops strings that contain path separators or url protocols', () => {
    const raw = {
      type: 'ed_summary',
      lang: 'path/to/some/file.ts',
      ts: 1000
    };
    const sanitized = PrivacyGate.sanitize(raw);
    assert.ok(sanitized);
    assert.equal(sanitized.lang, undefined, 'String containing path separator must be dropped');
  });
});
