import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as c from '../../contracts/sync/ref/crypto.mjs';
import { generateVectors } from '../lib/contract-vectors.mjs';

const vectors = JSON.parse(fs.readFileSync(new URL('../../contracts/sync/test-vectors.json', import.meta.url)));

test('public vectors reproduce deterministically', () => {
  const generated = generateVectors();
  assert.deepEqual(generated, vectors);
  assert.deepEqual(generateVectors(), generateVectors());
});

test('HKDF matches RFC 5869 SHA256 case 1', () => {
  const ikm = Buffer.alloc(22, 0x0b);
  const salt = Buffer.from('000102030405060708090a0b0c', 'hex');
  const info = Buffer.from('f0f1f2f3f4f5f6f7f8f9', 'hex');
  const okm = c.hkdfSha256(ikm, salt, info, 42);
  assert.equal(
    okm.toString('hex'),
    '3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865'
  );
});

test('P256 key pair generation and shared secret match vectors', () => {
  const skL = Buffer.from(vectors.laptopKeys.privateScalarHex, 'hex');
  const skP = Buffer.from(vectors.phoneKeys.privateScalarHex, 'hex');
  const pairL = c.generateP256KeyPair(skL);
  const pairP = c.generateP256KeyPair(skP);

  assert.equal(pairL.publicKey.toString('hex'), vectors.laptopKeys.publicKeyHex);
  assert.equal(pairP.publicKey.toString('hex'), vectors.phoneKeys.publicKeyHex);

  const secretL = c.computeECDH(skL, pairP.publicKey);
  const secretP = c.computeECDH(skP, pairL.publicKey);
  assert.deepEqual(secretL, secretP);
  assert.equal(secretL.toString('hex'), vectors.pairingAgreement.sharedSecretHex);
});

test('pairing transcript, SAS digits, and confirm MACs match vectors', () => {
  const pkL = Buffer.from(vectors.laptopKeys.publicKeyHex, 'hex');
  const pkP = Buffer.from(vectors.phoneKeys.publicKeyHex, 'hex');
  const nL = Buffer.from(vectors.laptopKeys.nonceHex, 'hex');
  const nP = Buffer.from(vectors.phoneKeys.nonceHex, 'hex');

  const commitL = c.computePairCommit(pkL, nL);
  assert.equal(commitL.toString('hex'), vectors.laptopKeys.commitHex);

  const T = c.computeTranscriptHash(pkL, pkP, nL, nP);
  assert.equal(T.toString('hex'), vectors.pairingAgreement.transcriptHashHex);

  const sas = c.computeSas(T);
  assert.equal(sas, vectors.pairingAgreement.sasDigits);

  const secret = Buffer.from(vectors.pairingAgreement.sharedSecretHex, 'hex');
  const macL = c.computePairConfirmMac(secret, T, 'laptop');
  const macP = c.computePairConfirmMac(secret, T, 'phone');
  assert.equal(macL.toString('base64'), vectors.pairingAgreement.confirmMacLaptopBase64);
  assert.equal(macP.toString('base64'), vectors.pairingAgreement.confirmMacPhoneBase64);

  const { PK, sid } = c.derivePairingKeyAndRoom(secret, T);
  assert.equal(PK.toString('hex'), vectors.pairingAgreement.permanentPairingKeyHex);
  assert.equal(sid, vectors.pairingAgreement.sessionRoomIdHex);
});

test('session handshake, hello MACs, and session keys match vectors', () => {
  const PK = Buffer.from(vectors.pairingAgreement.permanentPairingKeyHex, 'hex');
  const snL = Buffer.from(vectors.sessionHandshake.laptopSessionNonceBase64, 'base64');
  const snP = Buffer.from(vectors.sessionHandshake.phoneSessionNonceBase64, 'base64');

  const helloL = c.computeHelloMac(PK, 'laptop', snL.toString('base64'), vectors.sessionHandshake.lastSeqAckedLaptop);
  const helloP = c.computeHelloMac(PK, 'phone', snP.toString('base64'), vectors.sessionHandshake.lastSeqAckedPhone);
  assert.equal(helloL.toString('base64'), vectors.sessionHandshake.helloMacLaptopBase64);
  assert.equal(helloP.toString('base64'), vectors.sessionHandshake.helloMacPhoneBase64);

  const { k_l2p, k_p2l } = c.deriveSessionKeys(PK, snL, snP);
  assert.equal(k_l2p.toString('hex'), vectors.sessionHandshake.keyL2PHex);
  assert.equal(k_p2l.toString('hex'), vectors.sessionHandshake.keyP2LHex);
});

test('both directions authenticate and reject changed metadata, key and ciphertext', () => {
  const k_l2p = Buffer.from(vectors.sessionHandshake.keyL2PHex, 'hex');
  const k_p2l = Buffer.from(vectors.sessionHandshake.keyP2LHex, 'hex');

  // L2P frame
  const l2p = vectors.frameL2P;
  const decL2P = c.decryptFramePayload(k_l2p, l2p.t, l2p.d, l2p.ctr, l2p.ciphertextWithTagBase64);
  assert.equal(decL2P.toString('utf8'), l2p.plaintextJson);

  // P2L frame
  const p2l = vectors.frameP2L;
  const decP2L = c.decryptFramePayload(k_p2l, p2l.t, p2l.d, p2l.ctr, p2l.ciphertextWithTagBase64);
  assert.equal(decP2L.toString('utf8'), p2l.plaintextJson);

  // Rejection tests:
  // 1. Altered ciphertext
  const rawCipherBuf = Buffer.from(l2p.ciphertextWithTagBase64, 'base64');
  const tampered = Buffer.from(rawCipherBuf);
  tampered[0] ^= 0x01;
  assert.throws(() => {
    c.decryptFramePayload(k_l2p, l2p.t, l2p.d, l2p.ctr, tampered.toString('base64'));
  });

  // 2. Wrong tag
  const badTag = Buffer.from(rawCipherBuf);
  badTag[badTag.length - 1] ^= 0x01;
  assert.throws(() => {
    c.decryptFramePayload(k_l2p, l2p.t, l2p.d, l2p.ctr, badTag.toString('base64'));
  });

  // 3. Changed AAD (type)
  assert.throws(() => {
    c.decryptFramePayload(k_l2p, 'pong', l2p.d, l2p.ctr, l2p.ciphertextWithTagBase64);
  });

  // 4. Changed AAD (direction)
  assert.throws(() => {
    c.decryptFramePayload(k_l2p, l2p.t, 'p2l', l2p.ctr, l2p.ciphertextWithTagBase64);
  });

  // 5. Changed AAD (counter)
  assert.throws(() => {
    c.decryptFramePayload(k_l2p, l2p.t, l2p.d, l2p.ctr + 1, l2p.ciphertextWithTagBase64);
  });

  // 6. Wrong key
  assert.throws(() => {
    c.decryptFramePayload(k_p2l, l2p.t, l2p.d, l2p.ctr, l2p.ciphertextWithTagBase64);
  });
});

test('counter nonce is big endian with 12 bytes', () => {
  for (const n of [1, 17, 256, 1000000000]) {
    const nonce = c.buildNonce(n);
    assert.equal(nonce.length, 12);
    assert.equal(nonce.subarray(0, 4).toString('hex'), '00000000');
    assert.equal(nonce.readBigUInt64BE(4), BigInt(n));
  }
});

test('SAS preserves leading zeros and formats 6 digits grouped 3-3', () => {
  // Test T where first 4 bytes produce a small number requiring leading zeros
  const buf = Buffer.alloc(32);
  buf.writeUInt32BE(42, 0); // 42 % 1000000 = 000042 -> "000 042"
  assert.equal(c.computeSas(buf), '000 042');

  // Verify pairing code generation constraints
  for (let i = 0; i < 32; i++) {
    const code = c.generatePairingCode();
    assert.match(code, /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/);
  }

  // Rendezvous room ID
  const rRoom = c.getRendezvousRoomId('234567');
  assert.equal(rRoom, vectors.constants.rendezvousRoomId);
});
