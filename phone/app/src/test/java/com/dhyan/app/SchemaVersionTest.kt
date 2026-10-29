package com.dhyan.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaVersionTest {
    @Test fun schemaVersionMatchesCanonicalContract() {
        assertEquals("1.1.0", SchemaVersion.VERSION)
    }
}
