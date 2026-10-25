package com.dhyan.app.actuator

import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read-only view of currently held items for remote mirror (laptop panel).
 *
 * PRIVACY CONTRACT:
 * Exposes ONLY opaque heldId (keyHash), appCat, heldAt, expiresAt, and status.
 * Zero notification text, zero titles, zero sender names, zero package names!
 */
data class HeldItemSummary(
    val heldId: String,
    val appCat: String,
    val heldAt: Long,
    val expiresAt: Long,
    val status: ActuationStatus
)

interface HeldItemsView {
    suspend fun currentHeldItems(): List<HeldItemSummary>
    fun observeHeldItems(): Flow<List<HeldItemSummary>>
}

class RoomHeldItemsView(
    private val heldNotifDao: HeldNotifDao
) : HeldItemsView {

    override suspend fun currentHeldItems(): List<HeldItemSummary> {
        return heldNotifDao.allActive().map { it.toSummary() }
    }

    override fun observeHeldItems(): Flow<List<HeldItemSummary>> {
        return heldNotifDao.observeActive().map { list ->
            list.map { it.toSummary() }
        }
    }

    private fun HeldNotifEntity.toSummary() = HeldItemSummary(
        heldId = keyHash,
        appCat = appCat,
        heldAt = heldSinceMs,
        expiresAt = expiresAtMs,
        status = runCatching { ActuationStatus.valueOf(state) }.getOrDefault(ActuationStatus.HELD)
    )
}
