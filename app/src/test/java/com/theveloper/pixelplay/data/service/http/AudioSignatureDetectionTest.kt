package com.theveloper.pixelplay.data.service.http

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the audio container signature detection extracted from
 * `MediaFileHttpServerService`. These functions decide what MIME type
 * the Cast server declares for each transcode candidate; getting them
 * wrong leads to receiver-side load failures (status 2103).
 */
class AudioSignatureDetectionTest {

    @Test
    fun parseId3PayloadOffset_returnsZeroForNonId3Buffer() {
        val raw = ByteArray(20) { 0xFF.toByte() }
        assertThat(AudioSignatureDetection.parseId3PayloadOffset(raw)).isEqualTo(0)
    }

    @Test
    fun parseId3PayloadOffset_returnsZeroForTooSmallBuffer() {
        assertThat(AudioSignatureDetection.parseId3PayloadOffset(ByteArray(0))).isEqualTo(0)
        assertThat(AudioSignatureDetection.parseId3PayloadOffset(ByteArray(5))).isEqualTo(0)
    }

    @Test
    fun parseId3PayloadOffset_returnsTagSizeForMinimalId3v2Header() {
        // Header: 'ID3', version 04 00, flags 00, size 0x00,0x00,0x00,0x0a (= 10 bytes)
        val payload = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x0A
        ) + ByteArray(20) // 20 bytes of "tag content + audio"
        val offset = AudioSignatureDetection.parseId3PayloadOffset(payload)
        // 10-byte header + 10-byte declared tag size = 20.
        assertThat(offset).isEqualTo(20)
    }

    @Test
    fun parseId3PayloadOffset_isClampedToBufferLength() {
        // Header declares an absurdly large tag size; offset must clamp to
        // the buffer length so callers can use it as a slice index safely.
        val payload = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00,
            0x7F, 0x7F, 0x7F, 0x7F  // max 28-bit syncsafe value
        ) + ByteArray(50)
        val offset = AudioSignatureDetection.parseId3PayloadOffset(payload)
        assertThat(offset).isEqualTo(payload.size)
    }

    @Test
    fun parseId3PayloadOffset_addsFooterWhenFlagSet() {
        // Flags byte has bit 4 (0x10) set → 10-byte footer.
        val payload = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00,
            0x10,  // footer flag set
            0x00, 0x00, 0x00, 0x0A
        ) + ByteArray(40)
        val offset = AudioSignatureDetection.parseId3PayloadOffset(payload)
        // 10 header + 10 declared tag + 10 footer = 30.
        assertThat(offset).isEqualTo(30)
    }

    @Test
    fun detectMimeAtOffset_recognizesFLAC() {
        val bytes = "fLaC".toByteArray() + ByteArray(8)
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/flac")
    }

    @Test
    fun detectMimeAtOffset_recognizesOgg() {
        val bytes = "OggS".toByteArray() + ByteArray(8)
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/ogg")
    }

    @Test
    fun detectMimeAtOffset_recognizesWAV() {
        // RIFF....WAVE
        val bytes = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()
        )
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/wav")
    }

    @Test
    fun detectMimeAtOffset_recognizesAIFF() {
        val bytes = byteArrayOf(
            'F'.code.toByte(), 'O'.code.toByte(), 'R'.code.toByte(), 'M'.code.toByte(),
            0, 0, 0, 0,
            'A'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()
        )
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/aiff")
    }

    @Test
    fun detectMimeAtOffset_recognizesMp4Ftyp() {
        // First 4 bytes are box size, next 4 are 'ftyp'.
        val bytes = byteArrayOf(
            0, 0, 0, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
        )
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/mp4")
    }

    @Test
    fun detectMimeAtOffset_recognizesAACAdif() {
        val bytes = "ADIF".toByteArray() + ByteArray(8)
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isEqualTo("audio/aac")
    }

    @Test
    fun detectMimeAtOffset_returnsNullForUnknownSignature() {
        val bytes = "GARBAGE___bytes".toByteArray()
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 0)).isNull()
    }

    @Test
    fun detectMimeAtOffset_returnsNullForBadOffset() {
        val bytes = "fLaC".toByteArray()
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, -1)).isNull()
        assertThat(AudioSignatureDetection.detectMimeAtOffset(bytes, 100)).isNull()
    }

    @Test
    fun detectFramedAudioMime_findsMpegLayer3SyncWord() {
        // MPEG audio sync: 11 bits set in the high half, then layer bits 01-11.
        // 0xFF 0xFB = MPEG-1 Layer III.
        val bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x44.toByte())
        assertThat(AudioSignatureDetection.detectFramedAudioMime(bytes, 0)).isEqualTo("audio/mpeg")
    }

    @Test
    fun detectFramedAudioMime_findsAdtsAacSyncWord() {
        // 0xFF 0xF1 = ADTS AAC with layer bits == 00 → audio/aac.
        val bytes = byteArrayOf(0x00, 0xFF.toByte(), 0xF1.toByte(), 0x40, 0x80.toByte(), 0x40)
        assertThat(AudioSignatureDetection.detectFramedAudioMime(bytes, 0)).isEqualTo("audio/aac")
    }

    @Test
    fun detectFramedAudioMime_returnsNullWithoutSyncWord() {
        val bytes = ByteArray(64) { 0x00 }
        assertThat(AudioSignatureDetection.detectFramedAudioMime(bytes, 0)).isNull()
    }

    @Test
    fun detectFramedAudioMime_returnsNullForTinyBuffer() {
        assertThat(AudioSignatureDetection.detectFramedAudioMime(ByteArray(0), 0)).isNull()
        assertThat(AudioSignatureDetection.detectFramedAudioMime(ByteArray(1), 0)).isNull()
    }

    @Test
    fun detectFramedAudioMime_handlesOutOfRangeStartOffset() {
        // Out-of-range start should be clamped, not crash.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00)
        assertThat(AudioSignatureDetection.detectFramedAudioMime(bytes, 1000)).isNull()
    }
}
