package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MarqueeInitialDelayMillis = 1_500
private const val MarqueeFadeAnimationMillis = 180
private val MarqueeGap = 6.dp

@Composable
fun AutoScrollingTextOnDemand(
    text: String,
    style: TextStyle,
    gradientEdgeColor: Color,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val marqueeActive by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.99f }
    }

    AutoScrollingText(
        text = text,
        style = style,
        textAlign = TextAlign.Start,
        gradientEdgeColor = gradientEdgeColor,
        marqueeActive = marqueeActive,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 24.dp,
    marqueeActive: Boolean = true
) {
    var hasOverflow by remember(text, style) { mutableStateOf(false) }
    val shouldScroll = marqueeActive && hasOverflow

    var showStartFade by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text, shouldScroll) {
        showStartFade = false
        if (shouldScroll) {
            kotlinx.coroutines.delay(MarqueeInitialDelayMillis.toLong())
            showStartFade = true
        }
    }

    val startFadeAlpha by animateFloatAsState(
        targetValue = if (showStartFade) 1f else 0f,
        animationSpec = tween(durationMillis = MarqueeFadeAnimationMillis),
        label = "MarqueeStartFadeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (shouldScroll) {
                    Modifier.marqueeEdgeFade(
                        edgeColor = gradientEdgeColor,
                        edgeWidth = gradientWidth,
                        startFadeAlpha = startFadeAlpha
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val textModifier = if (shouldScroll) {
            Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                spacing = MarqueeSpacing(gradientWidth + MarqueeGap),
                velocity = 25.dp,
                initialDelayMillis = MarqueeInitialDelayMillis
            )
        } else {
            Modifier
        }

        Text(
            text = text,
            style = style,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = if (shouldScroll) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!shouldScroll) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
            modifier = textModifier
        )
    }
}

private fun Modifier.marqueeEdgeFade(
    edgeColor: Color,
    edgeWidth: Dp,
    startFadeAlpha: Float
): Modifier = drawWithContent {
    drawContent()

    val fadeWidth = minOf(edgeWidth.toPx(), size.width / 2f)
    if (fadeWidth <= 0f) return@drawWithContent

    if (startFadeAlpha > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    edgeColor.copy(alpha = edgeColor.alpha * startFadeAlpha),
                    edgeColor.copy(alpha = 0f)
                ),
                startX = 0f,
                endX = fadeWidth
            ),
            size = Size(fadeWidth, size.height)
        )
    }

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                edgeColor.copy(alpha = 0f),
                edgeColor
            ),
            startX = size.width - fadeWidth,
            endX = size.width
        ),
        topLeft = Offset(size.width - fadeWidth, 0f),
        size = Size(fadeWidth, size.height)
    )
}
