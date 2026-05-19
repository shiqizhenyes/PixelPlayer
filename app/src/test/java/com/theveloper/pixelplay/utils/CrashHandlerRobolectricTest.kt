package com.theveloper.pixelplay.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [CrashHandler] — the in-process crash-capture
 * helper that writes a stripped, redacted stack trace to SharedPreferences
 * so the next launch can surface it to the user.
 *
 * Before this test landed, [CrashHandler] had no coverage: it runs only on
 * the uncaught-exception path, which is the most brittle code class to
 * leave untested.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [30])
class CrashHandlerRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset the default handler to JDK's so install() doesn't capture a
        // previous CrashHandler instance as defaultHandler — that would
        // cause infinite recursion when uncaughtException is invoked.
        Thread.setDefaultUncaughtExceptionHandler(null)
        CrashHandler.install(context)
        // Always start clean — the SharedPreferences file is shared across tests
        // and Robolectric runs them in the same VM.
        CrashHandler.clearCrashLog()
    }

    @After
    fun tearDown() {
        CrashHandler.clearCrashLog()
    }

    @Test
    fun hasCrashLog_returnsFalseBeforeAnyCrash() {
        assertThat(CrashHandler.hasCrashLog()).isFalse()
    }

    @Test
    fun getCrashLog_returnsNullBeforeAnyCrash() {
        assertThat(CrashHandler.getCrashLog()).isNull()
    }

    @Test
    fun uncaughtException_persistsRedactedStackTrace() {
        // Trigger the persistence path with a synthetic exception containing
        // a credential pattern. The redactor must strip it before storage.
        val token = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"
        val cause = RuntimeException("Failed: Authorization: $token")
        // Use an arbitrary thread — CrashHandler signature accepts any thread.
        CrashHandler.uncaughtException(Thread.currentThread(), cause)

        val log = CrashHandler.getCrashLog()
        assertThat(log).isNotNull()
        // The redactor should have stripped the bearer token.
        assertThat(log!!.exceptionMessage).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        assertThat(log.stackTrace).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        // But the class name and "Failed" prefix should survive.
        assertThat(log.stackTrace).contains("RuntimeException")
    }

    @Test
    fun clearCrashLog_clearsPersistedLog() {
        CrashHandler.uncaughtException(
            Thread.currentThread(),
            IllegalStateException("test crash")
        )
        assertThat(CrashHandler.hasCrashLog()).isTrue()

        CrashHandler.clearCrashLog()
        assertThat(CrashHandler.hasCrashLog()).isFalse()
        assertThat(CrashHandler.getCrashLog()).isNull()
    }

    @Test
    fun getCrashLog_formattedDateIsPopulated() {
        CrashHandler.uncaughtException(
            Thread.currentThread(),
            IllegalArgumentException("formatted date check")
        )

        val log = CrashHandler.getCrashLog()
        assertThat(log).isNotNull()
        // The formattedDate string follows dd/MM/yyyy HH:mm:ss; spot-check
        // shape only (locale variations make exact match flaky).
        assertThat(log!!.formattedDate).matches("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}""")
        assertThat(log.timestamp).isGreaterThan(0L)
    }

    @Test
    fun getFullLog_includesAllFields() {
        CrashHandler.uncaughtException(
            Thread.currentThread(),
            IllegalArgumentException("full-log shape")
        )

        val log = CrashHandler.getCrashLog()
        val rendered = log!!.getFullLog()
        assertThat(rendered).contains("PixelPlayer Crash Report")
        assertThat(rendered).contains("Date:")
        assertThat(rendered).contains("Exception:")
        assertThat(rendered).contains("Stack Trace:")
        assertThat(rendered).contains("full-log shape")
    }
}
