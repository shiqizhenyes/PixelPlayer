package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sanitization tests for [sanitizeShareFileName]. Covers the
 * adversarial input cases the security review flagged: path-traversal
 * sequences, OS-reserved chars, leading dots that produce hidden files,
 * whitespace collapse, and length capping.
 */
class ZipShareHelperSanitizationTest {

    @Test
    fun sanitize_keepsSimpleAsciiTitle() {
        val result = sanitizeShareFileName("My Favourite Song")
        assertThat(result).isEqualTo("My_Favourite_Song")
    }

    @Test
    fun sanitize_keepsUnicodeTitle() {
        val result = sanitizeShareFileName("Cafe Tacvba")
        assertThat(result).isEqualTo("Cafe_Tacvba")
    }

    @Test
    fun sanitize_replacesSlashesWithUnderscore() {
        val result = sanitizeShareFileName("foo/bar\\baz")
        assertThat(result).isEqualTo("foo_bar_baz")
    }

    @Test
    fun sanitize_replacesShellChars() {
        val result = sanitizeShareFileName("a:b*c?d\"e<f>g|h")
        assertThat(result).isEqualTo("a_b_c_d_e_f_g_h")
    }

    @Test
    fun sanitize_rejectsPathTraversalDoubleDot() {
        val result = sanitizeShareFileName("../etc/passwd")
        assertThat(result).doesNotContain("..")
        // Leading dots are replaced with `_`, and slashes become `_`.
        assertThat(result).isEqualTo("__etc_passwd")
    }

    @Test
    fun sanitize_rejectsPathTraversalEvenWithoutSlash() {
        val result = sanitizeShareFileName("song..album")
        assertThat(result).doesNotContain("..")
        assertThat(result).contains("song")
        assertThat(result).contains("album")
    }

    @Test
    fun sanitize_replacesLeadingDots() {
        val result = sanitizeShareFileName(".hiddenfile")
        // Leading "." replaced with "_" — does not produce a dotfile.
        assertThat(result).startsWith("_")
        assertThat(result.startsWith(".")).isFalse()
    }

    @Test
    fun sanitize_replacesMultipleLeadingDots() {
        val result = sanitizeShareFileName("...sneaky")
        assertThat(result.startsWith(".")).isFalse()
        assertThat(result).doesNotContain("..")
    }

    @Test
    fun sanitize_collapsesWhitespace() {
        val result = sanitizeShareFileName("a   b\t\nc")
        // All whitespace runs collapse to a single underscore.
        assertThat(result).isEqualTo("a_b_c")
    }

    @Test
    fun sanitize_capsLengthAt100Chars() {
        val longName = "a".repeat(500)
        val result = sanitizeShareFileName(longName)
        assertThat(result.length).isAtMost(100)
    }

    @Test
    fun sanitize_emptyInputProducesEmpty() {
        val result = sanitizeShareFileName("")
        assertThat(result).isEmpty()
    }

    @Test
    fun sanitize_onlyDotsProducesUnderscore() {
        // ".." → "_" after the leading-dots regex strips them all.
        val result = sanitizeShareFileName("..")
        assertThat(result).isEqualTo("_")
    }

    @Test
    fun sanitize_percentEncodedTraversalIsHarmless() {
        // %2F is not a real separator in the local filesystem so it passes
        // through unchanged; the important thing is no real path separator
        // survives, which the other tests cover.
        val result = sanitizeShareFileName("a%2Fb")
        assertThat(result).doesNotContain("/")
    }
}
