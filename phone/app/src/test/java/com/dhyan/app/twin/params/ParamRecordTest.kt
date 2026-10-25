package com.dhyan.app.twin.params

import org.junit.Assert.*
import org.junit.Test

class ParamRecordTest {

    @Test
    fun paramKindFromWireRoundTrip() {
        for (kind in ParamKind.entries) {
            val from = ParamKind.fromWire(kind.wire)
            assertEquals("ParamKind wire round trip failed for $kind", kind, from)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun paramKindThrowsOnUnknownWire() {
        ParamKind.fromWire("unsupported_kind")
    }

    @Test
    fun paramRecordDataFieldsRetained() {
        val record = ParamRecord(
            key = "beta_pickup|nc=social",
            kind = ParamKind.BETA,
            a = 10.5,
            b = 4.2,
            n = 14.7,
            mean = 0.0,
            m2 = 0.0,
            updatedTs = 1730000000000L
        )

        assertEquals("beta_pickup|nc=social", record.key)
        assertEquals(ParamKind.BETA, record.kind)
        assertEquals(10.5, record.a, 0.0)
        assertEquals(4.2, record.b, 0.0)
        assertEquals(14.7, record.n, 0.0)
        assertEquals(1730000000000L, record.updatedTs)
    }
}
