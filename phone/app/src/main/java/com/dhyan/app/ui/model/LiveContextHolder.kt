package com.dhyan.app.ui.model

import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory provider for the most recent [ContextSnapshot].
 *
 * Implements [SnapshotSource] interface so Stage 8 fusion can substitute
 * multi-device fused snapshots seamlessly.
 */
object LiveContextHolder : SnapshotSource {
    private val _snapshot = MutableStateFlow<ContextSnapshot?>(null)
    override val snapshot: StateFlow<ContextSnapshot?> = _snapshot.asStateFlow()

    override fun currentSnapshot(): ContextSnapshot? = _snapshot.value

    fun update(newSnapshot: ContextSnapshot?) {
        _snapshot.value = newSnapshot
    }

    fun reset() {
        _snapshot.value = null
    }
}
