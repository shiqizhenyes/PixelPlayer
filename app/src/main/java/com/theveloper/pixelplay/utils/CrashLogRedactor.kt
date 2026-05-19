package com.theveloper.pixelplay.utils

/**
 * Redacts likely-PII and credential material from crash text before it is
 * persisted to SharedPreferences or shared via the crash report dialog.
 *
 * The strategy favors false positives (stripping a benign substring) over
 * false negatives (leaking a token). Patterns target the credential shapes
 * we know flow through this codebase: OAuth bearer tokens, Subsonic salted
 * tokens, Jellyfin/Emby session headers, Google API keys, NetEase MUSIC_U
 * cookies, and Telegram phone numbers.
 */
object CrashLogRedactor {

    private const val REDACTED = "[redacted]"

    private val SENSITIVE_QUERY_KEYS = listOf(
        "key", "api_key", "apikey",
        "access_token", "refresh_token", "token", "auth",
        "password", "pass", "pwd",
        "sig", "signature",
        "t", "s", "p", "salt"
    )

    private val SENSITIVE_HEADER_PATTERN = Regex(
        "(?im)^[ \\t]*(Authorization|Proxy-Authorization|Cookie|Set-Cookie|" +
            "X-Emby-Token|X-Emby-Authorization|X-MediaBrowser-Token|x-goog-api-key)" +
            "\\s*:\\s*.+$"
    )

    private val BEARER_PATTERN = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._\\-+/=]+")

    private val PHONE_PATTERN = Regex("\\+\\d{10,15}\\b")

    private val MUSIC_U_COOKIE_PATTERN = Regex("(?i)MUSIC_U=[^;\\s]+")

    private val QUERY_PARAM_PATTERN = Regex(
        "(?i)([?&])(" + SENSITIVE_QUERY_KEYS.joinToString("|") + ")=([^&\\s\"'\\]]+)"
    )

    fun redact(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var output = input
        output = SENSITIVE_HEADER_PATTERN.replace(output) { match ->
            val headerName = match.value.substringBefore(':').trimStart()
            "$headerName: $REDACTED"
        }
        output = BEARER_PATTERN.replace(output, "Bearer $REDACTED")
        output = MUSIC_U_COOKIE_PATTERN.replace(output, "MUSIC_U=$REDACTED")
        output = QUERY_PARAM_PATTERN.replace(output) { match ->
            val sep = match.groupValues[1]
            val key = match.groupValues[2]
            "$sep$key=$REDACTED"
        }
        output = PHONE_PATTERN.replace(output, REDACTED)
        return output
    }
}
