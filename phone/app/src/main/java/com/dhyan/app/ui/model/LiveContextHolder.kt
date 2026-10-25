package com.dhyan.app.ui.model

import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory provider for the most recent [ContextSnapshot].
 *
 * In Stage 2/4/5 this is updated by the telemetry and fusion layers.
 * If no snapshot has been published yet, [snapshot] is null, and the UI
 * displays an honest "waiting for telemetry" empty state.
 */
object LiveContextHolder {
    private val _snapshot = MutableStateFlow<ContextSnapshot?>(null)
    val snapshot: StateFlow<ContextSnapshot?> = _snapshot.asStateFlow()

    fun update(newSnapshot: ContextSnapshot?) {
        _snapshot.value = newSnapshot
    }

    fun reset() {
        _snapshot.value = null
    }
}
