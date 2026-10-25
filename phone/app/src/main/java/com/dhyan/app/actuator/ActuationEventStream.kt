package com.dhyan.app.actuator

import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event representing an actuation state change.
 *
 * Stage 8 (phone sync) subscribes to this stream to mirror pet events
 * (held, released, etc.) to the laptop companion.
 */
data class ActuationEvent(
    val keyHash: String,
    val status: ActuationStatus,
    val appCat: String,
    val timestampMs: Long
)

interface ActuationEventBus {
    val events: SharedFlow<ActuationEvent>
    suspend fun emit(event: ActuationEvent)
}

object SharedActuationEventBus : ActuationEventBus {
    private val _events = MutableSharedFlow<ActuationEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ActuationEvent> = _events.asSharedFlow()

    override suspend fun emit(event: ActuationEvent) {
        _events.emit(event)
    }
}
