package com.dhyan.app.actuator

import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus

/** Room-backed implementation of [HeldStore]. */
class RoomHeldStore(private val dao: HeldNotifDao) : HeldStore {
    override suspend fun insert(entity: HeldNotifEntity) = dao.insert(entity)
    override suspend fun get(keyHash: String): HeldNotifEntity? = dao.get(keyHash)
    override suspend fun setState(keyHash: String, state: ActuationStatus) =
        dao.setState(keyHash, state.name)
    override suspend fun setStateWithOverride(keyHash: String, state: ActuationStatus) =
        dao.setStateWithOverride(keyHash, state.name)
    override suspend fun dueForRelease(nowMs: Long): List<HeldNotifEntity> =
        dao.dueForRelease(nowMs)
    override suspend fun allActive(): List<HeldNotifEntity> = dao.allActive()
    override suspend fun stuckPending(olderThanMs: Long): List<HeldNotifEntity> =
        dao.stuckPending(olderThanMs)
    override suspend fun deleteExpired(nowMs: Long) = dao.deleteExpired(nowMs)
}
