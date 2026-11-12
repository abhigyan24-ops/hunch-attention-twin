package com.dhyan.app.notifications

import java.util.concurrent.ConcurrentHashMap

sealed class TriageDecision {
    object HOLD : TriageDecision()
    data class PASS_THROUGH_OTP(val extractedCode: String) : TriageDecision()
    object PASS_THROUGH_VIP : TriageDecision()
    object PASS_THROUGH_REPEATED_SENDER : TriageDecision()
    object PASS_THROUGH_SYSTEM_CATEGORY : TriageDecision()
}

object NotificationTriageEngine {
    // VIP packages — SharedPreferences-backed, but for testability expose a mutable Set in companion
    private val _vipPackages: MutableSet<String> = mutableSetOf()
    
    // Repeated sender tracking: key = "$pkg:$sender", value = deque of timestamps (ms)
    private val senderTimestamps: ConcurrentHashMap<String, ArrayDeque<Long>> = ConcurrentHashMap()
    
    private val OTP_REGEX = Regex("""(?i)\b\d{4,8}\b|\b[A-Z]-\d{4,6}\b""")
    private val OTP_KEYWORDS = setOf("otp","verification","code","passcode","2fa","login","signin","auth","bank","transaction","debit","verify","token")
    private val SYSTEM_CATEGORIES = setOf("android.intent.category.CALL", "android.intent.category.ALARM", "android.intent.category.CAR_EMERGENCY", "call", "alarm", "car_emergency")
    
    fun addVipPackage(pkg: String) { _vipPackages.add(pkg) }
    fun removeVipPackage(pkg: String) { _vipPackages.remove(pkg) }
    fun isVip(pkg: String): Boolean = _vipPackages.contains(pkg)
    
    /**
     * Core triage function. Pure and testable — takes all params explicitly.
     * NEVER persists OTP text or notification content to disk.
     * @param packageName App package name
     * @param senderKey Sender identifier (e.g. notification sender or package)
     * @param title Notification title
     * @param text Notification text body
     * @param category Notification category string (from Notification.category)
     * @param nowMs Current time in milliseconds
     */
    fun triage(
        packageName: String,
        senderKey: String,
        title: String,
        text: String,
        category: String?,
        nowMs: Long = System.currentTimeMillis()
    ): TriageDecision {
        // 1. System category — always pass
        if (!category.isNullOrBlank()) {
            val catLower = category.lowercase()
            if (SYSTEM_CATEGORIES.any { catLower.contains(it) }) {
                return TriageDecision.PASS_THROUGH_SYSTEM_CATEGORY
            }
        }
        
        // 2. VIP package — always pass
        if (_vipPackages.contains(packageName)) {
            return TriageDecision.PASS_THROUGH_VIP
        }
        
        // 3. OTP detection — volatile in-memory only, never persisted
        val combined = "$title $text"
        val otpMatch = OTP_REGEX.find(combined)
        if (otpMatch != null) {
            val hasKeyword = OTP_KEYWORDS.any { combined.lowercase().contains(it) }
            if (hasKeyword) {
                return TriageDecision.PASS_THROUGH_OTP(otpMatch.value) // code lives only in RAM
            }
        }
        
        // 4. Repeated sender (>=3 in rolling 180s window)
        val key = "$packageName:$senderKey"
        val timestamps = senderTimestamps.getOrPut(key) { ArrayDeque() }
        synchronized(timestamps) {
            val cutoff = nowMs - 180_000L
            while (timestamps.isNotEmpty() && timestamps.first() < cutoff) timestamps.removeFirst()
            timestamps.addLast(nowMs)
            if (timestamps.size >= 3) {
                return TriageDecision.PASS_THROUGH_REPEATED_SENDER
            }
        }
        
        return TriageDecision.HOLD
    }
    
    /** Clear sender cache (call at session start or for testing) */
    fun clearSenderCache() { senderTimestamps.clear() }
    
    /** Pure static helper for unit tests — extracts OTP code from text */
    fun extractOtpCode(text: String): String? {
        val regex = Regex("""(?i)\b\d{4,8}\b|\b[A-Z]-\d{4,6}\b""")
        return regex.find(text)?.value
    }
    
    fun hasOtpKeyword(text: String): Boolean {
        val keywords = setOf("otp","verification","code","passcode","2fa","login","signin","auth","bank","transaction","debit","verify","token")
        return keywords.any { text.lowercase().contains(it) }
    }
}
