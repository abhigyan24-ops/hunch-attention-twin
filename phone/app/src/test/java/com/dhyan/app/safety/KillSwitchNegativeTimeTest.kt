package com.dhyan.app.safety

import com.dhyan.app.twin.decision.KillSource
import org.junit.Assert.*
import org.junit.Test

class KillSwitchNegativeTimeTest {
    @Test fun negativeTransitionTimesClampWithoutThrowing() {
        for(time in listOf(-1L,Long.MIN_VALUE)) {
            val k=InMemoryKillSwitch()
            k.activate(KillSource.USER_SETTINGS,time)
            assertTrue(k.active);assertEquals(0L,k.changedAtMs)
            k.activate(KillSource.SYSTEM,time)
            assertTrue(k.active);assertEquals(0L,k.changedAtMs)
            k.deactivate(time)
            assertFalse(k.active);assertEquals(0L,k.changedAtMs)
            k.deactivate(time)
            assertFalse(k.active);assertEquals(0L,k.changedAtMs)
        }
    }
    @Test fun bootRestoreClampsNegativeTimesWithoutNotification() {
        for(time in listOf(-1L,Long.MIN_VALUE)) for(active in listOf(false,true)) {
            val k=InMemoryKillSwitch();var calls=0
            k.addListener(object:KillSwitchListener {
                override fun onActivated(source:KillSource){calls++}
                override fun onDeactivated(){calls++}
            })
            k.restore(active,time)
            assertEquals(active,k.active);assertEquals(0L,k.changedAtMs);assertEquals(0,calls)
            k.activate(KillSource.SYSTEM,time);assertTrue(k.active)
            k.deactivate(time);assertFalse(k.active)
        }
    }
    @Test fun listenerErrorsAreCountedAndDoNotEscapeTransitions() {
        val k=InMemoryKillSwitch();var calls=0
        k.addListener(object:KillSwitchListener {
            override fun onActivated(source:KillSource){throw AssertionError("synthetic-listener-error")}
            override fun onDeactivated(){throw AssertionError("synthetic-listener-error")}
        })
        k.addListener(object:KillSwitchListener {
            override fun onActivated(source:KillSource){calls++}
            override fun onDeactivated(){calls++}
        })
        k.activate(KillSource.SYSTEM,-1);k.deactivate(-1)
        assertEquals(2,calls);assertEquals(2L,k.listenerFailureCount)
    }
    @Test fun idempotentCallsDoNotRewriteThePreviousTransitionTime() {
        val k=InMemoryKillSwitch()
        k.activate(KillSource.SYSTEM,10);k.activate(KillSource.SYSTEM,-1)
        assertEquals(10L,k.changedAtMs)
        k.deactivate(20);k.deactivate(-1);assertEquals(20L,k.changedAtMs)
        k.restore(true,Long.MAX_VALUE);assertEquals(Long.MAX_VALUE,k.changedAtMs)
    }
}
