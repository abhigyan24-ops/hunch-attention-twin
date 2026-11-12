package com.dhyan.app.privacy

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicInteger

data class LedgerSnapshot(
    val appTransitionsCount: Int,
    val notificationsHeldCount: Int,
    val otpBypassedCount: Int,
    val circadianSyncCount: Int,
    // Always 0 — privacy guarantee
    val rawKeystrokesLogged: Int = 0,
    val rawNotificationTextBytesStored: Int = 0,
    val sourceCodeBytesCaptured: Int = 0
)

class PrivacyLedgerManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_ledger", Context.MODE_PRIVATE)

    private val appTransitionsCount = AtomicInteger(prefs.getInt("appTransitions", 0))
    private val notificationsHeldCount = AtomicInteger(prefs.getInt("notificationsHeld", 0))
    private val otpBypassedCount = AtomicInteger(prefs.getInt("otpBypassed", 0))
    private val circadianSyncCount = AtomicInteger(prefs.getInt("circadianSync", 0))

    fun incrementAppTransitions() {
        prefs.edit().putInt("appTransitions", appTransitionsCount.incrementAndGet()).apply()
    }

    fun incrementNotificationsHeld() {
        prefs.edit().putInt("notificationsHeld", notificationsHeldCount.incrementAndGet()).apply()
    }

    fun incrementOtpBypassed() {
        prefs.edit().putInt("otpBypassed", otpBypassedCount.incrementAndGet()).apply()
    }

    fun incrementCircadianSync() {
        prefs.edit().putInt("circadianSync", circadianSyncCount.incrementAndGet()).apply()
    }

    fun resetToday() {
        appTransitionsCount.set(0)
        notificationsHeldCount.set(0)
        otpBypassedCount.set(0)
        circadianSyncCount.set(0)
        prefs.edit().clear().apply()
    }

    fun snapshot(): LedgerSnapshot = LedgerSnapshot(
        appTransitionsCount = appTransitionsCount.get(),
        notificationsHeldCount = notificationsHeldCount.get(),
        otpBypassedCount = otpBypassedCount.get(),
        circadianSyncCount = circadianSyncCount.get()
    )

    companion object {
        /** Pure function for unit testing snapshot logic */
        fun buildSnapshot(transitions: Int, held: Int, otp: Int, circadian: Int): LedgerSnapshot =
            LedgerSnapshot(
                appTransitionsCount = transitions,
                notificationsHeldCount = held,
                otpBypassedCount = otp,
                circadianSyncCount = circadian
            )
    }
}
