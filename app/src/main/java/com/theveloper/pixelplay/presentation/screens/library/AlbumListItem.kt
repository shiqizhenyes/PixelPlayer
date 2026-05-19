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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.screens.search.components.GenreTypography
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.coroutines.flow.StateFlow

/**
 * Card-style list row for an album with extracted-palette gradient and
 * selection-mode UI. Extracted from `LibraryScreen.kt`.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun AlbumListItem(
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
    val cardCornerRadius = 16.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.99f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
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
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                    ) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            targetSize = Size(256, 256),
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(modifier = Modifier.fillMaxSize())
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            gradientBaseColor
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(gradientBaseColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            val variableTextStyle = remember(album.id, album.title) {
                                GenreTypography.getGenreStyle(album.id.toString(), album.title)
                            }

                            Text(
                                album.title,
                                style = variableTextStyle.copy(fontSize = 22.sp),
                                color = onGradientColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatSongCount(album.songCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
