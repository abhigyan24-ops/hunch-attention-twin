package com.dhyan.app.actuator

import android.app.NotificationManager
import android.content.Context
import com.dhyan.app.twin.model.LaptopState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages real laptop <-> phone focus leases and pre-emptive banner/sound blocking.
 *
 * Requirements:
 * - When focus == "deep" is received from laptop or when user starts a focus session on phone,
 *   AND notificationManager.isNotificationPolicyAccessGranted is true:
 *   - Saves prior currentInterruptionFilter.
 *   - Sets NotificationManager.INTERRUPTION_FILTER_PRIORITY.
 *   - Starts a 90-second lease countdown timer (leaseExpiryJob) that auto-restores DND
 *     if the laptop disconnects or stops renewing the lease.
 * - When focus returns to normal or session ends: restores prior interruption filter immediately.
 */
class FocusLeaseController(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val nm: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val mutex = Mutex()
    private var priorInterruptionFilter: Int? = null
    private var isHoldingDnd: Boolean = false
    private var leaseExpiryJob: Job? = null

    private val _isDeepFocusActive = MutableStateFlow(false)
    val isDeepFocusActive: StateFlow<Boolean> = _isDeepFocusActive.asStateFlow()

    companion object {
        const val DEFAULT_LEASE_DURATION_MS = 90_000L // 90 seconds lease

        @Volatile
        private var instance: FocusLeaseController? = null

        fun getInstance(context: Context): FocusLeaseController =
            instance ?: synchronized(this) {
                instance ?: FocusLeaseController(context.applicationContext).also { instance = it }
            }

        fun hasPolicyAccess(context: Context): Boolean {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return mgr?.isNotificationPolicyAccessGranted == true
        }
    }

    /**
     * Called when a laptop state / focus update arrives.
     * focus: "deep", "normal", "flow", etc.
     */
    fun onLaptopFocusStateChanged(focus: String?, laptopState: LaptopState? = null) {
        scope.launch {
            val isDeep = focus?.equals("deep", ignoreCase = true) == true ||
                (laptopState == LaptopState.FLOWING && focus?.equals("deep", ignoreCase = true) == true)
            if (isDeep) {
                acquireOrRenewLease(DEFAULT_LEASE_DURATION_MS)
            } else if (focus?.equals("normal", ignoreCase = true) == true || laptopState == LaptopState.OFFLINE) {
                // If laptop went offline or returned to normal, release lease
                releaseLease()
            }
        }
    }

    /**
     * Called when phone focus session starts.
     */
    fun onPhoneFocusSessionStarted(durationMinutes: Double) {
        scope.launch {
            val durationMs = (durationMinutes * 60_000).toLong().coerceAtLeast(DEFAULT_LEASE_DURATION_MS)
            acquireOrRenewLease(durationMs)
        }
    }

    /**
     * Called when phone focus session ends.
     */
    fun onPhoneFocusSessionEnded() {
        scope.launch {
            releaseLease()
        }
    }

    suspend fun acquireOrRenewLease(durationMs: Long = DEFAULT_LEASE_DURATION_MS) = mutex.withLock {
        _isDeepFocusActive.value = true
        val manager = nm ?: return@withLock
        val hasAccess = runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)

        if (hasAccess) {
            if (!isHoldingDnd) {
                priorInterruptionFilter = runCatching { manager.currentInterruptionFilter }.getOrNull()
                runCatching {
                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    isHoldingDnd = true
                }
            }
        }

        // Renew 90-second lease countdown timer
        leaseExpiryJob?.cancel()
        leaseExpiryJob = scope.launch {
            delay(durationMs)
            mutex.withLock {
                restoreDndLocked()
            }
        }
    }

    suspend fun releaseLease() = mutex.withLock {
        restoreDndLocked()
    }

    private fun restoreDndLocked() {
        leaseExpiryJob?.cancel()
        leaseExpiryJob = null
        _isDeepFocusActive.value = false

        if (isHoldingDnd) {
            val manager = nm
            val prior = priorInterruptionFilter
            if (manager != null && prior != null) {
                val hasAccess = runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
                if (hasAccess) {
                    runCatching { manager.setInterruptionFilter(prior) }
                }
            }
            isHoldingDnd = false
            priorInterruptionFilter = null
        }
    }
}
