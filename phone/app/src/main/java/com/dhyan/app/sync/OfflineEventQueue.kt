package com.dhyan.app.sync

data class OfflineEvent(
    val type: String,
    val payload: String,
    val timestampMs: Long
)

/**
 * Thread-safe, bounded in-memory FIFO queue for offline events.
 * Max capacity: 5000 events. Oldest events evicted on overflow.
 */
class OfflineEventQueue(private val maxCapacity: Int = 5000) {

    private val queue: ArrayDeque<OfflineEvent> = ArrayDeque()

    @Synchronized
    fun enqueue(event: OfflineEvent) {
        while (queue.size >= maxCapacity) {
            queue.removeFirst() // Evict oldest
        }
        queue.addLast(event)
    }

    @Synchronized
    fun flush(batchSize: Int = 100): List<OfflineEvent> {
        val batch = mutableListOf<OfflineEvent>()
        repeat(minOf(batchSize, queue.size)) {
            batch.add(queue.removeFirst())
        }
        return batch
    }

    @Synchronized
    fun size(): Int = queue.size

    @Synchronized
    fun clear() = queue.clear()
}
