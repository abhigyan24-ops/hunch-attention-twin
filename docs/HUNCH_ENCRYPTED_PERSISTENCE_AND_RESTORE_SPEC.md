# Encrypted persistence and restore

Hunch recovery uses AES-256-GCM. The Android recovery file starts with the six ASCII bytes HNCHV1, followed by a 16-byte random salt, a 12-byte random nonce, and ciphertext with a 16-byte authentication tag. PBKDF2-HMAC-SHA256 derives the key in 210,000 iterations. Choose a strong, unique recovery passphrase. A forgotten passphrase cannot be recovered. A short PIN is unsuitable for a file that can be guessed offline.

## Laptop recovery storage

The engine accepts an already encrypted envelope at POST /v1/vault/backup-phone-state and returns that envelope from GET /v1/vault/restore-phone-state. Both use a JSON object with a single Base64 envelope field. They require a private configured TWIN_TOKEN of at least 32 characters in the X-Twin-Token header. Remote requests require HTTPS. Use an independently generated random token. The token authorizes access and does not decrypt the profile.

The engine wraps each snapshot with a second AES-256-GCM key, stored under ~/.hunch or HUNCH_VAULT_DIR. Windows uses current-user DPAPI. Linux and macOS use an owner-only key file. The engine fails closed if its cryptography dependency or local key is unavailable. DELETE /v1/vault/wipe-all removes the wrapping key and snapshot. This does not erase copies outside the managed vault or guarantee physical erasure on SSDs.

The desktop sidecar inherits an explicitly configured TWIN_TOKEN. Its existing localhost listener is not a remote HTTPS service. Automatic phone upload and restore through the existing pairing protocol still require integration.

## Android system backup

The Android backup rules whitelist only files/hunch_vault_backup.enc. The file is encrypted before it reaches storage. System backup and device transfer depend on Android, device policy, and backup availability. A restored blob still requires its original recovery passphrase. If Android restores the encrypted blob, Settings shows **Restore from Android Backup**. Enter the original passphrase to recover the saved bedtime. The same Settings screen accepts a portable file.

## Portable recovery

Use **Export Encrypted Recovery File** or **Restore from Recovery File** in Settings. Android's document picker handles the location. Export also updates the encrypted internal backup file. Import authenticates the complete file before parsing or applying the schedule. Restored bedtime becomes a manual schedule so fresh device history cannot silently replace it. This implementation does not yet populate or apply VIP packages, calibration metrics, or parked tasks. The payload type reserves the requested aggregate fields.

Portable exports and provider backup copies remain under the user's control. Delete those separately if you no longer want to retain them. A complete app factory reset and cross-device wipe are not wired into these controls.
