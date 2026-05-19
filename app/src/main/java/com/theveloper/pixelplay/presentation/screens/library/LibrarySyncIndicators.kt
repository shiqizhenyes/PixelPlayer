package com.theveloper.pixelplay.presentation.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.SyncProgressBar
import com.theveloper.pixelplay.presentation.screens.positiveMod

/**
 * Sync/loading indicator composables extracted from `LibraryScreen.kt` as
 * part of the file-decomposition refactor.
 *
 * All three collect [SyncManager.syncProgress] inside this subtree so the
 * surrounding screen doesn't recompose on every progress tick.
 */

@Composable
internal fun CompactLibraryPagerIndicator(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val safeIndex = positiveMod(currentIndex, pageCount)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == safeIndex
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 10.dp,
                label = "LibraryCompactPagerIndicatorWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                label = "LibraryCompactPagerIndicatorAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Slim, non-intrusive indicator for sync work that should not keep the list
 * pulled down: automatic startup syncs, background maintenance, and manual
 * refreshes after the short pull-to-refresh confirmation window. Collapses
 * to zero height when not active.
 *
 * Distinct from [LibrarySyncOverlay], which is reserved for initial
 * empty-library loads. The parent screen also gates this off while the
 * pull spinner is visible.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryInlineSyncIndicator(
    visible: Boolean,
    syncManager: SyncManager
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(180)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(160))
    ) {
        val syncProgress by syncManager.syncProgress
            .collectAsStateWithLifecycle(initialValue = SyncProgress())

        val phaseLabel = when (syncProgress.phase) {
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                stringResource(R.string.sync_scanning)
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                stringResource(R.string.sync_processing)
            SyncProgress.SyncPhase.SCANNING_LRC ->
                stringResource(R.string.library_background_sync_lyrics)
            SyncProgress.SyncPhase.CLEANING_CACHE ->
                stringResource(R.string.library_background_sync_cache)
            SyncProgress.SyncPhase.SYNCING_CLOUD ->
                stringResource(R.string.library_background_sync_cloud)
            else ->
                stringResource(R.string.sync_in_progress)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * Full-screen overlay shown during initial empty-library scans. By
 * collecting [SyncManager.syncProgress] HERE instead of in the parent
 * [LibraryScreen], only this small subtree recomposes on every progress
 * tick.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibrarySyncOverlay(syncManager: SyncManager) {
    val syncProgress by syncManager.syncProgress
        .collectAsStateWithLifecycle(initialValue = SyncProgress())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                if (syncProgress.hasProgress && syncProgress.isRunning) {
                    SyncProgressBar(
                        syncProgress = syncProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.syncing_library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
