package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.rng.SeededRng
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CostAndHoldTest {
    private fun cost(show:Double,supp:Double,i:Double,release:Double=30.0,digest:Double=120.0,strict:Boolean=false,kappa:Double=3.0)=CostFunction.compute(show,supp,i,kappa,strict,release,digest)
    private fun verify(expected:Action,expectedCosts:List<Double>,c:CostBreakdown) {
        Action.entries.forEachIndexed { i,a -> assertEquals(expectedCosts[i],c.cost(a),1e-10) }
        assertEquals(expected,ActionSelector.select(c).action)
    }
    @Test fun allowWinsWhenIncrementalLossIsTiny() {
        // ALLOW=1; DELAY=.9+3*.25*.5+.25=1.525; DIGEST=.9+1.5+.8=3.2; SILENT=.9+1.5+2.5=4.9.
        verify(Action.ALLOW,listOf(1.0,1.525,3.2,4.9),cost(1.0,.9,.5))
    }
    @Test fun silentWinsForLowImportanceAndLargeLoss() {
        // Delay=1+.015+5; digest=1+.06+.8; silent=1+.06+.5.
        verify(Action.SILENT,listOf(20.0,6.015,1.86,1.56),cost(20.0,1.0,.1))
    }
    @Test fun digestWinsForModerateImportanceEvenWithFarDigest() {
        // Delay=1+.135+5; digest=1+.54+.8; silent=1+.54+1.5.
        verify(Action.DIGEST,listOf(20.0,6.135,2.34,3.04),cost(20.0,1.0,.3))
    }
    @Test fun delayWinsWithModerateImportanceAndNearRelease() {
        // Release15min: 3*.6^2*.25=.27. Digest120min: 3*.6^2*2=2.16.
        verify(Action.DELAY,listOf(4.0,1.77,3.46,5.66),cost(4.0,.5,.6,15.0))
    }
    @Test fun silentDigestCrossoverAndTieOrder() {
        for(i in listOf(.16-1e-5,.16,.16+1e-5)) {
            val c=cost(20.0,0.0,i)
            assertEquals(5*i-.8,c.jSilent-c.jDigest,1e-12)
            assertEquals(if(i<.16)Action.SILENT else Action.DIGEST,ActionSelector.select(c).action)
        }
        fun raw(a:Double,b:Double,c:Double,d:Double)=CostBreakdown(0.0,0.0,3.0,0.0,0.0,0.0,0.0,0.0,a,b,c,d)
        assertEquals(Action.ALLOW,ActionSelector.select(raw(1.0,1.0,1.0,1.0)).action)
        assertEquals(Action.DELAY,ActionSelector.select(raw(2.0,1.0,1.0,1.0)).action)
        assertEquals(Action.DIGEST,ActionSelector.select(raw(2.0,2.0,1.0,1.0)).action)
        assertEquals(Action.ALLOW,ActionSelector.select(raw(1.0+5e-10,1.0,2.0,2.0)).action)
        assertEquals(Action.DELAY,ActionSelector.select(raw(1.0+2e-9,1.0,2.0,2.0)).action)
        assertEquals(.5,ActionSelector.select(raw(1.0,1.5,2.0,3.0)).marginMin,0.0)
    }
    @Test fun hoursConversionStrictSessionAndZeroBaseline() {
        val normal=cost(4.0,0.0,1.0,60.0,60.0)
        assertEquals(3.0,normal.releaseDelayHarmMin,0.0);assertEquals(3.0,normal.digestDelayHarmMin,0.0)
        val strict=cost(4.0,0.0,1.0,60.0,60.0,true)
        assertEquals(1.5,strict.releaseDelayHarmMin,0.0);assertEquals(normal.releaseInterruptionMin,strict.releaseInterruptionMin,0.0)
        assertEquals(Action.ALLOW,ActionSelector.select(cost(1.0,1.0,.2)).action)
        assertTrue(cost(1e100,1e99,.5).jDelay.isFinite())
    }
    @Test fun invalidAndOverflowInputsRejectWithFixedCodes() {
        for(invalid in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)) for(index in 0..5) {
            val v=mutableListOf(4.0,1.0,.5,3.0,30.0,120.0);v[index]=invalid
            try { CostFunction.compute(v[0],v[1],v[2],v[3],false,v[4],v[5]);fail() } catch(_:IllegalArgumentException){}
        }
        try { cost(1.0,1.0,1.0,Double.MAX_VALUE,Double.MAX_VALUE,kappa=Double.MAX_VALUE);fail() } catch(_:IllegalArgumentException){}
        for(i in listOf(-.1,1.1))try{cost(1.0,0.0,i);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun fixedSeedSelectionProperties() {
        for(seed in listOf(1L,42L,2026L)) {
            val r=SeededRng(seed)
            repeat(3000) {
                val show=r.nextDouble()*100;val supp=r.nextDouble()*100;val importance=r.nextDouble()
                val c=cost(show,supp,importance,r.nextDouble()*120,r.nextDouble()*120,strict=r.nextDouble()<.5,kappa=.1+r.nextDouble()*10)
                val selected=ActionSelector.select(c)
                assertTrue(c.cost(selected.action)<=Action.entries.minOf { c.cost(it) }+ActionSelector.TIE_TOLERANCE_MIN)
                assertEquals(selected,ActionSelector.select(c))
                // All suppression terms are nonnegative. If Eshow<=Esupp, none can beat ALLOW.
                if(show<=supp && importance>=.16)assertEquals(Action.ALLOW,selected.action)
            }
        }
    }
    private val snapshot=ContextSnapshot(1,FocusState.WORKING,TaskType.CODING,TaskSource.INFERRED,.8,"morning",1.0,"low",0,LaptopState.OFFLINE,PhoneState.SCREEN_OFF,false,null)
    @Test fun holdTableClampsAndUsesSettingsInterval() {
        val e=HoldTimeEstimator();val zone=ZoneId.of("UTC");val now=Instant.parse("2026-01-05T00:00:00Z").toEpochMilli()
        for(focus in FocusState.entries)for(laptop in LaptopState.entries) {
            val expected=if(focus in listOf(FocusState.BREAK,FocusState.RECOVERING)||laptop in listOf(LaptopState.IDLE_OPEN,LaptopState.AWAY))15.0 else if(focus==FocusState.DEEP_FOCUS||laptop==LaptopState.FLOWING)45.0 else 30.0
            assertEquals(expected,e.estimate(snapshot.copy(focusState=focus,laptopState=laptop),PolicySettings(),now,zone).tauReleaseMin,0.0)
        }
        val capped=e.estimate(snapshot.copy(focusState=FocusState.DEEP_FOCUS),PolicySettings(digestIntervalMin=10),now,zone)
        assertEquals(HoldTimes(10.0,10.0),capped)
        assertEquals(HoldTimes(1.0,1.0),e.estimate(snapshot,PolicySettings(),now+119*60000+59000,zone))
        for(a in Action.entries)assertEquals(when(a){Action.ALLOW->null;Action.DELAY->15.0;else->120.0},holdMinutesFor(a,15.0,120.0))
    }
}
