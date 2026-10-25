package com.dhyan.app.actuator

/** Injected time source — never call System.currentTimeMillis() directly in actuator code. */
interface Clock {
    fun nowMs(): Long
}

/** Production implementation using wall clock. */
object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/** Test/fake implementation with manual control. */
class FakeClock(private var ms: Long = 1_700_000_000_000L) : Clock {
    override fun nowMs(): Long = ms
    fun advanceMs(delta: Long) { ms += delta }
    fun set(newMs: Long) { ms = newMs }
}
