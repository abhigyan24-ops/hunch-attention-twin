package com.dhyan.app.twin.episodes

import com.dhyan.app.twin.context.FatigueIndex
import com.dhyan.app.twin.context.HourBand
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import java.util.Calendar
import java.util.TimeZone

/**
 * Adapts event streams (Stage 4A synthetic replay GenEvents, JSONL logs, or Stage 2 collector events)
 * into stateful calls to [EpisodeBuilder].
 *
 * Tracks the current [ContextSnapshot] in real-time based on system and device state transitions.
 * Pure Kotlin — zero Android or UI dependencies.
 */
class EventStreamAdapter(
    val episodeBuilder: EpisodeBuilder,
    private val timeZone: TimeZone = TimeZone.getTimeZone("UTC")
) {
    var currentTaskType: TaskType = TaskType.CODING
        private set
    var currentLaptopState: LaptopState = LaptopState.FLOWING
        private set
    var currentPhoneState: PhoneState = PhoneState.SCREEN_OFF
        private set
    var currentFocusState: FocusState = FocusState.WORKING
        private set

    private var focusStartTimeMs: Long = 0L
    private var sessionHours: Double = 0.0

    /** Builds an instantaneous ContextSnapshot at epoch ms [ts]. */
    fun buildSnapshot(ts: Long): ContextSnapshot {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = ts }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val hourBand = HourBand.from(hour).wire
        val fatigueBand = FatigueIndex.bandFor(sessionHours)
        val minutesInState = if (focusStartTimeMs > 0L) (ts - focusStartTimeMs) / 60_000.0 else 0.0

        return ContextSnapshot(
            ts = ts,
            focusState = currentFocusState,
            taskType = currentTaskType,
            taskSource = TaskSource.INFERRED,
            confidence = 0.85,
            hourBand = hourBand,
            minutesInState = minutesInState,
            fatigueBand = fatigueBand,
            recentInterrupts30m = 0,
            laptopState = currentLaptopState,
            phoneState = currentPhoneState,
            secondScreenOverlap = (currentPhoneState == PhoneState.IN_APP && currentLaptopState == LaptopState.FLOWING),
            declaredSession = null
        )
    }

    /**
     * Ingests a single event from any stream.
     *
     * @param type The event type (e.g., "notif_posted", "app_foreground", "screen_off", "window_change").
     * @param ts Event epoch timestamp in milliseconds.
     * @param payload Map of event payload attributes.
     * @param explicitSnapshot Optional pre-computed snapshot.
     */
    fun processEvent(
        type: String,
        ts: Long,
        payload: Map<String, Any?> = emptyMap(),
        explicitSnapshot: ContextSnapshot? = null
    ) {
        val snapshot = explicitSnapshot ?: buildSnapshot(ts)

        // Always check timeout on every advancing event timestamp
        episodeBuilder.checkTimeout(ts)

        when (type) {
            "notif_posted" -> {
                val key = payload["key"] as? String ?: "notif_$ts"
                val appCat = payload["appCat"] as? String ?: "social"
                val notifCat = payload["notifCat"] as? String
                val senderHash = (payload["senderId"] ?: payload["senderHash"]) as? String
                val isVip = payload["isVip"] as? Boolean ?: false
                val urgencyScore = (payload["urgencyScore"] as? Number)?.toDouble() ?: 0.0
                val repeatCount5m = (payload["repeatCount5m"] as? Number)?.toInt() ?: 0

                episodeBuilder.onNotificationPosted(
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
            "app_foreground", "pickup" -> {
                currentPhoneState = PhoneState.IN_APP
                val appCat = payload["appCat"] as? String ?: "social"
                val surface = payload["surface"] as? String ?: "feed"
                episodeBuilder.onAppForeground(
                    appCat = appCat,
                    surface = surface,
                    ts = ts,
                    snapshot = snapshot
                )
            }
            "app_background" -> {
                val durationMs = (payload["durationMs"] as? Number)?.toLong() ?: 0L
                episodeBuilder.onAppBackground(durationMs, ts)
            }
            "screen_off" -> {
                currentPhoneState = PhoneState.SCREEN_OFF
                episodeBuilder.onScreenOff(ts)
            }
            "screen_on" -> {
                if (currentPhoneState == PhoneState.SCREEN_OFF) {
                    currentPhoneState = PhoneState.IN_APP
                }
            }
            "window_change" -> {
                val appCat = payload["appCat"] as? String ?: "coding"
                val app = payload["app"] as? String ?: ""
                when {
                    appCat == "coding" || appCat == "productivity" || app.contains("code") -> {
                        currentTaskType = TaskType.CODING
                        currentLaptopState = LaptopState.FLOWING
                        currentFocusState = FocusState.DEEP_FOCUS
                    }
                    appCat == "learning" || appCat == "studying" -> {
                        currentTaskType = TaskType.STUDYING
                        currentLaptopState = LaptopState.FLOWING
                        currentFocusState = FocusState.WORKING
                    }
                    appCat == "games" || appCat == "gaming" -> {
                        currentTaskType = TaskType.GAMING
                        currentLaptopState = LaptopState.GAMING
                        currentFocusState = FocusState.BREAK
                    }
                    appCat == "system" || app.contains("away") -> {
                        currentLaptopState = LaptopState.AWAY
                    }
                    else -> {
                        currentLaptopState = LaptopState.BROWSING
                    }
                }
                episodeBuilder.onLaptopWindowChange(appCat, ts)
            }
            "resume_first_edit" -> {
                val delayMs = (payload["delayMs"] as? Number)?.toLong() ?: 0L
                episodeBuilder.onResumeFirstEdit(delayMs, ts)
                currentFocusState = FocusState.WORKING
                focusStartTimeMs = ts
            }
            "idle_start" -> {
                currentLaptopState = LaptopState.IDLE_OPEN
            }
            "idle_end" -> {
                currentLaptopState = LaptopState.FLOWING
            }
        }
    }
}
