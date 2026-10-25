package com.dhyan.app.safety

import com.dhyan.app.twin.decision.KillSource
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class KillSwitchContractTest {
    @Test fun idempotenceRestoreAndListenerIsolation() {
        val k=InMemoryKillSwitch();val calls=AtomicInteger()
        val bad=object:KillSwitchListener{override fun onActivated(source:KillSource){throw IllegalStateException("synthetic-canary")};override fun onDeactivated(){throw IllegalStateException()}}
        val good=object:KillSwitchListener{override fun onActivated(source:KillSource){calls.incrementAndGet()};override fun onDeactivated(){calls.incrementAndGet()}}
        k.addListener(bad);k.addListener(good);k.addListener(good)
        k.activate(KillSource.USER_SETTINGS,10);k.activate(KillSource.SYSTEM,20)
        assertTrue(k.active);assertEquals(10L,k.changedAtMs);assertEquals(1,calls.get());assertEquals(1L,k.listenerFailureCount)
        k.deactivate(30);k.deactivate(40);assertEquals(2,calls.get());assertEquals(2L,k.listenerFailureCount)
        k.restore(true,50);assertTrue(k.active);assertEquals(50L,k.changedAtMs);assertEquals(2,calls.get())
        k.removeListener(good);k.deactivate(60);assertEquals(2,calls.get())
    }
    @Test fun concurrentActivationNotifiesOnlyOnce() {
        val k=InMemoryKillSwitch();val count=AtomicInteger()
        k.addListener(object:KillSwitchListener{override fun onActivated(source:KillSource){count.incrementAndGet()};override fun onDeactivated(){}})
        val threads=(1..20).map{Thread{k.activate(KillSource.SYSTEM,it.toLong())}.apply{start()}}
        threads.forEach{it.join()};assertTrue(k.active);assertEquals(1,count.get())
    }
}
