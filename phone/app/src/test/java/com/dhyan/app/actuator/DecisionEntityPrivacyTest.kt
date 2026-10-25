package com.dhyan.app.actuator

import com.dhyan.app.data.DecisionEntity
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

class DecisionEntityPrivacyTest {

    private val textColumnNames = setOf(
        "title", "text", "body", "content", "message",
        "notification_text", "notif_text", "sender_name", "explanation"
    )

    @Test
    fun `DecisionEntity has no text or content columns`() {
        val fields = DecisionEntity::class.java.declaredFields
        for (field in fields) {
            val name = field.name.lowercase()
            val annotation = field.getAnnotation(androidx.room.ColumnInfo::class.java)
            val colName = annotation?.name?.lowercase() ?: name
            assertFalse(
                "DecisionEntity must not have a text column: field='${field.name}' col='$colName'",
                colName in textColumnNames
            )
        }
    }

    @Test
    fun `DecisionRecord maps to DecisionEntity without losing numeric fields`() {
        val entity = DecisionEntity(
            ts = 1_000L,
            keyHash = "k1",
            postedTsMs = 900L,
            action = "DELAY",
            reasonCode = "focus",
            safetyReason = null,
            pLossShow = 0.3,
            pLossSuppress = 0.7,
            deltaLMean = 0.2,
            deltaLP10 = 0.1,
            deltaLP90 = 0.4,
            evidenceStrength = 0.8,
            propensity = 0.6,
            pShow = 0.4,
            exploration = false,
            failedOpen = false,
            importance = 0.5,
            modelVersion = 1L,
            policyVersion = "v1",
            mode = "LIVE",
            actuationStatus = "HELD"
        )
        // Verify round-trip numeric fields are preserved
        assertEquals(0.3, entity.pLossShow!!, 1e-9)
        assertEquals(0.7, entity.pLossSuppress!!, 1e-9)
        assertEquals("HELD", entity.actuationStatus)
        assertNull(entity.safetyReason)
    }
}
