package com.dhyan.app.actuator

import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HeldStoreFakeTest {

    private fun makeEntity(keyHash: String = "hash-abc", state: String = "PENDING") = HeldNotifEntity(
        keyHash = keyHash,
        mechanism = "CANCEL_DIGEST",
        state = state,
        appCat = "social",
        notifCat = "promo",
        senderHash = null,
        postedTsMs = 1_000_000L,
        heldSinceMs = 1_000_000L,
        holdUntilMs = 2_000_000L,
        expiresAtMs = 10_000_000L,
        ciphertext = null,
        iv = null,
        platformId = 42,
        pkgHash = "pkg-hash",
        decisionAction = "DIGEST",
        propensity = 0.7
    )

    @Test
    fun `insert and get round-trip`() = runBlocking {
        val store = InMemoryHeldStore()
        val entity = makeEntity("k1")
        store.insert(entity)
        val retrieved = store.get("k1")
        assertNotNull(retrieved)
        assertEquals("k1", retrieved!!.keyHash)
        assertEquals("PENDING", retrieved.state)
    }

    @Test
    fun `setState transitions correctly`() = runBlocking {
        val store = InMemoryHeldStore()
        store.insert(makeEntity("k2", "PENDING"))
        store.setState("k2", ActuationStatus.HELD)
        assertEquals("HELD", store.get("k2")!!.state)
    }

    @Test
    fun `setStateWithOverride sets user_override flag`() = runBlocking {
        val store = InMemoryHeldStore()
        store.insert(makeEntity("k3", "HELD"))
        store.setStateWithOverride("k3", ActuationStatus.RELEASED)
        val entity = store.get("k3")!!
        assertEquals("RELEASED", entity.state)
        assertTrue(entity.userOverride)
    }

    @Test
    fun `dueForRelease returns only past-due HELD items`() = runBlocking {
        val store = InMemoryHeldStore()
        // holdUntilMs = 2_000_000 — due at that time
        store.insert(makeEntity("past-due", "HELD"))    // holdUntilMs=2_000_000
        store.insert(makeEntity("future", "HELD").copy(keyHash = "future", holdUntilMs = 9_999_999L))
        store.insert(makeEntity("pending", "PENDING"))

        val due = store.dueForRelease(nowMs = 3_000_000L)
        assertEquals(1, due.size)
        assertEquals("past-due", due[0].keyHash)
    }

    @Test
    fun `stuckPending returns only old PENDING rows`() = runBlocking {
        val store = InMemoryHeldStore()
        store.insert(makeEntity("old").copy(heldSinceMs = 500_000L))
        store.insert(makeEntity("new").copy(keyHash = "new", heldSinceMs = 5_000_000L))
        val stuck = store.stuckPending(olderThanMs = 1_000_000L)
        assertEquals(1, stuck.size)
        assertEquals("old", stuck[0].keyHash)
    }

    @Test
    fun `deleteExpired removes finished rows past expiry`() = runBlocking {
        val store = InMemoryHeldStore()
        val expired = makeEntity("exp", "RELEASED").copy(expiresAtMs = 100L)
        val alive = makeEntity("alive", "RELEASED").copy(keyHash = "alive", expiresAtMs = 99_999_999L)
        val active = makeEntity("active", "HELD").copy(keyHash = "active", expiresAtMs = 100L) // active—should NOT be deleted
        store.insert(expired)
        store.insert(alive)
        store.insert(active)
        store.deleteExpired(nowMs = 1_000L)
        assertNull(store.get("exp"))
        assertNotNull(store.get("alive"))
        assertNotNull(store.get("active")) // active rows protected
    }
}
