package com.dhyan.app.ingest

import android.app.Notification
import android.os.Bundle

/**
 * Ephemeral extractor for text-derived notification safety signals and transient hold payload.
 *
 * PRIVACY CONTRACT (Spec 25.8, D56-1, D6-1):
 * - EXTRA_TITLE and EXTRA_TEXT are accessed ONLY within this class.
 * - [isOtpLike] and [hasEmergencyKeyword] are transient in-memory booleans passed to [NotificationFacts].
 *   They are never logged, persisted in events, or exported.
 * - [holdPayload] is transient in-memory string ("title\ntext") passed directly to the actuator's
 *   Keystore cipher. It is never logged, stored in plain text in any DB column, or shared across processes.
 */
object NotifExtractor {

    data class ExtractedNotif(
        val isOtpLike: Boolean,
        val hasEmergencyKeyword: Boolean,
        val holdPayload: String?
    )

    private val OTP_CODE_REGEX = Regex("""\b\d{4,8}\b""")
    private val OTP_KEYWORD_REGEX = Regex(
        """\b(otp|code|verification|verify|passcode|one[- ]time|secret|pin|auth|login code|security code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val EMERGENCY_KEYWORD_REGEX = Regex(
        """\b(emergency|urgent|sos|ambulance|hospital|police|fire|danger|cardiac|accident|distress|112|911)\b""",
        RegexOption.IGNORE_CASE
    )

    fun extract(notification: Notification?): ExtractedNotif {
        val extras: Bundle? = notification?.extras
        if (extras == null) {
            return extract(title = null, text = null, bigText = null)
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        return extract(title = title, text = text, bigText = bigText)
    }

    fun extract(title: CharSequence?, text: CharSequence?, bigText: CharSequence? = null): ExtractedNotif {
        val tStr = title?.toString()?.trim()
        val textStr = text?.toString()?.trim()
        val bigStr = bigText?.toString()?.trim()

        val fullText = buildString {
            if (!tStr.isNullOrBlank()) append(tStr).append(' ')
            if (!textStr.isNullOrBlank()) append(textStr).append(' ')
            if (!bigStr.isNullOrBlank()) append(bigStr)
        }.trim()

        if (fullText.isEmpty()) {
            return ExtractedNotif(
                isOtpLike = false,
                hasEmergencyKeyword = false,
                holdPayload = null
            )
        }

        val hasOtpKeyword = OTP_KEYWORD_REGEX.containsMatchIn(fullText)
        val hasOtpCode = OTP_CODE_REGEX.containsMatchIn(fullText)
        val isOtp = hasOtpKeyword && hasOtpCode

        val hasEmergency = EMERGENCY_KEYWORD_REGEX.containsMatchIn(fullText)

        val holdPayload = if (!tStr.isNullOrBlank() || !textStr.isNullOrBlank() || !bigStr.isNullOrBlank()) {
            val t = tStr ?: ""
            val b = if (!textStr.isNullOrBlank()) textStr else (bigStr ?: "")
            if (t.isNotBlank() && b.isNotBlank()) "$t\n$b"
            else if (t.isNotBlank()) t
            else b
        } else null

        return ExtractedNotif(
            isOtpLike = isOtp,
            hasEmergencyKeyword = hasEmergency,
            holdPayload = holdPayload
        )
    }
}
