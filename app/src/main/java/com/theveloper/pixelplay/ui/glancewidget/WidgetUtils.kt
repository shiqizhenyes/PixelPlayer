package com.theveloper.pixelplay.ui.glancewidget

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.data.model.PlayerInfo

object AlbumArtBitmapCache {
    private const val CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4 MiB
    private val lruCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun getBitmap(key: String): Bitmap? = lruCache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (getBitmap(key) == null) {
            lruCache.put(key, bitmap)
        }
    }

    /**
     * Hash only the first 4 KiB of the byte array. byteArray.contentHashCode()
     * was O(n) on every widget render — a 100 KiB image cost 100k ops per
     * render — and a prefix hash is just as distinguishing in practice for
     * album artwork (different images share their first 4 KiB only on
     * intentional collisions).
     */
    fun getKey(byteArray: ByteArray): String {
        val prefixLength = byteArray.size.coerceAtMost(4096)
        var hash = 1
        for (i in 0 until prefixLength) {
            hash = 31 * hash + byteArray[i].toInt()
        }
        // Mix the total size in so different-length payloads with same prefix
        // map to different cache buckets.
        hash = 31 * hash + byteArray.size
        return hash.toString()
    }
}

data class WidgetColors(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val artist: ColorProvider,
    val playPauseBackground: ColorProvider,
    val playPauseIcon: ColorProvider,
    val prevNextBackground: ColorProvider,
    val prevNextIcon: ColorProvider
)

@Composable
fun PlayerInfo.getWidgetColors(): WidgetColors {
    val theme = this.themeColors
    
    return if (theme != null) {
        WidgetColors(
            surface = ColorProvider(
                day = Color(theme.lightSurfaceContainer),
                night = Color(theme.darkSurfaceContainer)
            ),
            onSurface = ColorProvider(
                day = Color(theme.lightTitle),
                night = Color(theme.darkTitle)
            ),
            artist = ColorProvider(
                day = Color(theme.lightArtist),
                night = Color(theme.darkArtist)
            ),
            playPauseBackground = ColorProvider(
                day = Color(theme.lightPlayPauseBackground),
                night = Color(theme.darkPlayPauseBackground)
            ),
            playPauseIcon = ColorProvider(
                day = Color(theme.lightPlayPauseIcon),
                night = Color(theme.darkPlayPauseIcon)
            ),
            prevNextBackground = ColorProvider(
                day = Color(theme.lightPrevNextBackground),
                night = Color(theme.darkPrevNextBackground)
            ),
            prevNextIcon = ColorProvider(
                day = Color(theme.lightPrevNextIcon),
                night = Color(theme.darkPrevNextIcon)
            )
        )
    } else {
        WidgetColors(
            surface = GlanceTheme.colors.surface,
            onSurface = GlanceTheme.colors.onSurface,
            artist = GlanceTheme.colors.onSurface,
            playPauseBackground = GlanceTheme.colors.primaryContainer,
            playPauseIcon = GlanceTheme.colors.onPrimaryContainer,
            prevNextBackground = GlanceTheme.colors.secondaryContainer,
            prevNextIcon = GlanceTheme.colors.onSecondaryContainer
        )
    }
}
