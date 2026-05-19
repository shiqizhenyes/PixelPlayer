package com.theveloper.pixelplay.data.service.http

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.cio.CIO
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Test

/**
 * Ktor-route tests for the auth-token + song-allowlist enforcement that
 * gates the Cast HTTP server's `/song/{songId}` and `/art/{songId}` routes.
 *
 * These tests stand up a minimal Ktor `testApplication` with the same
 * authorization guard the real service uses (via [CastSessionSecurity]),
 * decoupled from the full `MediaFileHttpServerService` DI graph so the
 * route-level invariants can be verified in isolation.
 *
 * The full service is exercised by instrumented tests; these unit tests
 * cover the policy gate, which is the security boundary the review
 * specifically called out.
 */
class CastHttpRouteAuthTest {

    private val policy = CastAccessPolicy(
        authToken = "token-abc",
        allowedSongIds = setOf("42", "100"),
        allowedClientAddresses = setOf("192.168.1.50"),
        enforceClientAddressAllowlist = true
    )

    @Test
    fun songRoute_rejectsRequestWithoutAuthToken() = testApplication {
        application {
            routing {
                get("/song/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "song=$songId")
                }
            }
        }

        val response: HttpResponse = client.get("/song/42")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun songRoute_rejectsWrongAuthToken() = testApplication {
        application {
            routing {
                get("/song/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "song=$songId")
                }
            }
        }

        val response = client.get("/song/42?auth=wrong-token")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun songRoute_rejectsAuthorizedTokenButUnknownSongId() = testApplication {
        application {
            routing {
                get("/song/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "song=$songId")
                }
            }
        }

        // 99 is not in the song allowlist (only 42 and 100 are).
        val response = client.get("/song/99?auth=token-abc")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun songRoute_acceptsValidTokenAndKnownSongId() = testApplication {
        application {
            routing {
                get("/song/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "song=$songId")
                }
            }
        }

        val response = client.get("/song/42?auth=token-abc")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun healthRoute_acceptsLoopbackOnly() = testApplication {
        application {
            routing {
                get("/health") {
                    // Test harness emulates the loopback check via the
                    // request's remote address being absent / blank.
                    val remote = call.request.local.remoteHost
                    if (!CastSessionSecurity.isLoopbackAddress(remote)) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "ok")
                }
            }
        }

        // Ktor's testApplication runs the client over a synthetic in-memory
        // transport; `remoteHost` is reported as a loopback / localhost
        // address, which is what the real server's health check requires.
        val response = client.get("/health")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun artRoute_sharesAuthEnforcement() = testApplication {
        application {
            routing {
                get("/art/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "art=$songId")
                }
            }
        }

        // Same allowlist as songs; the policy applies to both routes.
        assertThat(client.get("/art/100?auth=token-abc").status)
            .isEqualTo(HttpStatusCode.OK)
        assertThat(client.get("/art/99?auth=token-abc").status)
            .isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.get("/art/100").status)
            .isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun songRoute_rejectsExtraneousSongIdSuffix() = testApplication {
        application {
            routing {
                get("/song/{songId}") {
                    val songId = call.parameters["songId"]
                    val provided = call.request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
                    if (!CastSessionSecurity.isAuthorizedSongRequest(provided, songId, policy)) {
                        call.respond(HttpStatusCode.Unauthorized, "no")
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, "song=$songId")
                }
            }
        }

        // "42x" is not in the policy's allowlist; only "42" and "100" are.
        // Verifies the song-id allowlist check rejects suffix-extended IDs.
        val response = client.get("/song/42x?auth=token-abc")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }
}
