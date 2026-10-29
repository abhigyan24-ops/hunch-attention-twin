package com.dhyan.app.ui.model

import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory provider for the most recent [ContextSnapshot].
 *
 * Implements [SnapshotSource] interface. Supports seamless delegation
 * to the Stage 8 multi-device fused snapshot source (ContextFuser).
 */
object LiveContextHolder : SnapshotSource {
    private val localSnapshot = MutableStateFlow<ContextSnapshot?>(null)
    private var fusedSource: SnapshotSource? = null
    private var localSink: ((ContextSnapshot) -> Unit)? = null

    override val snapshot: StateFlow<ContextSnapshot?>
        get() = fusedSource?.snapshot ?: localSnapshot.asStateFlow()

    override fun currentSnapshot(): ContextSnapshot? =
        fusedSource?.currentSnapshot() ?: localSnapshot.value

    fun attachFusedSource(source: SnapshotSource, onLocalSnapshot: ((ContextSnapshot) -> Unit)? = null) {
        fusedSource = source
        localSink = onLocalSnapshot
        localSnapshot.value?.let { localSink?.invoke(it) }
    }

    fun detachFusedSource() {
        fusedSource = null
        localSink = null
    }

    fun update(newSnapshot: ContextSnapshot?) {
        localSnapshot.value = newSnapshot
        newSnapshot?.let { localSink?.invoke(it) }
    }

    fun reset() {
        localSnapshot.value = null
        fusedSource = null
        localSink = null
    }
}
