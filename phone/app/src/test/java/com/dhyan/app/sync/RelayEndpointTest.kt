package com.dhyan.app.sync

import org.junit.Assert.*
import org.junit.Test

class RelayEndpointTest {
    @Test fun releaseRequiresSecureEndpoint() {
        assertTrue(RelayEndpoint.isAllowed("wss://relay.example/ws", false))
        assertFalse(RelayEndpoint.isAllowed("ws://127.0.0.1/ws", false))
        assertFalse(RelayEndpoint.isAllowed("https://relay.example/ws", false))
    }
    @Test fun debugOnlyAllowsCleartextLoopbackAndEmulator() {
        assertTrue(RelayEndpoint.isAllowed("ws://127.0.0.1:8080/ws", true))
        assertTrue(RelayEndpoint.isAllowed("ws://10.0.2.2:8080/ws", true))
        assertFalse(RelayEndpoint.isAllowed("ws://192.168.1.10/ws", true))
    }
    @Test fun rejectCredentialsQueriesFragmentsAndWrongPath() {
        for (url in listOf("wss://user:password@relay.example/ws", "wss://relay.example/ws?room=injected",
            "wss://relay.example/ws#fragment", "wss://relay.example/other", "invalid")) {
            assertFalse(RelayEndpoint.isAllowed(url, true))
        }
    }
}
