package com.theveloper.pixelplay.data.service.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastSessionSecurityTest {

    @Test
    fun `buildAccessPolicy keeps existing token and narrows song ids`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = "existing-token",
            allowedSongIds = listOf("1", " 2 ", "", "1"),
            castDeviceIpHint = "192.168.1.50"
        )

        assertEquals("existing-token", policy.authToken)
        assertEquals(setOf("1", "2"), policy.allowedSongIds)
        assertTrue(policy.enforceClientAddressAllowlist)
        assertTrue(policy.allowedClientAddresses.contains("192.168.1.50"))
    }

    @Test
    fun `isAuthorizedClientAddress always allows loopback`() {
        val policy = CastAccessPolicy.EMPTY.copy(
            enforceClientAddressAllowlist = true,
            allowedClientAddresses = setOf("192.168.1.50")
        )

        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("127.0.0.1", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("::1", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.80", policy))
    }

    @Test
    fun `isAuthorizedSongRequest requires matching token and whitelisted id`() {
        val policy = CastAccessPolicy(
            authToken = "token-123",
            allowedSongIds = setOf("42"),
            allowedClientAddresses = emptySet(),
            enforceClientAddressAllowlist = false
        )

        assertTrue(CastSessionSecurity.isAuthorizedSongRequest("token-123", "42", policy))
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("wrong", "42", policy))
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("token-123", "43", policy))
    }

    @Test
    fun `buildSongUrl appends auth token and encodes song id`() {
        val url = CastSessionSecurity.buildSongUrl(
            serverAddress = "http://192.168.1.10:8080",
            songId = "abc/123",
            streamRevision = "deadbeef",
            authToken = "secret"
        )

        assertTrue(url.contains("/song/abc%2F123"))
        assertTrue(url.contains("v=deadbeef"))
        assertTrue(url.contains("${CastSessionSecurity.AUTH_QUERY_PARAMETER}=secret"))
        assertTrue(CastSessionSecurity.redactAuthToken(url).contains("${CastSessionSecurity.AUTH_QUERY_PARAMETER}=<redacted>"))
    }

    @Test
    fun `buildAccessPolicy generates token when missing`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = null,
            allowedSongIds = listOf("1"),
            castDeviceIpHint = null
        )

        assertNotNull(policy.authToken)
        assertTrue(policy.authToken!!.length >= 32)
        // Allowlist must stay enforced even when the Cast device IP is unknown;
        // the allowed set then contains only loopback (default-deny LAN).
        assertTrue(policy.enforceClientAddressAllowlist)
    }

    @Test
    fun `isAuthorizedClientAddress denies LAN when no Cast hint was provided`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = null,
            allowedSongIds = listOf("1"),
            castDeviceIpHint = null
        )

        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("127.0.0.1", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.80", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("10.0.0.5", policy))
    }

    @Test
    fun `isAuthorizedSongRequest rejects blank or null song id`() {
        val policy = CastAccessPolicy(
            authToken = "tk",
            allowedSongIds = setOf("42"),
            allowedClientAddresses = emptySet(),
            enforceClientAddressAllowlist = false
        )
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("tk", null, policy))
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("tk", "", policy))
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("tk", "   ", policy))
    }

    @Test
    fun `isAuthorizedSongRequest rejects when policy has no auth token`() {
        val policy = CastAccessPolicy(
            authToken = null,
            allowedSongIds = setOf("42"),
            allowedClientAddresses = emptySet(),
            enforceClientAddressAllowlist = false
        )
        assertFalse(CastSessionSecurity.isAuthorizedSongRequest("anything", "42", policy))
    }

    @Test
    fun `isLoopbackAddress recognizes IPv4 IPv6 and mapped forms`() {
        assertTrue(CastSessionSecurity.isLoopbackAddress("127.0.0.1"))
        assertTrue(CastSessionSecurity.isLoopbackAddress("::1"))
        assertTrue(CastSessionSecurity.isLoopbackAddress("0:0:0:0:0:0:0:1"))
        assertTrue(CastSessionSecurity.isLoopbackAddress("::ffff:127.0.0.1"))
        assertFalse(CastSessionSecurity.isLoopbackAddress("192.168.1.1"))
        assertFalse(CastSessionSecurity.isLoopbackAddress(null))
        assertFalse(CastSessionSecurity.isLoopbackAddress(""))
    }

    @Test
    fun `redactAuthToken leaves non-auth params intact`() {
        val url = "http://host/song/42?v=abc&" + CastSessionSecurity.AUTH_QUERY_PARAMETER + "=supersecret&extra=xyz"
        val redacted = CastSessionSecurity.redactAuthToken(url)
        assertTrue(redacted.contains("v=abc"))
        assertTrue(redacted.contains("extra=xyz"))
        assertFalse(redacted.contains("supersecret"))
        assertTrue(redacted.contains("${CastSessionSecurity.AUTH_QUERY_PARAMETER}=<redacted>"))
    }

    @Test
    fun `generated auth tokens differ across calls`() {
        val a = CastSessionSecurity.buildAccessPolicy(null, emptyList(), null).authToken
        val b = CastSessionSecurity.buildAccessPolicy(null, emptyList(), null).authToken
        assertNotNull(a)
        assertNotNull(b)
        // Distinct SecureRandom outputs — collision probability ~2^-128.
        assertFalse(a == b)
    }

    @Test
    fun `buildAccessPolicy server ip is added to allowlist`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = "t",
            allowedSongIds = listOf("1"),
            castDeviceIpHint = "192.168.1.50",
            serverOwnIp = "192.168.1.42"
        )
        assertTrue(policy.allowedClientAddresses.contains("192.168.1.42"))
        assertTrue(policy.allowedClientAddresses.contains("192.168.1.50"))
    }

    @Test
    fun `buildArtUrl shares structure with buildSongUrl`() {
        val songUrl = CastSessionSecurity.buildSongUrl(
            serverAddress = "http://192.168.1.10:8080",
            songId = "42",
            streamRevision = "v1",
            authToken = "tk"
        )
        val artUrl = CastSessionSecurity.buildArtUrl(
            serverAddress = "http://192.168.1.10:8080",
            songId = "42",
            streamRevision = "v1",
            authToken = "tk"
        )
        assertTrue(songUrl.contains("/song/42"))
        assertTrue(artUrl.contains("/art/42"))
        assertTrue(songUrl.contains("auth=tk"))
        assertTrue(artUrl.contains("auth=tk"))
    }
}
