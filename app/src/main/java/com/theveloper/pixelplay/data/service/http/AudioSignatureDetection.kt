package com.theveloper.pixelplay.data.service.http

/**
 * Pure-function audio container signature detection. Extracted from
 * `MediaFileHttpServerService` so it can be unit-tested without bringing
 * up the full Ktor service. No Android dependencies.
 */
internal object AudioSignatureDetection {

    /**
     * If [bytes] starts with an ID3v2 header, return the byte offset
     * immediately after the tag (which is where the audio payload begins).
     * Returns 0 when no ID3 tag is present. The result is always clamped to
     * the buffer length so callers can pass it as a slice offset without a
     * range check.
     */
    fun parseId3PayloadOffset(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val flags = bytes[5].toInt() and 0xFF
        val hasFooter = (flags and 0x10) != 0
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val totalTagBytes = 10 + tagSize + if (hasFooter) 10 else 0
        return totalTagBytes.coerceIn(0, bytes.size)
    }

    /**
     * Match a container signature at [offset]. Returns the MIME type if a
     * known container header is present, or null otherwise. Recognised:
     * FLAC, Ogg, WAV, AIFF, MP4 (ftyp), AAC (ADIF).
     */
    fun detectMimeAtOffset(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= bytes.size) return null
        val remaining = bytes.size - offset
        if (remaining >= 4 &&
            bytes[offset] == 'f'.code.toByte() &&
            bytes[offset + 1] == 'L'.code.toByte() &&
            bytes[offset + 2] == 'a'.code.toByte() &&
            bytes[offset + 3] == 'C'.code.toByte()
        ) {
            return "audio/flac"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'O'.code.toByte() &&
            bytes[offset + 1] == 'g'.code.toByte() &&
            bytes[offset + 2] == 'g'.code.toByte() &&
            bytes[offset + 3] == 'S'.code.toByte()
        ) {
            return "audio/ogg"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'R'.code.toByte() &&
            bytes[offset + 1] == 'I'.code.toByte() &&
            bytes[offset + 2] == 'F'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte() &&
            bytes[offset + 8] == 'W'.code.toByte() &&
            bytes[offset + 9] == 'A'.code.toByte() &&
            bytes[offset + 10] == 'V'.code.toByte() &&
            bytes[offset + 11] == 'E'.code.toByte()
        ) {
            return "audio/wav"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'F'.code.toByte() &&
            bytes[offset + 1] == 'O'.code.toByte() &&
            bytes[offset + 2] == 'R'.code.toByte() &&
            bytes[offset + 3] == 'M'.code.toByte() &&
            bytes[offset + 8] == 'A'.code.toByte() &&
            bytes[offset + 9] == 'I'.code.toByte() &&
            bytes[offset + 10] == 'F'.code.toByte() &&
            bytes[offset + 11] == 'F'.code.toByte()
        ) {
            return "audio/aiff"
        }
        if (remaining >= 12 && offset + 8 <= bytes.size &&
            bytes[offset + 4] == 'f'.code.toByte() &&
            bytes[offset + 5] == 't'.code.toByte() &&
            bytes[offset + 6] == 'y'.code.toByte() &&
            bytes[offset + 7] == 'p'.code.toByte()
        ) {
            return "audio/mp4"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'A'.code.toByte() &&
            bytes[offset + 1] == 'D'.code.toByte() &&
            bytes[offset + 2] == 'I'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte()
        ) {
            return "audio/aac"
        }
        return null
    }

    /**
     * Scan for an MPEG/AAC framed sync word starting at [startOffset].
     * Differentiates MP3 (layer bits 1-3) from AAC (layer bits 0). Used as
     * a fallback when no container signature is found at the file head.
     */
    fun detectFramedAudioMime(bytes: ByteArray, startOffset: Int): String? {
        if (bytes.size < 2) return null
        val start = startOffset.coerceIn(0, bytes.lastIndex)
        for (index in start until bytes.size - 1) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = bytes[index + 1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) continue
            val layerBits = (b1 ushr 1) and 0x03
            if (layerBits == 0) return "audio/aac"
            if (layerBits in 1..3) return "audio/mpeg"
        }
        return null
    }
}
