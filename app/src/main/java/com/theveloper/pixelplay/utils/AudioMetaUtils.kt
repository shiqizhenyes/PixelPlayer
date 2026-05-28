package com.theveloper.pixelplay.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.theveloper.pixelplay.data.database.MusicDao
import timber.log.Timber
import java.io.File
import java.util.Locale

data class AudioMeta(
    val mimeType: String?,
    val bitrate: Int?,      // bits per second
    val sampleRate: Int?   // Hz
)

object AudioMetaUtils {

    /**
     * Returns audio metadata for a given file path.
     * Tries MediaMetadataRetriever first, then falls back to MediaExtractor.
     */
    suspend fun getAudioMetadata(musicDao: MusicDao, id: Long, filePath: String, deepScan: Boolean): AudioMeta {
        val cached = musicDao.getAudioMetadataById(id)
        if (!deepScan && cached != null &&
            cached.mimeType != null &&
            cached.bitrate != null &&
            cached.sampleRate != null
        )
            return cached

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return AudioMeta(null, null, null)

        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null

        // Try MediaMetadataRetriever via pool
        MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } catch (e: Exception) {
                Timber.tag("AudioMetaUtils").w(e, "Retriever failed for $filePath")
            }
        }

        // Fallback with MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            for (i in 0 until extractor.trackCount) {
                val format: MediaFormat = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME)
                if (trackMime?.startsWith("audio/") == true) {
                    mimeType = mimeType ?: trackMime
                    sampleRate = sampleRate ?: if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } else null
                    bitrate = bitrate ?: if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        format.getInteger(MediaFormat.KEY_BIT_RATE)
                    } else null
                    break
                }
            }
        } catch (e: Exception) {
            Timber.tag("AudioMetaUtils").w(e, "Extractor failed for $filePath")
        } finally {
            runCatching { extractor.release() }
        }

        return AudioMeta(mimeType, bitrate, sampleRate)

    }

    fun mimeTypeToFormat(mimeType: String?): String {
        val normalized = mimeType
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.substringBefore(';')
            ?: return "-"

        if (normalized.isBlank()) return "-"

        return when {
            normalized == "audio/mpeg" ||
                normalized == "audio/mp3" ||
                normalized == "audio/x-mp3" ||
                normalized == "audio/mpeg3" -> "mp3"

            normalized == "audio/flac" ||
                normalized == "audio/x-flac" -> "flac"

            normalized == "audio/wav" ||
                normalized == "audio/x-wav" ||
                normalized == "audio/wave" ||
                normalized == "audio/vnd.wave" -> "wav"

            normalized == "audio/ogg" ||
                normalized == "application/ogg" ||
                normalized == "audio/vorbis" ||
                normalized == "audio/x-vorbis" -> "ogg"

            normalized == "audio/opus" ||
                normalized == "audio/x-opus" -> "opus"

            normalized == "audio/mp4" ||
                normalized == "audio/m4a" ||
                normalized == "audio/x-m4a" ||
                normalized == "audio/mp4a-latm" -> "m4a"

            normalized == "audio/aac" ||
                normalized == "audio/aacp" -> "aac"

            normalized == "audio/amr" ||
                normalized == "audio/amr-wb" ||
                normalized == "audio/3gpp" -> "amr"

            normalized == "audio/evrc" ||
                normalized == "audio/x-evrc" -> "evrc"

            normalized == "audio/qcelp" ||
                normalized == "audio/x-qcelp" -> "qcelp"

            normalized == "audio/x-ima-adpcm" ||
                normalized == "audio/ima-adpcm" -> "ima"

            normalized == "audio/alac" ||
                normalized == "audio/x-alac" -> "alac"

            normalized == "audio/aiff" ||
                normalized == "audio/x-aiff" ||
                normalized == "audio/aif" ||
                normalized == "audio/x-aifc" -> "aiff"

            normalized == "audio/x-ms-wma" ||
                normalized == "audio/wma" -> "wma"

            normalized == "audio/ac3" ||
                normalized == "audio/eac3" ||
                normalized == "audio/eac3-joc" -> "ac3"

            normalized == "audio/vnd.dts" ||
                normalized == "audio/vnd.dts.hd" -> "dts"

            normalized == "audio/midi" ||
                normalized == "audio/x-midi" ||
                normalized == "audio/sp-midi" ||
                normalized == "audio/x-mid" -> "midi"

            normalized.contains("mp4a") -> "m4a"
            normalized.contains("flac") -> "flac"
            normalized.contains("opus") -> "opus"
            normalized.contains("vorbis") || normalized.contains("ogg") -> "ogg"
            normalized.contains("wav") || normalized.contains("wave") -> "wav"
            normalized.contains("aac") -> "aac"
            normalized.contains("mpeg") || normalized.contains("mp3") -> "mp3"
            normalized.contains("amr") -> "amr"
            normalized.contains("alac") -> "alac"
            normalized.contains("aiff") || normalized.contains("aif") -> "aiff"
            normalized.contains("wma") -> "wma"
            normalized.contains("dts") -> "dts"
            normalized.contains("eac3") || normalized.contains("ac3") -> "ac3"
            normalized.contains("midi") || normalized.contains("x-mid") -> "midi"
            normalized.startsWith("audio/") -> normalized.substringAfter("audio/").ifBlank { "-" }
            else -> "-"
        }
    }
}
