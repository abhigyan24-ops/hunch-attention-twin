import crypto from 'crypto';

export const CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

/**
 * Generates a 6-character pairing code from the 32-character alphabet using crypto.randomInt.
 */
export function generatePairingCode() {
  let code = "";
  for (let i = 0; i < 6; i++) {
    const idx = crypto.randomInt(0, CODE_ALPHABET.length);
    code += CODE_ALPHABET[idx];
  }
  return code;
}

/**
 * Derives the 64-hex rendezvous room ID from the pairing code:
 * hex(sha256("dhyan-rendezvous-v1|" + code))
 */
export function getRendezvousRoomId(code) {
  const norm = code.toUpperCase().trim();
  return crypto.createHash('sha256').update(`dhyan-rendezvous-v1|${norm}`).digest('hex');
}

/**
 * Generates an ephemeral P-256 key pair.
 * Returns { privateKey: Buffer(32), publicKey: Buffer(65) }
 */
export function generateP256KeyPair(privateScalar = null) {
  const ecdh = crypto.createECDH('prime256v1');
  if (privateScalar) {
    ecdh.setPrivateKey(privateScalar);
  } else {
    ecdh.generateKeys();
  }
  return {
    privateKey: ecdh.getPrivateKey(),
    publicKey: ecdh.getPublicKey() // 65 bytes uncompressed 0x04 || X || Y
  };
}

/**
 * Computes ECDH shared secret (32-byte X coordinate).
 */
export function computeECDH(privateKey, peerPublicKey) {
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.setPrivateKey(privateKey);
  return ecdh.computeSecret(peerPublicKey);
}

/**
 * HKDF-SHA256 (RFC 5869).
 */
export function hkdfSha256(secret, salt, info, length) {
  const saltBuf = Buffer.isBuffer(salt) ? salt : Buffer.from(salt || '');
  const infoBuf = Buffer.isBuffer(info) ? info : Buffer.from(info || '');
  return Buffer.from(crypto.hkdfSync('sha256', secret, saltBuf, infoBuf, length));
}

/**
 * Laptop commit: SHA256(pkL || nL).
 */
export function computePairCommit(pkL, nL) {
  return crypto.createHash('sha256').update(Buffer.concat([pkL, nL])).digest();
}

/**
 * Transcript hash T = SHA256("dhyan-pair-v1" || pkL || pkP || nL || nP).
 */
export function computeTranscriptHash(pkL, pkP, nL, nP) {
  return crypto.createHash('sha256')
    .update(Buffer.from("dhyan-pair-v1"))
    .update(pkL)
    .update(pkP)
    .update(nL)
    .update(nP)
    .digest();
}

/**
 * Short Authentication String (SAS): uint32_be(T[0..4]) mod 1000000, 6 digits grouped "123 456".
 */
export function computeSas(T) {
  const num = T.readUInt32BE(0) % 1000000;
  const str = num.toString().padStart(6, '0');
  return `${str.slice(0, 3)} ${str.slice(3)}`;
}

/**
 * Derives kConf = HKDF(secret, salt=T, info="dhyan-pair-confirm-v1", 32)
 * and computes confirm MAC = HMAC-SHA256(kConf, "confirm|" + device).
 */
export function computePairConfirmMac(secret, T, device) {
  const kConf = hkdfSha256(secret, T, "dhyan-pair-confirm-v1", 32);
  return crypto.createHmac('sha256', kConf).update(`confirm|${device}`).digest();
}

/**
 * Derives permanent pairing key PK = HKDF(secret, salt=T, info="dhyan-pairing-key-v1", 32)
 * and permanent session room sid = hex(HKDF(PK, salt="", info="dhyan-room-v1", 32)).
 */
export function derivePairingKeyAndRoom(secret, T) {
  const PK = hkdfSha256(secret, T, "dhyan-pairing-key-v1", 32);
  const sidBuf = hkdfSha256(PK, Buffer.alloc(0), "dhyan-room-v1", 32);
  return {
    PK,
    sid: sidBuf.toString('hex')
  };
}

/**
 * Derives kHello = HKDF(PK, salt="", info="dhyan-hello-mac-v1", 32)
 * and computes hello MAC = HMAC-SHA256(kHello, "hello|" + device + "|" + sn + "|" + lastSeqAcked).
 */
export function computeHelloMac(PK, device, snBase64, lastSeqAcked) {
  const kHello = hkdfSha256(PK, Buffer.alloc(0), "dhyan-hello-mac-v1", 32);
  const data = `hello|${device}|${snBase64}|${lastSeqAcked}`;
  return crypto.createHmac('sha256', kHello).update(data).digest();
}

/**
 * Derives session keys:
 * okm = HKDF(PK, salt = snL || snP, info = "dhyan-session-v1", 64)
 * k_l2p = okm[0..32], k_p2l = okm[32..64]
 */
export function deriveSessionKeys(PK, snL, snP) {
  const salt = Buffer.concat([snL, snP]);
  const okm = hkdfSha256(PK, salt, "dhyan-session-v1", 64);
  return {
    k_l2p: Buffer.from(okm.subarray(0, 32)),
    k_p2l: Buffer.from(okm.subarray(32, 64))
  };
}

/**
 * Computes 12-byte GCM nonce: 0x00000000 || ctr (64-bit big endian).
 */
export function buildNonce(ctr) {
  const nonce = Buffer.alloc(12, 0);
  nonce.writeBigUInt64BE(BigInt(ctr), 4);
  return nonce;
}

/**
 * Builds AAD: ASCII "dhyan|1|" + t + "|" + d + "|" + ctr.
 */
export function buildAad(t, d, ctr) {
  return Buffer.from(`dhyan|1|${t}|${d}|${ctr}`, 'ascii');
}

/**
 * Encrypts an encrypted frame payload using AES-256-GCM.
 * Returns base64 string of (ciphertext || 16-byte tag).
 */
export function encryptFramePayload(key, t, d, ctr, plaintext) {
  const nonce = buildNonce(ctr);
  const aad = buildAad(t, d, ctr);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce);
  cipher.setAAD(aad);

  const plainBuf = Buffer.isBuffer(plaintext) ? plaintext : Buffer.from(plaintext, 'utf8');
  const ciphertext = Buffer.concat([cipher.update(plainBuf), cipher.final()]);
  const tag = cipher.getAuthTag();

  return Buffer.concat([ciphertext, tag]).toString('base64');
}

/**
 * Decrypts an encrypted frame payload using AES-256-GCM.
 * Returns plaintext Buffer. Throws if tag or AAD fails.
 */
export function decryptFramePayload(key, t, d, ctr, cBase64) {
  const fullBuf = Buffer.from(cBase64, 'base64');
  if (fullBuf.length < 16) {
    throw new Error("Ciphertext too short (missing 16-byte GCM tag)");
  }

  const ciphertext = fullBuf.subarray(0, fullBuf.length - 16);
  const tag = fullBuf.subarray(fullBuf.length - 16);

  const nonce = buildNonce(ctr);
  const aad = buildAad(t, d, ctr);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, nonce);
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);

  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}
