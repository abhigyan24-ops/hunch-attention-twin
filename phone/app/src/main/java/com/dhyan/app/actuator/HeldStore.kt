package com.dhyan.app.actuator

import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Abstraction over the held-notification store.
 * The production impl is Room-backed; the test impl is in-memory.
 */
interface HeldStore {
    suspend fun insert(entity: HeldNotifEntity)
    suspend fun get(keyHash: String): HeldNotifEntity?
    suspend fun setState(keyHash: String, state: ActuationStatus)
    suspend fun setStateWithOverride(keyHash: String, state: ActuationStatus)
    suspend fun dueForRelease(nowMs: Long): List<HeldNotifEntity>
    suspend fun allActive(): List<HeldNotifEntity>
    suspend fun stuckPending(olderThanMs: Long): List<HeldNotifEntity>
    suspend fun deleteExpired(nowMs: Long)
}

/** In-memory fake for JVM unit tests. Thread-safety via coroutine Mutex. */
class InMemoryHeldStore : HeldStore {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, HeldNotifEntity>()

    override suspend fun insert(entity: HeldNotifEntity) = mutex.withLock { map[entity.keyHash] = entity }
    override suspend fun get(keyHash: String): HeldNotifEntity? = mutex.withLock { map[keyHash] }
    override suspend fun setState(keyHash: String, state: ActuationStatus) = mutex.withLock {
        map[keyHash]?.let { map[keyHash] = it.copy(state = state.name) }
        Unit
    }
    override suspend fun setStateWithOverride(keyHash: String, state: ActuationStatus) = mutex.withLock {
        map[keyHash]?.let { map[keyHash] = it.copy(state = state.name, userOverride = true) }
        Unit
    }
    override suspend fun dueForRelease(nowMs: Long): List<HeldNotifEntity> = mutex.withLock {
        map.values.filter { it.state in listOf("HELD", "SNOOZED") && it.holdUntilMs > 0 && it.holdUntilMs <= nowMs }
    }
    override suspend fun allActive(): List<HeldNotifEntity> = mutex.withLock {
        map.values.filter { it.state in listOf("HELD", "SNOOZED") }
    }
    override suspend fun stuckPending(olderThanMs: Long): List<HeldNotifEntity> = mutex.withLock {
        map.values.filter { it.state == "PENDING" && it.heldSinceMs < olderThanMs }
    }
    override suspend fun deleteExpired(nowMs: Long) = mutex.withLock {
        val expired = map.values.filter { it.expiresAtMs < nowMs && it.state !in listOf("PENDING", "HELD", "SNOOZED") }
        expired.forEach { map.remove(it.keyHash) }
        Unit
    }

    fun snapshotAll(): List<HeldNotifEntity> = map.values.toList()
}
