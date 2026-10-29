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
 * Adapts event streams (Stage 4A synthetic replay GenEvents, JSONL logs, Stage 2 collector events, or Stage 8/9 cross-device sync)
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
     * @param type The event type (e.g., "notif_posted", "app_foreground", "screen_off", "window_change", "tab_category", "edit_burst").
     * @param ts Event epoch timestamp in milliseconds (with tsAdj applied if from a peer device).
     * @param payload Map of event payload attributes.
     * @param explicitSnapshot Optional pre-computed snapshot.
     */
    fun processEvent(
        type: String,
        ts: Long,
        payload: Map<String, Any?> = emptyMap(),
        explicitSnapshot: ContextSnapshot? = null
    ) {
        val effectiveTs = (payload["tsAdj"] as? Number)?.toLong() ?: ts
        val snapshot = explicitSnapshot ?: buildSnapshot(effectiveTs)

        // Always check timeout on every advancing event timestamp
        episodeBuilder.checkTimeout(effectiveTs)

        when (type) {
            "notif_posted" -> {
                val key = payload["key"] as? String ?: "notif_$effectiveTs"
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
                    ts = effectiveTs,
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
                    ts = effectiveTs,
                    snapshot = snapshot
                )
            }
            "app_background" -> {
                val durationMs = (payload["durationMs"] as? Number)?.toLong() ?: 0L
                episodeBuilder.onAppBackground(durationMs, effectiveTs)
            }
            "screen_off" -> {
                currentPhoneState = PhoneState.SCREEN_OFF
                episodeBuilder.onScreenOff(effectiveTs)
            }
            "screen_on" -> {
                if (currentPhoneState == PhoneState.SCREEN_OFF) {
                    currentPhoneState = PhoneState.IN_APP
                }
            }
            "window_change" -> {
                val appCat = payload["appCat"] as? String ?: return
                val app = payload["app"] as? String ?: ""
                val isWork = appCat in EpisodeBuilder.WORK_CATEGORIES || app.contains("code", ignoreCase = true)
                when {
                    isWork -> {
                        currentTaskType = if (appCat == "learning") TaskType.STUDYING else TaskType.CODING
                        currentLaptopState = LaptopState.FLOWING
                        currentFocusState = FocusState.DEEP_FOCUS
                    }
                    appCat == "games" || appCat == "gaming" -> {
                        currentTaskType = TaskType.GAMING
                        currentLaptopState = LaptopState.GAMING
                        currentFocusState = FocusState.BREAK
                    }
                    appCat == "system" || app.contains("away", ignoreCase = true) -> {
                        currentLaptopState = LaptopState.AWAY
                    }
                    else -> {
                        currentLaptopState = LaptopState.BROWSING
                    }
                }
                episodeBuilder.onLaptopWindowChange(appCat, effectiveTs)
            }
            "tab_category" -> {
                val domainCat = payload["domainCat"] as? String ?: return
                val isWork = domainCat in EpisodeBuilder.WORK_CATEGORIES
                if (!isWork) {
                    currentLaptopState = LaptopState.BROWSING
                    episodeBuilder.onLaptopWindowChange(domainCat, effectiveTs)
                } else {
                    currentLaptopState = LaptopState.FLOWING
                }
            }
            "edit_burst" -> {
                val editsPerMin = (payload["editsPerMin"] as? Number)?.toDouble() ?: 0.0
                if (editsPerMin > 0.0) {
                    currentLaptopState = LaptopState.FLOWING
                    // Do not change TaskType or any bucket key (Fix 3)
                    episodeBuilder.onLaptopEditBurst(editsPerMin, effectiveTs)
                }
            }
            "resume_first_edit" -> {
                val delayMs = (payload["delayMs"] as? Number)?.toLong() ?: 0L
                episodeBuilder.onResumeFirstEdit(delayMs, effectiveTs)
                currentFocusState = FocusState.WORKING
                focusStartTimeMs = effectiveTs
            }
            "idle_start" -> {
                currentLaptopState = LaptopState.IDLE_OPEN
            }
            "idle_end" -> {
                currentLaptopState = LaptopState.FLOWING
            }
            "gap" -> {
                val reason = payload["reason"] as? String ?: "relay_down"
                val local = payload["deviceFrom"] == "phone" && payload["deviceTo"] == "phone"
                episodeBuilder.onServiceGap(if (local) reason else "relay_down", effectiveTs)
            }
        }
    }
}
