# Dhyan Sync Protocol v1.1.0 — Test Vectors & Reference Crypto

This directory contains deterministic test vectors and reference crypto implementation for the Dhyan Sync Protocol (P-256 ECDH, HKDF-SHA256, HMAC-SHA256, AES-256-GCM).

## Files
- `VERSION`: Semver `1.1.0`
- `CHANGELOG.md`: Log of contract changes.
- `schemas/`: JSON schemas for all sync frames (outer, session, and local ingest).
- `fixtures/`: Valid and invalid JSON fixture payloads.
- `ref/crypto.mjs`: Reference Node.js implementation using native `node:crypto`.
- `test-vectors.json`: Golden test vectors generated with known deterministic inputs.

## How to Verify Vectors

### In TypeScript / Node.js
Run the contract test suite:
```bash
npm run check:contracts
```
The test suite loads `test-vectors.json` and asserts:
1. P-256 public keys generated from `privateScalarHex` match `publicKeyHex`.
2. Pairing commit: SHA256(pkL || nL) matches `commitHex` and `commitBase64`.
3. ECDH shared secret computed from both perspectives (L with P's public key, P with L's public key) matches `sharedSecretHex`.
4. Transcript hash `T = SHA256("dhyan-pair-v1" || pkL || pkP || nL || nP)` matches `transcriptHashHex`.
5. SAS 6-digit string matches `sasDigits` (`790 350`).
6. Confirm MACs match `confirmMacLaptopBase64` and `confirmMacPhoneBase64`.
7. Permanent pairing key `PK` and session room ID `sid` match.
8. Hello MACs match for respective sequence numbers.
9. Directional session keys `k_l2p` and `k_p2l` match.
10. Encrypted frame decryption using `k_l2p` and AAD verifies authentication tag and matches `plaintextJson`.

### In Kotlin / Android
In `phone/app/src/test/java/com/dhyan/app/sync/CryptoVectorsTest.kt`:
1. Use `BigInteger(privateScalarHex, 16)` to construct `ECPrivateKeySpec` on NIST P-256 (`secp256r1`).
2. Construct `ECPublicKeySpec` from `publicKeyHex` (uncompressed 65-byte point `0x04 || X || Y`).
3. Compute `KeyAgreement.getInstance("ECDH")` to verify `sharedSecretHex`.
4. Verify HKDF-SHA256 (RFC 5869 extract and expand) matching `PK`, `k_l2p`, `k_p2l`.
5. Verify `Cipher.getInstance("AES/GCM/NoPadding")` with 12-byte IV (`0x00000000 || ctr`) and `updateAAD(aadAscii.toByteArray(StandardCharsets.US_ASCII))` decrypts `ciphertextWithTagBase64`.

Public fixed vectors are TEST ONLY, never production keys or nonces. W3 changes schema metadata only; cryptographic intermediates remain identical. Both platforms must independently pass vectors before merging an actual sync integration. See sync contract section15 for exact declared/adapterHealthy semantics and migration limitations.

