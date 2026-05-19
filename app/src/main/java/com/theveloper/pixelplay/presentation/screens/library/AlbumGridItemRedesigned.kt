package com.theveloper.pixelplay.presentation.screens.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.coroutines.flow.StateFlow

/**
 * Grid-style album item with extracted-palette gradient and selection-mode UI.
 * Extracted from `LibraryScreen.kt`.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelPlayDarkTheme.current
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 20.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.985f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = cardShape
                )
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(selectionScale)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box {
                Column(
                    modifier = Modifier.background(
                        color = gradientBaseColor,
                        shape = cardShape
                    )
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            targetSize = Size(256, 256),
                            modifier = Modifier
                                .aspectRatio(3f / 2f)
                                .fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(
                                modifier = Modifier
                                    .aspectRatio(3f / 2f)
                                    .fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(3f / 2f)
                                .background(
                                    remember(gradientBaseColor) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent, gradientBaseColor
                                            )
                                        )
                                    })
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onGradientColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSongCount(album.songCount), style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "✓",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
