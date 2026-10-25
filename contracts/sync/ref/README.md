# Sync reference crypto (partial C0.2)

These Node built-in crypto helpers implement the byte formulas in docs/contracts/SYNC_CONTRACT.md. They are reference primitives, not a complete pairing/session implementation. Protocol validation, replay protection, lifecycle, storage and size limits must be enforced by callers before applying decrypted data.

Run `node --test tools/test/contract-crypto.test.mjs` and `node tools/gen-vectors.mjs` from the repository root. test-vectors.json contains PUBLIC TEST ONLY fixed scalars and nonces, never production secrets. Do not use these fixed inputs for real pairing. The generator prints only the fixture hash. Tests include RFC 5869 case 1, independent P-256 points, both encryption directions and authentication failures.

Full C0 schemas, mocks and integration checks remain pending the CCRs in NOTES_contracts.md. No platform, cloud or hardware verification is implied.
