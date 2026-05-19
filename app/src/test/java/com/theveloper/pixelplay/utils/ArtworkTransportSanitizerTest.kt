package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ArtworkTransportSanitizer] that don't require the Android
 * Bitmap stack. The pure-Kotlin code paths we can cover from a JVM unit
 * test are the input-size guard and null/empty short-circuits.
 *
 * The actual bitmap decode + re-encode round-trip requires
 * Bitmap/BitmapFactory which is a no-op on the JVM, so those paths are
 * exercised via instrumentation tests elsewhere.
 */
class ArtworkTransportSanitizerTest {

    @Test
    fun sanitize_nullInputReturnsNull() {
        val result = ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = null,
            config = ArtworkTransportSanitizer.WIDGET_CONFIG
        )
        assertThat(result).isNull()
    }

    @Test
    fun sanitize_emptyInputReturnsNull() {
        val result = ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = ByteArray(0),
            config = ArtworkTransportSanitizer.WIDGET_CONFIG
        )
        assertThat(result).isNull()
    }

    @Test
    fun sanitize_oversizedInputRejected() {
        // 1 byte over the widget cap (2 MiB). The sanitizer must bail before
        // calling into the native bitmap decoder — that decoder has a long
        // CVE history (e.g. CVE-2023-4863 in libwebp) and must not see
        // attacker-controlled bytes past the configured cap.
        val tooLarge = ByteArray(ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit + 1)
        val result = ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = tooLarge,
            config = ArtworkTransportSanitizer.WIDGET_CONFIG
        )
        assertThat(result).isNull()
    }

    @Test
    fun sanitize_wearConfigHasLargerLimit() {
        // Sanity check: wear gets a larger cap because watch screens need
        // higher-resolution artwork.
        assertThat(ArtworkTransportSanitizer.WEAR_CONFIG.sourceBytesLimit)
            .isGreaterThan(ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
    }

    @Test
    fun widgetConfig_dimensionLimitsAreSensible() {
        val cfg = ArtworkTransportSanitizer.WIDGET_CONFIG
        assertThat(cfg.maxDimensionPx).isGreaterThan(0)
        assertThat(cfg.maxBytes).isGreaterThan(0)
        assertThat(cfg.initialJpegQuality).isAtMost(100)
        assertThat(cfg.minJpegQuality).isAtMost(cfg.initialJpegQuality)
        assertThat(cfg.jpegQualityStep).isGreaterThan(0)
    }

    @Test
    fun wearConfig_dimensionLimitsAreSensible() {
        val cfg = ArtworkTransportSanitizer.WEAR_CONFIG
        assertThat(cfg.maxDimensionPx).isGreaterThan(0)
        assertThat(cfg.maxBytes).isGreaterThan(0)
        assertThat(cfg.initialJpegQuality).isAtMost(100)
        assertThat(cfg.minJpegQuality).isAtMost(cfg.initialJpegQuality)
    }
}
