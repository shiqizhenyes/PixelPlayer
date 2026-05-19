package com.theveloper.pixelplay.data.worker

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.worker.SyncWorker.Companion.stableFnv1aHash64
import com.theveloper.pixelplay.data.worker.SyncWorker.Companion.stableNegativeSyntheticId
import org.junit.Test

/**
 * Stability + collision tests for the synthetic-ID hash that replaced
 * `String.hashCode()` for Telegram/Netease song/album/artist IDs.
 *
 * Why this matters: with 32-bit hashing, a Telegram library of ~65k tracks
 * has roughly 50% collision probability per the birthday bound, which
 * historically caused row overwrites. The 64-bit FNV-1a should keep the
 * collision probability essentially zero for non-adversarial inputs.
 */
class SyncWorkerHashTest {

    @Test
    fun fnv1a_emptyString_returnsOffsetBasis() {
        // Spec value for FNV-1a 64-bit offset basis.
        assertThat(stableFnv1aHash64("")).isEqualTo(-3750763034362895579L)
    }

    @Test
    fun fnv1a_isDeterministicAcrossCalls() {
        val a = stableFnv1aHash64("Some Artist Name")
        val b = stableFnv1aHash64("Some Artist Name")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun fnv1a_differentInputsProduceDifferentHashes() {
        val a = stableFnv1aHash64("song_one")
        val b = stableFnv1aHash64("song_two")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun fnv1a_singleCharacterChangeBreaksHash() {
        // Avalanche: a 1-bit change in input should flip many output bits.
        val a = stableFnv1aHash64("track_001")
        val b = stableFnv1aHash64("track_002")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun fnv1a_caseSensitive() {
        // We lowercase before hashing in callers, but make sure the hash
        // itself is case-sensitive so the lowercasing actually does work.
        val a = stableFnv1aHash64("Foo")
        val b = stableFnv1aHash64("foo")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun syntheticId_isAlwaysNegative() {
        listOf("a", "abcdefg", "track 12345", "Some Album Title", "x".repeat(1000)).forEach { input ->
            assertThat(stableNegativeSyntheticId(input)).isLessThan(0L)
        }
    }

    @Test
    fun syntheticId_isStable() {
        val a = stableNegativeSyntheticId("artist_alpha")
        val b = stableNegativeSyntheticId("artist_alpha")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun syntheticId_neverZero() {
        // The zero sentinel is reserved for "not synthesized yet"; the helper
        // must never collapse to 0L even if a pathological hash output
        // landed there.
        listOf("", " ", "0", "song_0", "x").forEach { input ->
            assertThat(stableNegativeSyntheticId(input)).isNotEqualTo(0L)
        }
    }

    @Test
    fun syntheticId_lowCollisionRateAcrossLargeCorpus() {
        // Generate 5000 distinct inputs and ensure the resulting synthetic
        // IDs are all unique. With 32-bit hashing this would be expected to
        // collide; with 64-bit FNV-1a we expect zero collisions on a
        // 5000-element sample of non-adversarial inputs.
        val ids = HashSet<Long>()
        repeat(5000) { i ->
            val input = "telegram_chat_-1001234567890_msg_$i"
            val id = stableNegativeSyntheticId(input)
            check(ids.add(id)) { "Collision detected for input #$i" }
        }
        assertThat(ids).hasSize(5000)
    }
}
