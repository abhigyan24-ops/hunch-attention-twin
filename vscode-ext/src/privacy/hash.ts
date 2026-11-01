import { createHmac, randomBytes } from 'node:crypto';

export interface SecretStorageLike {
  get(key: string): Promise<string | undefined>;
  store(key: string, value: string): Promise<void>;
}

const SALT_KEY = 'dhyan.privacy.salt';

/**
 * Computes a 16-hex-character HMAC-SHA256 hash using the provided salt.
 * Ensures irreversible, privacy-preserving taskKey and intention hashing.
 */
export function hashWithSalt(input: string, salt: string): string {
  if (!input || !salt) {
    return '0000000000000000';
  }
  const hmac = createHmac('sha256', salt);
  hmac.update(input, 'utf8');
  return hmac.digest('hex').slice(0, 16);
}

/**
 * Retrieves the persisted salt from context.secrets or generates and persists a new 32-byte hex salt.
 */
export async function getOrCreateSalt(secrets: SecretStorageLike): Promise<string> {
  const existing = await secrets.get(SALT_KEY);
  if (existing && existing.length >= 32) {
    return existing;
  }
  const newSalt = randomBytes(32).toString('hex');
  await secrets.store(SALT_KEY, newSalt);
  return newSalt;
}
