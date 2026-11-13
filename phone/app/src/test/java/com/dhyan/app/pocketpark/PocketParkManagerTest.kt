package com.dhyan.app.pocketpark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PocketParkManagerTest {

    private lateinit var manager: PocketParkManager

    @Before
    fun setUp() {
        manager = PocketParkManager(context = null)
    }

    @Test
    fun `parkThought captures thought and prepends to active list`() {
        val t1 = manager.parkThought("Look up WebRTC TURN server docs later", nowMillis = 1000L)
        val t2 = manager.parkThought("Fix memory leak in notification collector", nowMillis = 2000L)

        val list = manager.getParkedThoughts()
        assertEquals(2, list.size)
        // Most recent first
        assertEquals("Fix memory leak in notification collector", list[0].text)
        assertEquals("Look up WebRTC TURN server docs later", list[1].text)
        assertFalse(list[0].isCheckedOff)
        assertFalse(list[1].isCheckedOff)
    }

    @Test
    fun `checkOffThought toggles completion status`() {
        val thought = manager.parkThought("Read research paper on attention twin", nowMillis = 1000L)
        assertFalse(thought.isCheckedOff)

        assertTrue(manager.checkOffThought(thought.id))
        val updated = manager.getParkedThoughts().first { it.id == thought.id }
        assertTrue(updated.isCheckedOff)

        // Active thoughts filter out completed thoughts
        assertEquals(0, manager.getActiveThoughts().size)
    }

    @Test
    fun `dismissThought removes thought cleanly`() {
        val t1 = manager.parkThought("Buy coffee beans", nowMillis = 1000L)
        val t2 = manager.parkThought("Reply to Alex on GitHub", nowMillis = 2000L)

        assertTrue(manager.dismissThought(t1.id))
        assertFalse(manager.dismissThought("non-existent-id"))

        val list = manager.getParkedThoughts()
        assertEquals(1, list.size)
        assertEquals(t2.id, list[0].id)
    }

    @Test
    fun `enforces FIFO cap of 100 items`() {
        for (i in 1..110) {
            manager.parkThought("Thought #$i", nowMillis = i * 1000L)
        }

        val all = manager.getParkedThoughts()
        assertEquals(100, all.size)
        // Top is thought #110
        assertEquals("Thought #110", all.first().text)
        // Oldest retained is thought #11
        assertEquals("Thought #11", all.last().text)
    }
}
