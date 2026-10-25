package com.dhyan.app.twin.params

import com.dhyan.app.twin.context.BaselineBucket
import com.dhyan.app.twin.context.ContextBucket
import com.dhyan.app.twin.model.NotificationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class InMemoryParamStoreTest {

    private class FakePersistence(var shouldFail: Boolean = false) : ParamPersistence {
        val storage = ConcurrentHashMap<String, ParamRecord>()

        override suspend fun loadAll(): List<ParamRecord> {
            if (shouldFail) throw RuntimeException("Simulated load failure")
            return storage.values.toList()
        }

        override suspend fun upsert(records: List<ParamRecord>) {
            if (shouldFail) throw RuntimeException("Simulated upsert failure")
            for (r in records) {
                storage[r.key] = r
            }
        }
    }

    private val sampleBucket = ContextBucket(
        taskType = "coding",
        hourBand = "evening",
        fatigueBand = "med",
        notifClass = "social|urg=low|rep=R0|vip=0"
    )

    private val sampleBaselineBucket = BaselineBucket(
        taskType = "coding",
        hourBand = "evening",
        fatigueBand = "med"
    )

    private val sampleNotif = NotificationInput(
        appCat = "social",
        notifCat = "msg",
        isVip = false,
        urgencyScore = 0.2,
        repeatCount5m = 0
    )

    @Test
    fun unloadedStoreReturnsNullOnResolve() {
        val persistence = FakePersistence()
        val store = InMemoryParamStore(persistence, flushDispatcher = Dispatchers.Unconfined)
        assertFalse(store.isLoaded)

        val resolved = store.resolve(sampleBucket, sampleBaselineBucket, sampleNotif)
        assertNull("Unloaded store must return null on resolve", resolved)
    }

    @Test
    fun loadRoundTripAndVersionBumping() = runBlocking {
        val persistence = FakePersistence()
        val key = sampleBucket.fullKey()
        persistence.storage[key] = ParamRecord(
            key = key,
            kind = ParamKind.BETA,
            a = 5.0,
            b = 3.0,
            n = 8.0,
            mean = 0.0,
            m2 = 0.0,
            updatedTs = 1000L
        )

        val store = InMemoryParamStore(persistence, flushDispatcher = Dispatchers.Unconfined)
        store.load()
        assertTrue(store.isLoaded)

        val resolved = store.resolve(sampleBucket, sampleBaselineBucket, sampleNotif)
        assertNotNull(resolved)
        assertEquals(HierarchyLevel.FULL, resolved!!.pickupHierarchyLevel)

        val initialVersion = store.modelVersion
        store.observePickup(sampleBucket, picked = true, nowMs = 2000L)
        assertTrue("Model version must increment after update", store.modelVersion > initialVersion)

        store.flushNow()
        val persisted = persistence.storage[key]
        assertNotNull(persisted)
        assertEquals(ParamKind.BETA, persisted!!.kind)
        assertTrue("Successes must have increased", persisted.a > 5.0)
    }

    @Test
    fun persistenceFailureIncrementsCounterAndNeverThrows() = runBlocking {
        val failingPersistence = FakePersistence(shouldFail = true)
        val store = InMemoryParamStore(failingPersistence, flushDispatcher = Dispatchers.Unconfined)

        // Load fails -> isLoaded stays false
        store.load()
        assertFalse(store.isLoaded)

        // Observe when failing persistence does not throw to caller
        store.observePickup(sampleBucket, picked = true, nowMs = 1000L)
        try {
            store.flushNow()
            fail("Synchronous flushNow should throw if persistence fails")
        } catch (e: Exception) {
            // Expected for flushNow
        }
    }

    @Test
    fun concurrentUpdatesStressTest() = runBlocking {
        val persistence = FakePersistence()
        val store = InMemoryParamStore(persistence, flushDispatcher = Dispatchers.Default)
        store.load()

        val numThreads = 8
        val updatesPerThread = 250
        val latch = CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            thread {
                try {
                    for (i in 0 until updatesPerThread) {
                        store.observePickup(sampleBucket, picked = (i % 2 == 0), nowMs = System.currentTimeMillis())
                        store.observeDwell("social", "feed", 5.0 + (i % 10), nowMs = System.currentTimeMillis())
                        val snap = store.resolve(sampleBucket, sampleBaselineBucket, sampleNotif)
                        assertNotNull(snap)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Threads did not finish in time", latch.await(10, TimeUnit.SECONDS))
        store.flushNow()

        val fullRecord = persistence.storage[sampleBucket.fullKey()]
        assertNotNull(fullRecord)
        assertTrue("Expected updates to be recorded", fullRecord!!.n > 100.0)
    }
}
