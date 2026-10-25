package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted twin parameter record.
 * Maps to [com.dhyan.app.twin.params.ParamRecord].
 * Columns a, b, n, mean, m2 cover Beta, LogNormal, Dirichlet, and Gamma stats.
 */
@Entity(tableName = "params")
data class ParamEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "kind") val kind: String,   // BETA | LOGNORM | DIRICHLET | GAMMA
    @ColumnInfo(name = "a") val a: Double,
    @ColumnInfo(name = "b") val b: Double,
    @ColumnInfo(name = "n") val n: Double,
    @ColumnInfo(name = "mean") val mean: Double,
    @ColumnInfo(name = "m2") val m2: Double,
    @ColumnInfo(name = "updated_ms") val updatedMs: Long
)
