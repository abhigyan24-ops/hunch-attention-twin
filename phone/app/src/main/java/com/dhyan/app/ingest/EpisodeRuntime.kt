package com.dhyan.app.ingest

import com.dhyan.app.twin.episodes.EpisodeBuilder
import com.dhyan.app.twin.episodes.EpisodeCloser
import com.dhyan.app.twin.episodes.EventStreamAdapter
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.ui.model.LiveContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlinx.coroutines.Job

/**
 * Production runtime managing live episode attribution, cross-device outcome measurement,
 * and Bayesian parameter persistence (Spec 7.1, 7.2, 25.4, 25.13).
 *
 * Runs off the main thread (Dispatchers.Default), consuming phone-local events
 * and inbound laptop events, and updates [InMemoryParamStore] which persists to Room.
 */
class EpisodeRuntime(
    val store: InMemoryParamStore,
    val windowMin: Double = 30.0,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    enablePeriodicChecker: Boolean = true
) {
    companion object {
        @Volatile
        var instance: EpisodeRuntime? = null
            internal set
    }

    private var checkerJob: Job? = null

    val closer = EpisodeCloser(store)
    val builder = EpisodeBuilder(
        windowMin = windowMin,
        onEpisodeClosed = { ep ->
            closer.onEpisodeClosed(ep)
            ep.outcome?.let { outcome ->
                if (outcome.isResumeEstimated) {
                    try {
                        com.dhyan.app.DhyanApplication.syncManager.contextFuser.isLastResumeEstimated = true
                    } catch (_: Throwable) {}
                } else if (!outcome.isCensored && outcome.pickedUp) {
                    try {
                        com.dhyan.app.DhyanApplication.syncManager.contextFuser.isLastResumeEstimated = false
                    } catch (_: Throwable) {}
                }
            }
        }
    )
    val eventStreamAdapter = EventStreamAdapter(builder)

    init {
        instance = this
        if (enablePeriodicChecker) {
            startPeriodicTimeoutChecker()
        }
    }

    private fun startPeriodicTimeoutChecker() {
        checkerJob = scope.launch {
            while (isActive) {
                delay(15_000L) // check every 15s
                val now = ClockOffset.adjustedNow()
                builder.checkTimeout(now)
            }
        }
    }

    fun stop() {
        checkerJob?.cancel()
        checkerJob = null
    }

    fun onNotificationPosted(
        key: String,
        appCat: String,
        notifCat: String,
        senderHash: String?,
        isVip: Boolean,
        urgencyScore: Double,
        ts: Long = ClockOffset.adjustedNow(),
        explicitSnapshot: ContextSnapshot? = null,
        repeatCount5m: Int = 0
    ) {
        scope.launch {
            val snapshot = explicitSnapshot ?: LiveContextHolder.currentSnapshot() ?: eventStreamAdapter.buildSnapshot(ts)
            builder.onNotificationPosted(
                key = key,
                appCat = appCat,
                notifCat = notifCat,
                senderHash = senderHash,
                isVip = isVip,
                urgencyScore = urgencyScore,
                ts = ts,
                snapshot = snapshot,
                repeatCount5m = repeatCount5m
            )
        }
    }

    fun onAppForeground(
        appCat: String,
        surface: String,
        ts: Long = ClockOffset.adjustedNow(),
        explicitSnapshot: ContextSnapshot? = null
    ) {
        scope.launch {
            val snapshot = explicitSnapshot ?: LiveContextHolder.currentSnapshot() ?: eventStreamAdapter.buildSnapshot(ts)
            builder.onAppForeground(appCat, surface, ts, snapshot)
        }
    }

    fun onAppBackground(
        durationMs: Long,
        ts: Long = ClockOffset.adjustedNow()
    ) {
        scope.launch {
            builder.onAppBackground(durationMs, ts)
        }
    }

    fun onScreenOff(ts: Long = ClockOffset.adjustedNow()) {
        scope.launch {
            builder.onScreenOff(ts)
        }
    }

    fun onScreenOn(ts: Long = ClockOffset.adjustedNow()) {
        // Keeps state machine aligned
    }

    fun onPickup(ts: Long = ClockOffset.adjustedNow(), explicitSnapshot: ContextSnapshot? = null) {
        scope.launch {
            val snapshot = explicitSnapshot ?: LiveContextHolder.currentSnapshot() ?: eventStreamAdapter.buildSnapshot(ts)
            builder.onAppForeground("general", "system", ts, snapshot)
        }
    }

    fun onLocalCollectorGap(reason: String, ts: Long = ClockOffset.adjustedNow()) {
        scope.launch {
            builder.onServiceGap(reason, ts)
        }
    }

    fun checkTimeout(nowTs: Long = ClockOffset.adjustedNow()) {
        scope.launch {
            builder.checkTimeout(nowTs)
        }
    }
}
