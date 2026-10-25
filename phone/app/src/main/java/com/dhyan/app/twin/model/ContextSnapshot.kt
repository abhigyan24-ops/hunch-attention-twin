package com.dhyan.app.twin.model

/**
 * Focus state of the user, per the spec (section 6.4 and context-snapshot.schema.json).
 * Wire strings match the schema exactly.
 */
enum class FocusState(val wire: String) {
    DEEP_FOCUS("deep_focus"),
    WORKING("working"),
    DRIFTING("drifting"),
    DISTRACTED("distracted"),
    RECOVERING("recovering"),
    BREAK("break");
    companion object { fun fromWire(w: String) = entries.first { it.wire == w } }
}

/** Task type, per context-snapshot.schema.json. */
enum class TaskType(val wire: String) {
    CODING("coding"),
    STUDYING("studying"),
    WRITING("writing"),
    GAMING("gaming"),
    BROWSING("browsing"),
    UNKNOWN("unknown");
    companion object { fun fromWire(w: String) = entries.first { it.wire == w } }
}

/** How the task was determined. */
enum class TaskSource(val wire: String) {
    DECLARED("declared"),
    INFERRED("inferred");
    companion object { fun fromWire(w: String) = entries.first { it.wire == w } }
}

/** Laptop activity state, per context-snapshot.schema.json. */
enum class LaptopState(val wire: String) {
    FLOWING("flowing"),
    STUCK("stuck"),
    IDLE_OPEN("idle_open"),
    BROWSING("browsing"),
    GAMING("gaming"),
    AWAY("away"),
    OFFLINE("offline");
    companion object { fun fromWire(w: String) = entries.first { it.wire == w } }
}

/** Phone screen state, per context-snapshot.schema.json. */
enum class PhoneState(val wire: String) {
    SCREEN_OFF("screen_off"),
    IN_APP("in_app"),
    SCROLLING("scrolling");
    companion object { fun fromWire(w: String) = entries.first { it.wire == w } }
}

/** A declared focus session, or null if none is active. */
data class DeclaredSession(
    val task: TaskType,
    val plannedMin: Double,
    val strict: Boolean,
    val startedTs: Long
)

/**
 * A snapshot of the user's current context at a point in time.
 * All fields correspond to the context-snapshot.schema.json v1.0.0 contract.
 *
 * The [ts] field is epoch ms. [confidence] is in [0, 1].
 */
data class ContextSnapshot(
    val ts: Long,
    val focusState: FocusState,
    val taskType: TaskType,
    val taskSource: TaskSource,
    val confidence: Double,
    val hourBand: String,           // "night" | "morning" | "afternoon" | "evening" | "late"
    val minutesInState: Double,
    val fatigueBand: String,        // "low" | "med" | "high"
    val recentInterrupts30m: Int,
    val laptopState: LaptopState,
    val phoneState: PhoneState,
    val secondScreenOverlap: Boolean,
    val declaredSession: DeclaredSession?
)
