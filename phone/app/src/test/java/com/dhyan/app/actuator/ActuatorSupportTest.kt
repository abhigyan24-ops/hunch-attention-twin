package com.dhyan.app.actuator

import com.dhyan.app.actuator.release.NoOpReleaseJobScheduler
import com.dhyan.app.actuator.release.ReleaseService
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReleaseServiceTest {

    private fun makeEntity(keyHash: String, state: String, holdUntilMs: Long = 2_000_000L) =
        HeldNotifEntity(
            keyHash = keyHash, mechanism = "CANCEL_DIGEST", state = state,
            appCat = "social", notifCat = "post", senderHash = null,
            postedTsMs = 1_000_000L, heldSinceMs = 1_000_000L,
            holdUntilMs = holdUntilMs, expiresAtMs = 10_000_000L,
            ciphertext = null, iv = null, platformId = 1, pkgHash = "ph",
            decisionAction = "DIGEST", propensity = 0.6
        )

    @Test
    fun `releases due HELD items`() = runBlocking {
        val store = InMemoryHeldStore()
        store.insert(makeEntity("due", "HELD", holdUntilMs = 1_000L))
        store.insert(makeEntity("future", "HELD", holdUntilMs = 5_000_000L))
        val result = ReleaseService.releaseDue(store, nowMs = 2_000_000L)
        assertEquals(1, result.released)
        assertEquals("RELEASED", store.get("due")!!.state)
        assertEquals("HELD", store.get("future")!!.state)
    }

    @Test
    fun `fails open stuck PENDING rows`() = runBlocking {
        val store = InMemoryHeldStore()
        // Stuck pending (heldSinceMs=500, cutoff = nowMs - 60000 = 940000, so 500 < 940000 → stuck)
        store.insert(makeEntity("stuck", "PENDING").copy(heldSinceMs = 500L))
        store.insert(makeEntity("new-pending", "PENDING").copy(keyHash = "new-pending", heldSinceMs = 1_100_000L))
        val result = ReleaseService.releaseDue(store, nowMs = 1_000_000L)
        assertEquals(1, result.failedOpen)
        assertEquals("FAILED_OPEN", store.get("stuck")!!.state)
        assertEquals("PENDING", store.get("new-pending")!!.state)
    }
}

class KillSwitchControllerTest {

    @Test
    fun `activate releases all active items and cancels jobs`() = runBlocking {
        val store = InMemoryHeldStore()
        val scheduler = NoOpReleaseJobScheduler()
        val controller = KillSwitchController(store, scheduler)

        val makeEntity = { kh: String ->
            HeldNotifEntity(
                keyHash = kh, mechanism = "CANCEL_DIGEST", state = "HELD",
                appCat = "social", notifCat = "post", senderHash = null,
                postedTsMs = 1L, heldSinceMs = 1L, holdUntilMs = 9_000_000L,
                expiresAtMs = 99_000_000L, ciphertext = null, iv = null,
                platformId = 1, pkgHash = "ph", decisionAction = "DIGEST", propensity = 0.7
            )
        }
        store.insert(makeEntity("k1"))
        store.insert(makeEntity("k2"))

        val count = controller.activate()
        assertEquals(2, count)
        assertTrue(scheduler.cancelled)
        assertEquals("FAILED_OPEN", store.get("k1")!!.state)
        assertEquals("FAILED_OPEN", store.get("k2")!!.state)
    }
}

class ReleasedKeyRegistryTest {

    @Test
    fun `isRecent returns true within 24h window`() {
        val clock = FakeClock(ms = 0L)
        val registry = ReleasedKeyRegistry(clock)
        registry.register("key1")
        assertTrue(registry.isRecent("key1"))
    }

    @Test
    fun `isRecent returns false after window expires`() {
        val clock = FakeClock(ms = 0L)
        val registry = ReleasedKeyRegistry(clock)
        registry.register("key1")
        clock.advanceMs(ActuatorConfig.LOOP_GUARD_WINDOW_MS + 1_000L)
        assertFalse(registry.isRecent("key1"))
    }

    @Test
    fun `isRecent returns false for unknown key`() {
        val clock = FakeClock()
        val registry = ReleasedKeyRegistry(clock)
        assertFalse(registry.isRecent("unknown"))
    }
}
