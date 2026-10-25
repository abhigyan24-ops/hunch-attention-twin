package com.dhyan.app.personas.replay

import com.dhyan.app.personas.*
import com.dhyan.app.personas.world.HistoryGenerator

/**
 * 4A-5: FastForward.
 * Generates a persona history and replays it through ReplayDriver.
 * Does not know about the twin; the sink decides what to do with events.
 * Stage 4B and Stage 6 will pass learning sinks.
 */
class FastForward(
    val persona: PersonaDef,
    val config: GeneratorConfig = GeneratorConfig()
) {
    suspend fun run(
        sink: ReplaySink,
        clock: ReplayClock,
        options: ReplayOptions = ReplayOptions()
    ): ReplayProgress {
        val history = HistoryGenerator.generate(persona, config)
        var lastProgress = ReplayProgress(0.0, 0, 0L, 0L, 0L)
        val trackingSink = object : ReplaySink {
            override fun onEvent(e: com.dhyan.app.personas.world.GenEvent) = sink.onEvent(e)
            override fun onProgress(p: ReplayProgress) { lastProgress = p; sink.onProgress(p) }
        }
        ReplayDriver.run(
            history.events, trackingSink, clock, options,
            startEpochMs = config.startEpochMs,
            daysTotal = config.days
        )
        return lastProgress
    }
}
