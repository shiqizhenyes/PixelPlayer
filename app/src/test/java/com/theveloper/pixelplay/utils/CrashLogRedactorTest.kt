package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashLogRedactorTest {

    @Test
    fun redact_emptyAndNullInputs_returnEmptyString() {
        assertThat(CrashLogRedactor.redact(null)).isEqualTo("")
        assertThat(CrashLogRedactor.redact("")).isEqualTo("")
    }

    @Test
    fun redact_plainText_unchanged() {
        val input = "java.io.IOException: failed to open file /storage/emulated/0/Music/song.mp3"
        assertThat(CrashLogRedactor.redact(input)).isEqualTo(input)
    }

    @Test
    fun redact_bearerToken_isMasked() {
        val input = "GET /api/items failed with Authorization=Bearer eyJhbGciOiJIUzI1NiJ9.abc.def"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("Bearer [redacted]")
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
    }

    @Test
    fun redact_authorizationHeader_isMasked() {
        val input = "Authorization: Token abc123def456ghi789"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).isEqualTo("Authorization: [redacted]")
    }

    @Test
    fun redact_cookieHeader_isMasked() {
        val input = "Cookie: MUSIC_U=secretvalue; SessionId=xyz"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).isEqualTo("Cookie: [redacted]")
    }

    @Test
    fun redact_embyTokenHeader_isMasked() {
        val input = "X-Emby-Token: 1a2b3c4d5e6f"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).isEqualTo("X-Emby-Token: [redacted]")
    }

    @Test
    fun redact_googApiKeyHeader_isMasked() {
        val input = "x-goog-api-key: AIzaSyBexample1234567890"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).isEqualTo("x-goog-api-key: [redacted]")
    }

    @Test
    fun redact_geminiKeyInQueryString_isMasked() {
        val input = "https://generativelanguage.googleapis.com/v1/models?key=AIzaSyB123abc"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("?key=[redacted]")
        assertThat(result).doesNotContain("AIzaSyB123abc")
    }

    @Test
    fun redact_subsonicAuthParams_areMasked() {
        val input = "GET /rest/getSong.view?u=admin&t=abc123salted&s=randomsalt&v=1.16.1"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("&t=[redacted]")
        assertThat(result).contains("&s=[redacted]")
        assertThat(result).contains("u=admin")
        assertThat(result).contains("v=1.16.1")
        assertThat(result).doesNotContain("abc123salted")
        assertThat(result).doesNotContain("randomsalt")
    }

    @Test
    fun redact_accessAndRefreshTokens_areMasked() {
        val input = "url?access_token=ya29.abc&refresh_token=1//def"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("?access_token=[redacted]")
        assertThat(result).contains("&refresh_token=[redacted]")
        assertThat(result).doesNotContain("ya29.abc")
        assertThat(result).doesNotContain("1//def")
    }

    @Test
    fun redact_musicUCookie_isMasked() {
        val input = "got cookie MUSIC_U=abcdefg12345; expires=tomorrow"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("MUSIC_U=[redacted]")
        assertThat(result).doesNotContain("abcdefg12345")
    }

    @Test
    fun redact_telegramPhoneNumber_isMasked() {
        val input = "Failed authenticating +905551234567 with Telegram"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("[redacted]")
        assertThat(result).doesNotContain("+905551234567")
    }

    @Test
    fun redact_nonSensitiveQueryParam_untouched() {
        val input = "https://api.example.com/songs?id=42&limit=10"
        assertThat(CrashLogRedactor.redact(input)).isEqualTo(input)
    }

    @Test
    fun redact_multipleSecretsInSameInput_allMasked() {
        val input = """
            Authorization: Bearer eyJabc.def.ghi
            url=https://example/?key=AIza123&id=42
            Cookie: MUSIC_U=netease123; other=fine
        """.trimIndent()

        val result = CrashLogRedactor.redact(input)

        assertThat(result).doesNotContain("eyJabc.def.ghi")
        assertThat(result).doesNotContain("AIza123")
        assertThat(result).doesNotContain("netease123")
        assertThat(result).contains("Authorization: [redacted]")
        assertThat(result).contains("?key=[redacted]")
        assertThat(result).contains("Cookie: [redacted]")
        assertThat(result).contains("id=42")
    }

    @Test
    fun redact_caseInsensitiveBearer_masked() {
        val input = "bearer token-xyz-123"
        val result = CrashLogRedactor.redact(input)
        assertThat(result).contains("Bearer [redacted]")
        assertThat(result).doesNotContain("token-xyz-123")
    }
}
