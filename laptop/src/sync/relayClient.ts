export interface RelayClient {connect():Promise<void>;disconnect():Promise<void>;}
// TODO 14/25.9: WSS pairing, schema validation, bounded queue, dedup/backfill,
// clock offsets, explicit stale state and command authorization/idempotency.
