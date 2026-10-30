package com.dhyan.app.ui.pet

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.*
import org.junit.Test

class PetParticleSystemTest {
    @Test fun seededParticlesReplayDeterministically() {
        val first = ParticleEmitter(42)
        val second = ParticleEmitter(42)
        ParticleType.entries.forEach { type ->
            first.emit(40f, 60f, type)
            second.emit(40f, 60f, type)
        }
        first.update(.1f)
        second.update(.1f)
        assertEquals(first.particles.toList(), second.particles.toList())
    }

    @Test fun heartsDriftUpAndFadeThenExpire() {
        val emitter = ParticleEmitter()
        emitter.emit(40f, 60f, ParticleType.HEART, 1)
        val initial = emitter.particles.single().copy()
        emitter.update(.1f)
        val updated = emitter.particles.single()
        assertTrue(updated.y < initial.y)
        assertTrue(updated.vy < initial.vy)
        assertTrue(updated.alpha < initial.alpha)
        assertTrue(updated.scale > initial.scale)
        emitter.update(10f)
        assertTrue(emitter.particles.isEmpty())
    }

    @Test fun breathRingsExpandWithoutTranslating() {
        val emitter = ParticleEmitter()
        emitter.emit(40f, 60f, ParticleType.BREATH_RING, 1)
        val initial = emitter.particles.single().copy()
        emitter.update(.25f)
        val updated = emitter.particles.single()
        assertEquals(initial.x, updated.x, 0f)
        assertEquals(initial.y, updated.y, 0f)
        assertTrue(updated.scale > initial.scale)
        assertTrue(updated.alpha < initial.alpha)
    }

    @Test fun invalidArgumentsDoNotAdvanceOrEmit() {
        val emitter = ParticleEmitter()
        emitter.emit(Float.NaN, 0f, ParticleType.SPARKLE)
        emitter.emit(0f, Float.POSITIVE_INFINITY, ParticleType.ZZZ)
        emitter.emit(0f, 0f, ParticleType.ZZZ, -1)
        assertTrue(emitter.particles.isEmpty())
        emitter.emit(40f, 60f, ParticleType.ZZZ)
        val before = emitter.particles.map { it.copy() }
        listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { emitter.update(it) }
        assertEquals(before, emitter.particles.toList())
    }

    @Test fun emissionAndWorkingSetAreBounded() {
        val emitter = ParticleEmitter()
        emitter.emit(0f, 0f, ParticleType.SPARKLE, Int.MAX_VALUE)
        assertEquals(32, emitter.particles.size)
        repeat(100) { emitter.emit(it.toFloat(), 0f, ParticleType.HEART, 32) }
        assertEquals(128, emitter.particles.size)
        emitter.update(10f)
        assertTrue(emitter.particles.isEmpty())
    }

    @Test fun updatesInvalidateSnapshotReadersIncludingCanvas() {
        val emitter = ParticleEmitter()
        emitter.emit(0f, 0f, ParticleType.HEART, 1)
        Snapshot.sendApplyNotifications()
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { invalidations++ }) { emitter.particles.single().alpha }
            emitter.update(.1f)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun corruptParticlesAreRemovedWithoutPoisoningTheEmitter() {
        val emitter = ParticleEmitter()
        emitter.emit(0f, 0f, ParticleType.HEART, 1)
        emitter.particles.single().vx = Float.NaN
        emitter.update(.1f)
        assertTrue(emitter.particles.isEmpty())
        emitter.emit(0f, 0f, ParticleType.SPARKLE, 1)
        emitter.update(.1f)
        assertEquals(1, emitter.particles.size)
    }
}
