package com.dhyan.app.ui.model

import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction for providing the current [ContextSnapshot].
 *
 * In Stage 6 this is implemented by [LiveContextHolder].
 * In Stage 8 the fused multi-device snapshot source will implement this interface.
 */
interface SnapshotSource {
    val snapshot: StateFlow<ContextSnapshot?>
    fun currentSnapshot(): ContextSnapshot?
}
