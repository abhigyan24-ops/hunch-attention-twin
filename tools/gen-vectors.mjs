import {createHash} from 'node:crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import {
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
  getRendezvousRoomId
} from '../contracts/sync/ref/crypto.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');
const VECTORS_FILE = path.join(ROOT, 'contracts/sync/test-vectors.json');

export function generateTestVectors() {
  const skL = Buffer.from('0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 'hex');
  const skP = Buffer.from('2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40', 'hex');

  const pairL = generateP256KeyPair(skL);
  const pairP = generateP256KeyPair(skP);

  const nL = Buffer.from('a0a1a2a3a4a5a6a7a8a9aaabacadaeaf', 'hex');
  const nP = Buffer.from('b0b1b2b3b4b5b6b7b8b9babbbcbdbebf', 'hex');

  const code = "234567";
  const rendezvousRoomId = getRendezvousRoomId(code);

  const commitL = computePairCommit(pairL.publicKey, nL);

  const secretL = computeECDH(skL, pairP.publicKey);
  const secretP = computeECDH(skP, pairL.publicKey);
  if (!secretL.equals(secretP)) {
    throw new Error("ECDH computation mismatch between laptop and phone!");
  }

  const T = computeTranscriptHash(pairL.publicKey, pairP.publicKey, nL, nP);
  const sas = computeSas(T);

  const confirmMacL = computePairConfirmMac(secretL, T, 'laptop');
  const confirmMacP = computePairConfirmMac(secretP, T, 'phone');

  const { PK, sid } = derivePairingKeyAndRoom(secretL, T);

  // Session hello
  const snL = Buffer.from('c0c1c2c3c4c5c6c7c8c9cacbcccdcecf', 'hex');
  const snP = Buffer.from('d0d1d2d3d4d5d6d7d8d9dadbdcdddedf', 'hex');
  const lastSeqAckedL = 100;
  const lastSeqAckedP = 50;

  const helloMacL = computeHelloMac(PK, 'laptop', snL.toString('base64'), lastSeqAckedL);
  const helloMacP = computeHelloMac(PK, 'phone', snP.toString('base64'), lastSeqAckedP);

  const { k_l2p, k_p2l } = deriveSessionKeys(PK, snL, snP);

  // Encrypted frames
  const l2pPlaintext = JSON.stringify({
    mid: "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
    ts: 1729864000000,
    b: { t0: 1729864000000 }
  });
  const l2pCtr = 1;
  const l2pNonce = buildNonce(l2pCtr);
  const l2pAad = buildAad("ping", "l2p", l2pCtr);
  const l2pCiphertextBase64 = encryptFramePayload(k_l2p, "ping", "l2p", l2pCtr, l2pPlaintext);

  const p2lPlaintext = JSON.stringify({
    mid: "b2c3d4e5-f6a1-4b5c-9d0e-1f2a3b4c5d6e",
    ts: 1729864000020,
    b: { t0: 1729864000000, t1: 1729864000010, t2: 1729864000020 }
  });
  const p2lCtr = 1;
  const p2lNonce = buildNonce(p2lCtr);
  const p2lAad = buildAad("pong", "p2l", p2lCtr);
  const p2lCiphertextBase64 = encryptFramePayload(k_p2l, "pong", "p2l", p2lCtr, p2lPlaintext);

  return {
    description: "PUBLIC TEST ONLY: Cross-language crypto test vectors for Dhyan Sync Protocol v1.1.0",
    contractVersion: '1.1.0',
    generatedAt: "2026-10-25T21:45:00.000Z",
    constants: {
      pairingCode: code,
      rendezvousRoomId
    },
    laptopKeys: {
      privateScalarHex: skL.toString('hex'),
      publicKeyHex: pairL.publicKey.toString('hex'),
      publicKeyBase64: pairL.publicKey.toString('base64'),
      nonceHex: nL.toString('hex'),
      nonceBase64: nL.toString('base64'),
      commitHex: commitL.toString('hex'),
      commitBase64: commitL.toString('base64')
    },
    phoneKeys: {
      privateScalarHex: skP.toString('hex'),
      publicKeyHex: pairP.publicKey.toString('hex'),
      publicKeyBase64: pairP.publicKey.toString('base64'),
      nonceHex: nP.toString('hex'),
      nonceBase64: nP.toString('base64')
    },
    pairingAgreement: {
      sharedSecretHex: secretL.toString('hex'),
      transcriptHashHex: T.toString('hex'),
      sasDigits: sas,
      confirmMacLaptopBase64: confirmMacL.toString('base64'),
      confirmMacPhoneBase64: confirmMacP.toString('base64'),
      permanentPairingKeyHex: PK.toString('hex'),
      sessionRoomIdHex: sid
    },
    sessionHandshake: {
      laptopSessionNonceBase64: snL.toString('base64'),
      phoneSessionNonceBase64: snP.toString('base64'),
      lastSeqAckedLaptop: lastSeqAckedL,
      lastSeqAckedPhone: lastSeqAckedP,
      helloMacLaptopBase64: helloMacL.toString('base64'),
      helloMacPhoneBase64: helloMacP.toString('base64'),
      keyL2PHex: k_l2p.toString('hex'),
      keyP2LHex: k_p2l.toString('hex')
    },
    frameL2P: {
      t: "ping",
      d: "l2p",
      ctr: l2pCtr,
      nonceHex: l2pNonce.toString('hex'),
      aadAscii: l2pAad.toString('ascii'),
      plaintextJson: l2pPlaintext,
      ciphertextWithTagBase64: l2pCiphertextBase64
    },
    frameP2L: {
      t: "pong",
      d: "p2l",
      ctr: p2lCtr,
      nonceHex: p2lNonce.toString('hex'),
      aadAscii: p2lAad.toString('ascii'),
      plaintextJson: p2lPlaintext,
      ciphertextWithTagBase64: p2lCiphertextBase64
    }
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const vectors = generateTestVectors();
  fs.writeFileSync(VECTORS_FILE, JSON.stringify(vectors, null, 2) + '\n');
  console.log('Public test vectors generated; sha256='+createHash('sha256').update(fs.readFileSync(VECTORS_FILE)).digest('hex'));
}
