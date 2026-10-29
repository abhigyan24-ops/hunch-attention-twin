package com.dhyan.app.sync.data

import com.dhyan.app.personas.json.JsonReader
import org.junit.Assert.*
import org.junit.Test

class WireSchemasTest {
    @Test fun allCanonicalEventTypesAndInvalidMutations() {
        val types = listOf("notif_posted", "notif_removed", "app_foreground", "app_background", "screen_on", "screen_off", "unlock", "pickup", "scroll_burst", "surface_change", "window_change", "idle_start", "idle_end", "edit_burst", "resume_first_edit", "tab_category", "declared_session", "feedback", "gap", "state_snapshot")
        for (type in types) {
            val value = CanonicalTestEvents.event(1, type)
            assertTrue(type, WireSchemas.current.event(value))
            assertFalse(WireSchemas.current.event(value + ("unexpected" to true)))
            assertFalse(WireSchemas.current.event(value + ("schemaVersion" to "1.0.0")))
        }
        val event = CanonicalTestEvents.event(2)
        assertFalse(WireSchemas.current.event(event + ("seq" to 1.5)))
        assertFalse(WireSchemas.current.event(event + ("payload" to mapOf("title" to "CANARY_SYNTHETIC"))))
    }

    @Test fun commandUnionRejectsMalformedArguments() {
        val base = mapOf("cmdId" to CanonicalTestEvents.uuid(1), "cmd" to "kill_switch", "args" to mapOf("on" to true), "issuedTs" to 0, "exp" to 30_000)
        assertTrue(WireSchemas.current.valid("command.schema.json", base))
        assertFalse(WireSchemas.current.valid("command.schema.json", base + ("args" to emptyMap<String, Any?>())))
        assertFalse(WireSchemas.current.valid("command.schema.json", base + ("args" to mapOf("on" to true, "extra" to true))))
        assertFalse(WireSchemas.current.valid("command.schema.json", base + ("cmdId" to "not-a-uuid")))
    }
}
