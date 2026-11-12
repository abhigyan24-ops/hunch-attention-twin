package com.dhyan.app.sync

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OfflineEventQueueTest {

    private lateinit var queue: OfflineEventQueue

    @Before
    fun setup() {
        queue = OfflineEventQueue(maxCapacity = 10)
    }

    @Test
    fun `enqueue and flush single event`() {
        val event = OfflineEvent("focus_start", "{}", 1000L)
        queue.enqueue(event)
        assertEquals(1, queue.size())
        val batch = queue.flush(10)
        assertEquals(1, batch.size)
        assertEquals(event, batch[0])
        assertEquals(0, queue.size())
    }

    @Test
    fun `flush respects batch size`() {
        repeat(10) { queue.enqueue(OfflineEvent("type", "{}", it.toLong())) }
        val batch = queue.flush(3)
        assertEquals(3, batch.size)
        assertEquals(7, queue.size())
    }

    @Test
    fun `evicts oldest on overflow`() {
        repeat(10) { queue.enqueue(OfflineEvent("type", "$it", it.toLong())) }
        // Adding one more should evict the oldest (payload "0")
        queue.enqueue(OfflineEvent("type", "10", 10L))
        assertEquals(10, queue.size())
        val all = queue.flush(10)
        assertEquals("1", all[0].payload) // oldest "0" was evicted
        assertEquals("10", all[9].payload)
    }

    @Test
    fun `flush returns FIFO order`() {
        listOf("a", "b", "c").forEachIndexed { i, v ->
            queue.enqueue(OfflineEvent("type", v, i.toLong()))
        }
        val batch = queue.flush(3)
        assertEquals(listOf("a", "b", "c"), batch.map { it.payload })
    }

    @Test
    fun `clear empties the queue`() {
        queue.enqueue(OfflineEvent("x", "{}", 0L))
        queue.clear()
        assertEquals(0, queue.size())
    }

    @Test
    fun `flush empty queue returns empty list`() {
        val batch = queue.flush()
        assertTrue(batch.isEmpty())
    }
}
