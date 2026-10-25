package com.dhyan.app.twin.bench

import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.model.*

/**
 * JIT compiler warm-up routine.
 *
 * Runs several dummy simulations at app start so that the JVM / ART hotspot compiler
 * optimizes the Monte Carlo inner loop before the first real notification arrives,
 * guaranteeing <10 ms decision latency on the binder thread.
 */
object TwinWarmup {

    fun run(engine: TwinEngine, iterations: Int = 300): Long {
        val dummySnapshot = ContextSnapshot(
            ts = System.currentTimeMillis(),
            focusState = FocusState.WORKING,
            taskType = TaskType.CODING,
            taskSource = TaskSource.INFERRED,
            confidence = 0.8,
            hourBand = "evening",
            minutesInState = 45.0,
            fatigueBand = "low",
            recentInterrupts30m = 1,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = false,
            declaredSession = null
        )

        val dummyNotif = NotificationInput(
            appCat = "social",
            notifCat = "msg",
            isVip = false,
            urgencyScore = 0.2,
            repeatCount5m = 0
        )

        val start = System.nanoTime()
        for (i in 0 until iterations) {
            engine.simulate(dummySnapshot, dummyNotif, seed = i.toLong())
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        return elapsedMs
    }
}
