package com.dhyan.app.twin.params

/**
 * Persistence contract for twin parameters.
 *
 * Implementations must be safe to call from a background dispatcher.
 * [upsert] is idempotent by key: calling it twice with the same record
 * results in the latest record being stored, not a duplicate.
 */
interface ParamPersistence {
    /** Loads all stored parameter records. Called once at startup. */
    suspend fun loadAll(): List<ParamRecord>

    /**
     * Upserts (insert or replace) all records by key.
     * A failure should throw — callers are expected to handle and count failures.
     */
    suspend fun upsert(records: List<ParamRecord>)
}
