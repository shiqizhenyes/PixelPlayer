package com.theveloper.pixelplay.data.service

import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric smoke test confirming the test infrastructure works for
 * Android-component test code. This is the foundation for the broader
 * MusicService instrumentation-style tests the review called out.
 *
 * Specific tests can be layered on top — the test classpath now has
 * Robolectric available, JUnit Platform configured via the Vintage
 * engine, and Android resources included in the unit-test sourceSet.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [30])
class MusicServiceConstantsRobolectricTest {

    @Test
    fun applicationContext_isAvailable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(context).isNotNull()
        assertThat(context.packageName).contains("com.theveloper.pixelplay")
    }

    @Test
    fun playbackStateIntent_constantsAreStable() {
        // Smoke check that the Android framework's PlaybackState constants
        // we depend on for MusicService's media-session integration remain
        // their documented integer values. If these ever drift, the
        // notification + lock-screen integration breaks silently.
        assertThat(PlaybackState.STATE_PLAYING).isEqualTo(3)
        assertThat(PlaybackState.STATE_PAUSED).isEqualTo(2)
        assertThat(PlaybackState.STATE_STOPPED).isEqualTo(1)
        assertThat(PlaybackState.STATE_BUFFERING).isEqualTo(6)
    }
}
