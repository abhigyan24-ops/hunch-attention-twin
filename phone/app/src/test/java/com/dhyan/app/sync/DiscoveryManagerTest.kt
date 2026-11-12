package com.dhyan.app.sync

import org.junit.Assert.*
import org.junit.Test

class DiscoveryManagerTest {

    @Test
    fun `valid payload parses correctly`() {
        val raw = """{"host":"192.168.1.10","port":8765,"token":"abc123","service":"_hunch._tcp"}"""
        val config = DiscoveryManager.parsePairingPayload(raw)
        assertNotNull(config)
        assertEquals("192.168.1.10", config!!.host)
        assertEquals(8765, config.port)
        assertEquals("abc123", config.token)
        assertEquals("_hunch._tcp", config.service)
    }

    @Test
    fun `missing host returns null`() {
        val raw = """{"port":8765,"token":"abc123"}"""
        assertNull(DiscoveryManager.parsePairingPayload(raw))
    }

    @Test
    fun `invalid port returns null`() {
        val raw = """{"host":"192.168.1.10","port":99999,"token":"abc123"}"""
        assertNull(DiscoveryManager.parsePairingPayload(raw))
    }

    @Test
    fun `missing token returns null`() {
        val raw = """{"host":"192.168.1.10","port":8765}"""
        assertNull(DiscoveryManager.parsePairingPayload(raw))
    }

    @Test
    fun `malformed JSON returns null`() {
        assertNull(DiscoveryManager.parsePairingPayload("not json at all"))
    }

    @Test
    fun `service field defaults to _hunch_tcp when absent`() {
        val raw = """{"host":"10.0.0.1","port":8765,"token":"tok"}"""
        val config = DiscoveryManager.parsePairingPayload(raw)
        assertNotNull(config)
        assertEquals("_hunch._tcp", config!!.service)
    }
}
