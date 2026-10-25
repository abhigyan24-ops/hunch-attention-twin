package com.dhyan.app.personas

import com.dhyan.app.personas.rng.GenRng
import org.junit.Assert.*
import org.junit.Test

class GenRngTest {
    @Test fun deterministicAndForksIgnoreParentConsumption() {
        val a=GenRng(42); val b=GenRng(42)
        repeat(1000) { assertEquals(a.nextDouble(), b.nextDouble(), 0.0) }
        val parent=GenRng(42); val first=parent.fork(1,2)
        repeat(100) { parent.nextNormal() }
        val second=parent.fork(1,2)
        repeat(100) { assertEquals(first.nextDouble(), second.nextDouble(), 0.0) }
        assertNotEquals(parent.fork(1,2).nextDouble(), parent.fork(1,3).nextDouble())
    }
    @Test fun fixedSeedMomentsAndFrequencies() {
        for(seed in listOf(1L,42L,2026L)) {
            val r=GenRng(seed); var sum=0.0; var squares=0.0; var expSum=0.0
            val counts=IntArray(3)
            repeat(100000) {
                val x=r.nextNormal(); sum+=x; squares+=x*x; expSum+=r.nextExp(2.0)
                counts[r.categorical(listOf(1.0,2.0,7.0))]++
            }
            assertEquals(0.0,sum/100000,0.02); assertEquals(1.0,squares/100000,0.03)
            assertEquals(0.5,expSum/100000,0.01)
            for(i in counts.indices) assertEquals(listOf(.1,.2,.7)[i],counts[i]/100000.0,.01)
        }
    }
    @Test fun poissonMeanAndBoundaryDraws() {
        val r=GenRng(7); var total=0
        repeat(20000) { total+=r.nextPoisson(3.0) }; assertEquals(3.0,total/20000.0,.06)
        val a=GenRng(8); val b=GenRng(8)
        assertFalse(a.bernoulli(0.0)); b.nextDouble(); assertEquals(a.nextDouble(),b.nextDouble(),0.0)
        assertEquals(Double.POSITIVE_INFINITY,r.nextExp(0.0),0.0)
        repeat(100) { assertEquals(1,r.categorical(listOf(0.0,1.0,0.0))) }
    }
}
