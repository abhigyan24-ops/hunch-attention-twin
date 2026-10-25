import { WebSocket } from 'ws';
import crypto from 'crypto';
import {
  generatePairingCode,
  getRendezvousRoomId,
  generateP256KeyPair,
  computeECDH,
  computePairCommit,
  computeTranscriptHash,
  computeSas,
  computePairConfirmMac,
  derivePairingKeyAndRoom,
  computeHelloMac,
  deriveSessionKeys,
  buildNonce,
  buildAad,
  encryptFramePayload,
  decryptFramePayload
} from '../../contracts/sync/ref/crypto.mjs';

export class MockPeer {
  constructor(options = {}) {
    this.role = options.role || 'laptop'; // 'laptop' | 'phone'
    this.relayUrl = options.relayUrl || 'ws://127.0.0.1:8080/ws';
    this.pairingKey = options.pairingKey || null;
    this.sessionId = options.sessionId || null;
    this.ws = null;
    this.connected = false;

    // Keys and nonces
    this.keyPair = null;
    this.nonce = null;
    this.peerPublicKey = null;
    this.peerNonce = null;
    this.secret = null;
    this.transcript = null;
    this.sas = null;

    // Session state
    this.sessionNonce = null;
    this.peerSessionNonce = null;
    this.kL2P = null;
    this.kP2L = null;
    this.sendCtr = 1;
    this.lastReceivedCtr = 0;
    this.lastSeqAcked = 0;

    // Event & message queues
    this.receivedMessages = [];
    this.receivedPlaintexts = [];
    this.rejections = new Map(); // reason -> count
    this.onMessageCallback = null;
  }

  logRejection(reason) {
    const count = this.rejections.get(reason) || 0;
    this.rejections.set(reason, count + 1);
  }

  // --- Pairing Flow ---
  async pair({ code, attack = null } = {}) {
    this.keyPair = generateP256KeyPair();
    this.nonce = crypto.randomBytes(16);

    const roomId = getRendezvousRoomId(code);
    const wsUrl = `${this.relayUrl}?room=${roomId}&device=${this.role}`;

    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(wsUrl);

      this.ws.on('open', () => {
        this.connected = true;
        if (this.role === 'laptop') {
          // 1. Laptop sends pair_offer with commit
          const commit = attack === 'tamper-commit'
            ? crypto.randomBytes(32)
            : computePairCommit(this.keyPair.publicKey, this.nonce);

          this.ws.send(JSON.stringify({
            v: 1,
            t: 'pair_offer',
            device: 'laptop',
            commit: commit.toString('base64')
          }));
        }
      });

      this.ws.on('message', (data) => {
        const frame = JSON.parse(data.toString('utf8'));
        this.receivedMessages.push(frame);

        if (this.role === 'phone') {
          if (frame.t === 'pair_offer') {
            this.laptopCommit = Buffer.from(frame.commit, 'base64');
            // 2. Phone sends pair_join
            this.ws.send(JSON.stringify({
              v: 1,
              t: 'pair_join',
              device: 'phone',
              pk: this.keyPair.publicKey.toString('base64'),
              n: this.nonce.toString('base64')
            }));
          } else if (frame.t === 'pair_reveal') {
            this.peerPublicKey = Buffer.from(frame.pk, 'base64');
            this.peerNonce = Buffer.from(frame.n, 'base64');

            // Verify commit
            const expectedCommit = computePairCommit(this.peerPublicKey, this.peerNonce);
            if (!expectedCommit.equals(this.laptopCommit)) {
              this.logRejection('commit_mismatch');
              this.ws.send(JSON.stringify({ v: 1, t: 'pair_cancel', device: 'phone', reason: 'commit_mismatch' }));
              return;
            }

            // Compute transcript and SAS
            this.secret = computeECDH(this.keyPair.privateKey, this.peerPublicKey);
            this.transcript = computeTranscriptHash(this.peerPublicKey, this.keyPair.publicKey, this.peerNonce, this.nonce);
            this.sas = computeSas(this.transcript);

            if (attack === 'bad-sas') {
              this.logRejection('user_rejected_sas');
              this.ws.send(JSON.stringify({ v: 1, t: 'pair_cancel', device: 'phone', reason: 'user_rejected_sas' }));
              return;
            }

            // Send pair_confirm
            const mac = computePairConfirmMac(this.secret, this.transcript, 'phone');
            this.ws.send(JSON.stringify({
              v: 1,
              t: 'pair_confirm',
              device: 'phone',
              mac: mac.toString('base64')
            }));
          } else if (frame.t === 'pair_confirm') {
            const expectedMac = computePairConfirmMac(this.secret, this.transcript, 'laptop');
            if (!expectedMac.equals(Buffer.from(frame.mac, 'base64'))) {
              this.logRejection('confirm_mac_mismatch');
              this.ws.send(JSON.stringify({ v: 1, t: 'pair_cancel', device: 'phone', reason: 'confirm_mac_mismatch' }));
              return;
            }

            const { PK, sid } = derivePairingKeyAndRoom(this.secret, this.transcript);
            this.pairingKey = PK;
            this.sessionId = sid;
            resolve({ PK, sid, sas: this.sas });
          }
        } else if (this.role === 'laptop') {
          if (frame.t === 'pair_join') {
            this.peerPublicKey = Buffer.from(frame.pk, 'base64');
            this.peerNonce = Buffer.from(frame.n, 'base64');

            // 3. Laptop sends pair_reveal
            this.ws.send(JSON.stringify({
              v: 1,
              t: 'pair_reveal',
              device: 'laptop',
              pk: this.keyPair.publicKey.toString('base64'),
              n: this.nonce.toString('base64')
            }));

            // Compute transcript and SAS
            this.secret = computeECDH(this.keyPair.privateKey, this.peerPublicKey);
            this.transcript = computeTranscriptHash(this.keyPair.publicKey, this.peerPublicKey, this.nonce, this.peerNonce);
            this.sas = computeSas(this.transcript);
          } else if (frame.t === 'pair_confirm') {
            const expectedMac = computePairConfirmMac(this.secret, this.transcript, 'phone');
            if (!expectedMac.equals(Buffer.from(frame.mac, 'base64'))) {
              this.logRejection('confirm_mac_mismatch');
              this.ws.send(JSON.stringify({ v: 1, t: 'pair_cancel', device: 'laptop', reason: 'confirm_mac_mismatch' }));
              return;
            }

            if (attack === 'bad-confirm-mac') {
              this.ws.send(JSON.stringify({
                v: 1,
                t: 'pair_confirm',
                device: 'laptop',
                mac: crypto.randomBytes(32).toString('base64')
              }));
              return;
            }

            // Send pair_confirm
            const mac = computePairConfirmMac(this.secret, this.transcript, 'laptop');
            this.ws.send(JSON.stringify({
              v: 1,
              t: 'pair_confirm',
              device: 'laptop',
              mac: mac.toString('base64')
            }));

            const { PK, sid } = derivePairingKeyAndRoom(this.secret, this.transcript);
            this.pairingKey = PK;
            this.sessionId = sid;
            resolve({ PK, sid, sas: this.sas });
          }
        }
      });

      this.ws.on('error', (err) => reject(err));
    });
  }

  // --- Session Handshake & Encryption ---
  async connectSession({ sid, PK } = {}) {
    this.sessionId = sid || this.sessionId;
    this.pairingKey = PK || this.pairingKey;

    this.sessionNonce = crypto.randomBytes(16);
    this.sendCtr = 1;
    this.lastReceivedCtr = 0;

    const wsUrl = `${this.relayUrl}?room=${this.sessionId}&device=${this.role}`;

    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(wsUrl);

      this.ws.on('open', () => {
        this.connected = true;
        // Send hello
        const snB64 = this.sessionNonce.toString('base64');
        const helloMac = computeHelloMac(this.pairingKey, this.role, snB64, this.lastSeqAcked);

        this.ws.send(JSON.stringify({
          v: 1,
          t: 'hello',
          device: this.role,
          sn: snB64,
          proto: [1],
          lastSeqAcked: this.lastSeqAcked,
          mac: helloMac.toString('base64')
        }));
      });

      this.ws.on('message', (data) => {
        const frame = JSON.parse(data.toString('utf8'));
        this.receivedMessages.push(frame);

        if (frame.t === 'hello') {
          const peerRole = this.role === 'phone' ? 'laptop' : 'phone';
          if (frame.device !== peerRole) {
            this.logRejection('hello_device_mismatch');
            return;
          }

          // Verify Hello MAC
          const expectedMac = computeHelloMac(this.pairingKey, peerRole, frame.sn, frame.lastSeqAcked);
          if (!expectedMac.equals(Buffer.from(frame.mac, 'base64'))) {
            this.logRejection('hello_mac_invalid');
            return;
          }

          this.peerSessionNonce = Buffer.from(frame.sn, 'base64');
          const snL = this.role === 'laptop' ? this.sessionNonce : this.peerSessionNonce;
          const snP = this.role === 'phone' ? this.sessionNonce : this.peerSessionNonce;

          const keys = deriveSessionKeys(this.pairingKey, snL, snP);
          this.kL2P = keys.k_l2p;
          this.kP2L = keys.k_p2l;

          resolve();
        } else if (frame.c !== undefined) {
          // Encrypted session frame
          this.handleEncryptedFrame(frame);
        }
      });

      this.ws.on('error', (err) => reject(err));
    });
  }

  handleEncryptedFrame(frame) {
    const expectedDir = this.role === 'phone' ? 'l2p' : 'p2l';
    if (frame.d !== expectedDir) {
      this.logRejection('wrong_direction');
      return;
    }

    if (frame.ctr <= this.lastReceivedCtr) {
      this.logRejection('replay_or_old_counter');
      return;
    }

    const decryptKey = this.role === 'phone' ? this.kL2P : this.kP2L;

    let plaintextBuf;
    try {
      plaintextBuf = decryptFramePayload(decryptKey, frame.t, frame.d, frame.ctr, frame.c);
    } catch (err) {
      this.logRejection('tag_failed');
      return;
    }

    this.lastReceivedCtr = frame.ctr;

    try {
      const plaintext = JSON.parse(plaintextBuf.toString('utf8'));
      this.receivedPlaintexts.push({ type: frame.t, plaintext });
      if (this.onMessageCallback) {
        this.onMessageCallback(frame.t, plaintext);
      }
    } catch (err) {
      this.logRejection('schema_invalid');
    }
  }

  sendFrame(t, body, options = {}) {
    const ctr = options.ctr !== undefined ? options.ctr : this.sendCtr++;
    const dir = options.d || (this.role === 'laptop' ? 'l2p' : 'p2l');
    let key = this.role === 'laptop' ? this.kL2P : this.kP2L;

    if (options.wrongKey) {
      key = crypto.randomBytes(32);
    }

    const plaintext = JSON.stringify({
      mid: crypto.randomUUID(),
      ts: Date.now(),
      b: body
    });

    let ciphertextBase64 = encryptFramePayload(key, t, dir, ctr, plaintext);

    if (options.tamper) {
      const raw = Buffer.from(ciphertextBase64, 'base64');
      raw[0] ^= 0x01;
      ciphertextBase64 = raw.toString('base64');
    }

    const frame = {
      v: 1,
      t: options.unknownT ? 'unknown_type' : t,
      d: dir,
      ctr,
      c: ciphertextBase64
    };

    this.ws.send(JSON.stringify(frame));
  }

  close() {
    if (this.ws) {
      this.ws.close();
    }
  }
}

// Standalone CLI runner
if (process.argv[1] && process.argv[1].endsWith('tools/mock-peer/index.mjs')) {
  console.log('Mock Peer CLI ready');
}
