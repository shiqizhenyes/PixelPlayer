package com.theveloper.pixelplay.presentation.screens.library

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.service.wear.PhoneWatchTransferState
import com.theveloper.pixelplay.shared.WearTransferProgress

/**
 * Modal progress dialog for the "send to watch" Wear transfer flow.
 *
 * Extracted from `LibraryScreen.kt` as the first step of the 3.7k-line
 * decomposition: this dialog has no coupling to other Library screen
 * internals (it only consumes [PhoneWatchTransferState] and two callbacks),
 * so it can live in its own file without touching the surrounding screen
 * structure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WatchTransferProgressDialog(
    transfer: PhoneWatchTransferState,
    onDismiss: () -> Unit,
    onCancelTransfer: () -> Unit,
) {
    val context = LocalContext.current
    val startingTransfer = stringResource(R.string.presentation_batch_d_watch_starting_transfer)
    val preparingTransfer = stringResource(R.string.presentation_batch_d_watch_preparing_transfer)
    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "WatchTransferProgressDialog"
    )
    val progressPercent = (animatedProgress * 100f).toInt().coerceIn(0, 100)
    val bytesText = if (transfer.totalBytes > 0L) {
        val sent = Formatter.formatFileSize(context, transfer.bytesTransferred)
        val total = Formatter.formatFileSize(context, transfer.totalBytes)
        stringResource(R.string.presentation_batch_h_transfer_bytes_progress, sent, total)
    } else {
        startingTransfer
    }
    val statusText = when (transfer.status) {
        WearTransferProgress.STATUS_TRANSFERRING -> stringResource(R.string.presentation_batch_d_watch_status_transferring)
        WearTransferProgress.STATUS_COMPLETED -> stringResource(R.string.presentation_batch_d_watch_status_completed)
        WearTransferProgress.STATUS_FAILED -> stringResource(R.string.presentation_batch_d_watch_status_failed)
        WearTransferProgress.STATUS_CANCELLED -> stringResource(R.string.presentation_batch_d_watch_status_cancelled)
        else -> stringResource(R.string.presentation_batch_d_watch_status_preparing)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_d_watch_sending_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.84f),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_sync_percent, progressPercent),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.4f
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = transfer.songTitle.ifBlank { preparingTransfer },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.presentation_batch_f_status_bullet_step, statusText, bytesText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                transfer.error?.takeIf { it.isNotBlank() }?.let { errorText ->
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = onCancelTransfer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_d_watch_cancel_transfer),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
