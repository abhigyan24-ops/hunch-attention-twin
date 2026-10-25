import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');
const VALID_DIR = path.join(ROOT, 'contracts/sync/fixtures/valid');
const INVALID_DIR = path.join(ROOT, 'contracts/sync/fixtures/invalid');

const validFixtures = {
  pair_offer: {
    v: 1,
    t: "pair_offer",
    device: "laptop",
    commit: "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
  },
  pair_join: {
    v: 1,
    t: "pair_join",
    device: "phone",
    pk: "BB8UAUa/sbJR+E9N2+DUzc/Xev2YSpUg41eUAh+DErue7JlaCLH6dwTfPcwLUKlmUmP7dxH5X5+KRJxQluR8iSs=",
    n: "AQIDBAUGBwgJCgsMDQ4PEA=="
  },
  pair_reveal: {
    v: 1,
    t: "pair_reveal",
    device: "laptop",
    pk: "BFFcPW6545a5BNP+yn9U/c0MwemXvzddylFa0KbDtANfRTa+OlDzGPv5pUdZAqIhUCvvDVfgjFOyzApW8X2fk1Q=",
    n: "AQIDBAUGBwgJCgsMDQ4PEA=="
  },
  pair_confirm: {
    v: 1,
    t: "pair_confirm",
    device: "laptop",
    mac: "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
  },
  pair_cancel: {
    v: 1,
    t: "pair_cancel",
    device: "phone",
    reason: "commit_mismatch"
  },
  hello: {
    v: 1,
    t: "hello",
    device: "phone",
    sn: "AQIDBAUGBwgJCgsMDQ4PEA==",
    proto: [1],
    lastSeqAcked: 42,
    mac: "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
  },
  encrypted_frame: {
    v: 1,
    t: "events",
    d: "l2p",
    ctr: 1,
    c: "SGVsbG8gV29ybGQgYWVzLWdjbS1jaXBoZXJ0ZXh0LXBsYWNlaG9sZGVyLTE2Ynl0ZXRhZw=="
  },
  ping: {
    t0: 1729864000000
  },
  pong: {
    t0: 1729864000000,
    t1: 1729864000050,
    t2: 1729864000060
  },
  ack: {
    upToSeq: 100
  },
  state: {
    ts: 1729864000000,
    focusState: "deep_focus",
    laptopState: "flowing",
    phoneState: "screen_off",
    confidence: 0.85,
    declared: false,
    heldCount: 2,
    minutesInState: 25.0
  },
  pet_event: {
    kind: "held",
    count: 1
  },
  held_list: {
    total: 1,
    items: [
      {
        heldId: "kh_test_id_123",
        appCat: "social",
        heldAt: 1729864000000,
        expiresAt: 1729867600000,
        status: "HELD"
      }
    ]
  },
  command: {
    cmdId: "123e4567-e89b-12d3-a456-426614174000",
    cmd: "show_now",
    args: { heldId: "kh_test_id_123" },
    issuedTs: 1729864000000,
    exp: 1729864030000
  },
  cmd_ack: {
    cmdId: "123e4567-e89b-12d3-a456-426614174000",
    status: "applied",
    reason: "released"
  },
  error: {
    code: "schema_invalid",
    detail: "missing required field"
  },
  ingest_auth: {
    kind: "auth",
    token: "valid_ingest_token_32_bytes_len_ok",
    client: "vscode",
    clientVersion: "1.0.0"
  },
  ingest_ext_heartbeat: {
    kind: "ext_heartbeat",
    ts: 1729864000000
  },
  ingest_edit_burst: {
    kind: "edit_burst",
    ts: 1729864000000,
    editsPerMin: 120,
    saves: 3,
    errors: 0,
    warnings: 1,
    windowSec: 15
  },
  ingest_resume_first_edit: {
    kind: "resume_first_edit",
    ts: 1729864000000,
    delayMs: 3500
  },
  ingest_vscode_state: {
    kind: "vscode_state",
    ts: 1729864000000,
    state: "flowing",
    windowFocused: true,
    debugging: false
  },
  ingest_tab_category: {
    kind: "tab_category",
    ts: 1729864000000,
    domainCat: "coding",
    surface: "editor",
    videoState: "none",
    scrollCount: 42,
    tabActive: true
  }
};

const invalidFixtures = {
  bad_version: {
    v: 2,
    t: "pair_offer",
    device: "laptop",
    commit: "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
  },
  forbidden_field: {
    ts: 1729864000000,
    focusState: "deep_focus",
    confidence: 0.8,
    declared: false,
    heldCount: 0,
    title: "Leaked Notification Title"
  },
  bad_enum: {
    cmdId: "123e4567-e89b-12d3-a456-426614174000",
    cmd: "reboot_device",
    args: {},
    issuedTs: 1729864000000,
    exp: 1729864030000
  },
  out_of_range: {
    kind: "edit_burst",
    ts: 1729864000000,
    editsPerMin: 5000,
    saves: 1,
    errors: 0,
    warnings: 0,
    windowSec: 15
  },
  extra_property: {
    upToSeq: 42,
    extraField: "not allowed"
  },
  invalid_uuid: {
    cmdId: "not-a-uuid",
    cmd: "end_session",
    args: {},
    issuedTs: 1729864000000,
    exp: 1729864030000
  }
};

for (const [name, obj] of Object.entries(validFixtures)) {
  fs.writeFileSync(path.join(VALID_DIR, `${name}.json`), JSON.stringify(obj, null, 2));
}

for (const [name, obj] of Object.entries(invalidFixtures)) {
  fs.writeFileSync(path.join(INVALID_DIR, `${name}.json`), JSON.stringify(obj, null, 2));
}

console.log(`Wrote ${Object.keys(validFixtures).length} valid and ${Object.keys(invalidFixtures).length} invalid fixtures.`);
