package com.dhyan.app.share

import org.junit.Assert.*
import org.junit.Test

class ShareToDeskManagerTest {
    private fun parse(text: String?) = ShareToDeskManager.parseIncomingShare(text, "Subject", 42)

    @Test fun classifiesTextAndExtractsFirstValidUrl() {
        assertNull(parse(null))
        assertNull(parse(" \n"))
        assertEquals("note", parse("Remember this idea")?.itemType)
        assertEquals("code_snippet", parse("const x = () => 1")?.itemType)
        assertEquals("code_snippet", parse("IllegalStateException\n  at Example.run(Example.kt:1)")?.itemType)
        assertEquals("url", parse("https://example.org")?.itemType)
        val text = "Read https://example.org/issue, then https://example.com"
        val item = requireNotNull(parse(text))
        assertEquals(text, item.rawText)
        assertEquals("https://example.org/issue", item.extractedUrl)
        assertEquals("Subject", item.title)
        assertEquals(42L, item.createdAtMillis)
        assertEquals(item, parse(text))
    }

    @Test fun queueReloadPreservesOrderAndPrunesOldestItems() {
        var persisted = emptyList<SharedDeskItem>()
        fun open() = DeskQueue({ persisted }, { persisted = it.toList(); true })
        val queue = open()
        repeat(202) { index -> assertTrue(queue.append(requireNotNull(parse("note $index")))) }
        assertEquals(200, persisted.size)
        val reopened = open()
        val oldest = requireNotNull(reopened.first())
        assertEquals("note 2", oldest.rawText)
        assertTrue(reopened.acknowledge(oldest.id))
        assertEquals("note 3", reopened.first()?.rawText)
    }
}
