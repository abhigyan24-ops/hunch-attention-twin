package com.dhyan.app.sync.data

import java.util.regex.Pattern

/**
 * Validates data envelopes and incoming payloads for strict privacy compliance.
 * References: core-spec/forbidden-fields.json and tools/privacy-scan.mjs.
 *
 * Privacy rule: Categories and counts only.
 * NEVER persist or forward raw notification text, titles, URLs, or keystrokes!
 */
object PrivacyGuard {

    val FORBIDDEN_FIELDS: Set<String> = setOf(
        "title",
        "text",
        "body",
        "subText",
        "bigText",
        "summaryText",
        "tickerText",
        "url",
        "uri",
        "href",
        "windowTitle",
        "tabTitle",
        "documentTitle",
        "filePath",
        "content",
        "message",
        "rawText",
        "keystrokes",
        "typedText",
        "phoneNumber",
        "email",
        "contactName"
    )

    private val URL_PATTERN = Pattern.compile("https?://[^\\s\"]+", Pattern.CASE_INSENSITIVE)
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val CANARY_PATTERN = Pattern.compile("CANARY_[A-Z0-9_]+", Pattern.CASE_INSENSITIVE)

    /**
     * Inspects an arbitrary object (Map, List, or primitive) for forbidden keys or canary text.
     * Returns true if strictly clean, false if any privacy violation is found.
     */
    fun isClean(obj: Any?): Boolean {
        return findViolations(obj).isEmpty()
    }

    /**
     * Collects all privacy violation descriptions found within the given structure.
     */
    fun findViolations(obj: Any?, currentPath: String = ""): List<String> {
        val violations = mutableListOf<String>()
        if (obj == null) return violations

        when (obj) {
            is Map<*, *> -> {
                for ((key, value) in obj) {
                    val keyStr = key?.toString() ?: continue
                    val fullPath = if (currentPath.isEmpty()) keyStr else "$currentPath.$keyStr"

                    if (FORBIDDEN_FIELDS.contains(keyStr)) {
                        violations.add("Forbidden field name detected: $fullPath")
                    }

                    violations.addAll(findViolations(value, fullPath))
                }
            }
            is List<*> -> {
                for (i in obj.indices) {
                    violations.addAll(findViolations(obj[i], "$currentPath[$i]"))
                }
            }
            is String -> {
                if (URL_PATTERN.matcher(obj).find()) {
                    violations.add("URL pattern match at $currentPath")
                }
                if (EMAIL_PATTERN.matcher(obj).find()) {
                    violations.add("Email pattern match at $currentPath")
                }
                if (CANARY_PATTERN.matcher(obj).find()) {
                    violations.add("Canary pattern match at $currentPath")
                }
            }
        }
        return violations
    }
}
