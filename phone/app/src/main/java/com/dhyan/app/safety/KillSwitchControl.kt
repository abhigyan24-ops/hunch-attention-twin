package com.dhyan.app.safety

import com.dhyan.app.twin.decision.KillSource
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

interface KillSwitchListener {
    fun onActivated(source: KillSource)
    fun onDeactivated()
}
interface KillSwitchControl {
    val active: Boolean
    val changedAtMs: Long
    fun activate(source: KillSource, nowMs: Long)
    fun deactivate(nowMs: Long)
    fun restore(active: Boolean, changedAtMs: Long)
    fun addListener(l: KillSwitchListener)
    fun removeListener(l: KillSwitchListener)
}
/** State only; Stage 6 owns persistence, reboot restoration and notification release effects. */
class InMemoryKillSwitch : KillSwitchControl {
    @Volatile private var state = false
    @Volatile private var changed = 0L
    private val listeners = CopyOnWriteArrayList<KillSwitchListener>()
    private val errors = AtomicLong(0)
    val listenerFailureCount: Long get() = errors.get()
    override val active: Boolean get() = state
    override val changedAtMs: Long get() = changed
    @Synchronized override fun activate(source: KillSource, nowMs: Long) {
        require(nowMs >= 0) { "invalid_time" }
        if (state) return
        state=true; changed=nowMs
        for (l in listeners) try { l.onActivated(source) } catch (_: Exception) { errors.incrementAndGet() }
    }
    @Synchronized override fun deactivate(nowMs: Long) {
        require(nowMs >= 0) { "invalid_time" }
        if (!state) return
        state=false; changed=nowMs
        for (l in listeners) try { l.onDeactivated() } catch (_: Exception) { errors.incrementAndGet() }
    }
    @Synchronized override fun restore(active: Boolean, changedAtMs: Long) {
        require(changedAtMs >= 0) { "invalid_time" }
        // Restore persisted state silently; it must not duplicate actuator side effects.
        state=active; changed=changedAtMs
    }
    override fun addListener(l: KillSwitchListener) { listeners.addIfAbsent(l) }
    override fun removeListener(l: KillSwitchListener) { listeners.remove(l) }
}
