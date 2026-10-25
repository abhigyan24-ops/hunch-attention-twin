package com.dhyan.app.twin.context

import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thread-safe holder for the current [ContextSnapshot].
 *
 * The simulation reads [snapshotFlow].value without suspending.
 * The fusion layer calls [update] to push new snapshots whenever context changes.
 *
 * Pure JVM — no android.* or androidx.* dependencies.
 */
class SnapshotHolder {
    private val _snapshotFlow = MutableStateFlow<ContextSnapshot?>(null)

    /** Read-only view of the current snapshot. Null until the first [update] is called. */
    val snapshotFlow: StateFlow<ContextSnapshot?> = _snapshotFlow

    /** The current snapshot value, or null if not yet set. Thread-safe O(1) read. */
    val current: ContextSnapshot? get() = _snapshotFlow.value

    /** Updates the current snapshot. Thread-safe. */
    fun update(snapshot: ContextSnapshot) {
        _snapshotFlow.value = snapshot
    }
}
