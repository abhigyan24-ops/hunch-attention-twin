package com.dhyan.app.data

import com.dhyan.app.twin.params.ParamKind
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord

/**
 * Adapts [ParamDao] to the [ParamPersistence] interface consumed by [InMemoryParamStore].
 *
 * Converts between Room's [ParamEntity] (flat columns) and the twin's [ParamRecord].
 */
class RoomParamPersistence(private val dao: ParamDao) : ParamPersistence {

    override suspend fun loadAll(): List<ParamRecord> =
        dao.loadAll().map { e ->
            ParamRecord(
                key       = e.key,
                kind      = ParamKind.valueOf(e.kind),  // e.kind stores enum name (BETA, LOGNORM, ...)
                a         = e.a,
                b         = e.b,
                n         = e.n,
                mean      = e.mean,
                m2        = e.m2,
                updatedTs = e.updatedMs
            )
        }

    override suspend fun upsert(records: List<ParamRecord>) {
        val entities = records.map { r ->
            ParamEntity(
                key       = r.key,
                kind      = r.kind.name,   // enum name (BETA, LOGNORM, DIRICHLET, GAMMA)
                a         = r.a,
                b         = r.b,
                n         = r.n,
                mean      = r.mean,
                m2        = r.m2,
                updatedMs = r.updatedTs
            )
        }
        dao.upsert(entities)
    }
}
