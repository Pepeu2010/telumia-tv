package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.ui.theme.LocalUiMotion
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.ui.util.LocalRecompositionHighlighterEnabled
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.recompositionHighlighter
import coil3.request.transitionFactory
import com.nuvio.tv.R
import kotlinx.coroutines.delay
import com.nuvio.tv.ui.components.ImdbRatingSourceLabel
import com.nuvio.tv.ui.components.TrailerPlayer
import androidx.compose.ui.res.stringResource

@Composable
internal fun ModernHeroScene(
    state: () -> ModernHeroSceneState,
    isFullScreen: () -> Boolean,
    bgColor: Color,
    modifier: Modifier,
    requestWidthPx: Int,
    requestHeightPx: Int,
    onTrailerEnded: () -> Unit,
    onFirstFrameRendered: () -> Unit
) {
    ModernHeroMediaLayer(
        heroBackdrop = { state().heroBackdrop },
        enrichmentActive = { state().enrichmentActive },
        shouldPlayHeroTrailer = { state().shouldPlayTrailer },
        heroTrailerFirstFrameRendered = { state().trailerFirstFrameRendered },
        heroTrailerUrl = { state().trailerUrl },
        heroTrailerAudioUrl = { state().trailerAudioUrl },
        heroTrailerPlaybackKey = { state().trailerPlaybackKey },
        muted = { state().trailerMuted },
        onTrailerEnded = onTrailerEnded,
        onFirstFrameRendered = onFirstFrameRendered,
        modifier = modifier,
        requestWidthPx = requestWidthPx,
        requestHeightPx = requestHeightPx
    )
    val isTrailerPlayingFullScreen = {
        val s = state()
        s.fullScreenBackdrop && s.shouldPlayTrailer && s.trailerFirstFrameRendered
    }
    ModernHeroGradientLayer(
        bgColor = bgColor,
        isFullScreen = isFullScreen,
        isTrailerPlayingFullScreen = isTrailerPlayingFullScreen,
        modifier = modifier
    )
}

@Composable
internal fun ModernHeroMediaLayer(
    heroBackdrop: () -> String?,
    enrichmentActive: () -> Boolean,
    shouldPlayHeroTrailer: () -> Boolean,
    heroTrailerFirstFrameRendered: () -> Boolean,
    heroTrailerUrl: () -> String?,
    heroTrailerAudioUrl: () -> String?,
    heroTrailerPlaybackKey: () -> String?,
    muted: () -> Boolean,
    onTrailerEnded: () -> Unit,
    onFirstFrameRendered: () -> Unit,
    modifier: Modifier,
    requestWidthPx: Int,
    requestHeightPx: Int
) {
    val shouldPlay by remember { derivedStateOf { shouldPlayHeroTrailer() } }
    val trailerRendered by remember { derivedStateOf { heroTrailerFirstFrameRendered() } }
    val motion = LocalUiMotion.current
    val transitionProgressState = animateFloatAsState(
        targetValue = if (shouldPlay && trailerRendered) 1f else 0f,
        animationSpec = tween(durationMillis = motion.durationMillis(480)),
        label = "heroBackdropTrailerCrossfadeProgress"
    )
    val localContext = LocalContext.current

    // Backdrop URL is managed upstream (heroSceneStateLambda freezes it
    // during rapid nav / scroll). Only update when enrichment is not active
    val rawBackdrop by remember { derivedStateOf { heroBackdrop() } }
    val enriching by remember { derivedStateOf { enrichmentActive() } }
    var displayedBackdrop by remember { mutableStateOf(HeroBackdropState.lastDisplayedUrl ?: heroBackdrop()) }
    if (rawBackdrop != null && rawBackdrop != displayedBackdrop && !enriching) {
        displayedBackdrop = rawBackdrop!!
    }
    val imageModel = remember(
        localContext,
        displayedBackdrop,
        requestWidthPx,
        requestHeightPx
    ) {
        displayedBackdrop?.let {
            ImageRequest.Builder(localContext)
                .data(it)
                .size(width = requestWidthPx, height = requestHeightPx)
                .build()
        }
    }
    // Keep HeroBackdropState in sync for navigation transitions.
    LaunchedEffect(displayedBackdrop) {
        displayedBackdrop?.let { HeroBackdropState.update(it) }
    }

    Box(modifier = modifier) {
        ModernHeroFallback(Modifier.fillMaxSize())
        androidx.compose.animation.Crossfade(
            targetState = imageModel,
            animationSpec = tween(durationMillis = motion.durationMillis(NuvioMotion.tokens.durations.overlay)),
            label = "heroBackdropCrossfade"
        ) { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = 1f - transitionProgressState.value
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd
            )
        }
        if (shouldPlay) {
            val trailerUrlVal = heroTrailerUrl()
            val playbackKeyVal = heroTrailerPlaybackKey()
            val audioUrlVal = heroTrailerAudioUrl()
            val mutedVal = muted()
            key(playbackKeyVal ?: trailerUrlVal) {
                TrailerPlayer(
                    trailerUrl = trailerUrlVal,
                    trailerAudioUrl = audioUrlVal,
                    isPlaying = true,
                    onEnded = onTrailerEnded,
                    onFirstFrameRendered = onFirstFrameRendered,
                    muted = mutedVal,
                    cropToFill = true,
                    overscanZoom = MODERN_TRAILER_OVERSCAN_ZOOM,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = transitionProgressState.value
                        }
                )
            }
        }
        HomePreviewPlaybackBadge(
            visible = shouldPlay && trailerRendered,
            muted = muted(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 32.dp),
        )
    }
}

/** Lightweight first frame shown before the network artwork is ready. */
@Composable
private fun ModernHeroFallback(modifier: Modifier = Modifier) {
    val colors = NuvioTheme.colors
    val primary = NuvioTheme.palette.secondaryVariant
    val secondary = NuvioTheme.palette.secondary
    Box(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to colors.Background,
                        0.40f to colors.Surface,
                        0.76f to colors.SurfaceVariant.copy(alpha = 0.92f),
                        1f to colors.Background,
                    )
                )
            )
            .drawWithCache {
                val ambient = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.24f),
                    radius = maxOf(size.width, size.height) * 0.62f,
                )
                val warmEdge = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.40f, size.height * 0.90f),
                    radius = maxOf(size.width, size.height) * 0.48f,
                )
                val rayColor = colors.TextPrimary.copy(alpha = 0.035f)
                onDrawBehind {
                    drawRect(ambient)
                    drawRect(warmEdge)
                    repeat(4) { index ->
                        val x = size.width * (0.47f + index * 0.075f)
                        drawLine(
                            color = rayColor,
                            start = Offset(x, 0f),
                            end = Offset(x + size.width * 0.20f, size.height),
                            strokeWidth = size.width * 0.018f,
                        )
                    }
                }
            }
    )
}

@Composable
internal fun ModernHeroGradientLayer(
    bgColor: Color,
    isFullScreen: () -> Boolean,
    isTrailerPlayingFullScreen: () -> Boolean = { false },
    modifier: Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val accentColor = NuvioTheme.palette.secondary
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = if (isTrailerPlayingFullScreen()) 0f else 1f
            }
            .drawWithCache {
                val fullScreen = isFullScreen()
                val horizontalFadeEndX = size.width * if (fullScreen) 0.65f else 0.56f
                val colorStops = if (fullScreen) {
                    arrayOf(
                        0.0f to bgColor,
                        0.22f to bgColor.copy(alpha = 0.90f),
                        0.46f to bgColor.copy(alpha = 0.80f),
                        0.76f to bgColor.copy(alpha = 0.42f),
                        1.0f to Color.Transparent
                    )
                } else {
                    arrayOf(
                        0.0f to bgColor,
                        0.22f to bgColor.copy(alpha = 0.90f),
                        0.46f to bgColor.copy(alpha = 0.70f),
                        0.76f to bgColor.copy(alpha = 0.24f),
                        1.0f to Color.Transparent
                    )
                }
                val horizontalGradient = if (isRtl) {
                    Brush.horizontalGradient(
                        colorStops = colorStops,
                        startX = size.width,
                        endX = size.width - horizontalFadeEndX
                    )
                } else {
                    Brush.horizontalGradient(
                        colorStops = colorStops,
                        startX = 0f,
                        endX = horizontalFadeEndX
                    )
                }

                val bottomStripStartY = size.height * if (fullScreen) 0.64f else 0.82f
                val verticalGradient = Brush.verticalGradient(
                    colorStops = if (fullScreen) {
                        arrayOf(
                            0.0f to Color.Transparent,
                            0.30f to bgColor.copy(alpha = 0.35f),
                            0.60f to bgColor.copy(alpha = 0.75f),
                            1.0f to bgColor
                        )
                    } else {
                        arrayOf(
                            0.0f to Color.Transparent,
                            0.40f to bgColor.copy(alpha = 0.25f),
                            0.75f to bgColor.copy(alpha = 0.65f),
                            1.0f to bgColor
                        )
                    },
                    startY = bottomStripStartY,
                    endY = size.height
                )

                onDrawBehind {
                    val accentRadius = maxOf(size.width, size.height) * 0.72f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.13f), Color.Transparent),
                            center = Offset(size.width * if (isRtl) 0.24f else 0.78f, size.height * 0.22f),
                            radius = accentRadius
                        )
                    )
                    // 1. Horizontal fade (reversed in RTL)
                    val rectLeft = if (isRtl) size.width - horizontalFadeEndX else 0f
                    drawRect(
                        brush = horizontalGradient,
                        topLeft = Offset(rectLeft, 0f),
                        size = Size(horizontalFadeEndX, size.height)
                    )
                    
                    // 2. Bottom vertical strip
                    drawRect(
                        brush = verticalGradient,
                        topLeft = Offset(0f, bottomStripStartY),
                        size = Size(size.width, size.height - bottomStripStartY)
                    )
                }
            }
    )
}

@Composable
internal fun HeroTitleBlock(
    previewProvider: () -> HeroPreview?,
    enrichmentActive: () -> Boolean = { false },
    portraitMode: Boolean,
    showImdbRatings: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    onPrimaryAction: (() -> Unit)? = null,
    trailerPlaying: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val currentPreview = previewProvider()
    val isEnriching = enrichmentActive()
    
    var stablePreview by remember { mutableStateOf<HeroPreview?>(null) }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(previewProvider(), enrichmentActive()) }.collect { (p, e) ->
            if (!e && p != null) {
                if (stablePreview != p) stablePreview = p
            } else if (e) {
                if (stablePreview != null) stablePreview = null
            }
        }
    }

    val displayPreview = if (!isEnriching && currentPreview != null) currentPreview else stablePreview
    if (displayPreview == null) return
    val heroMotion = NuvioTheme.motion
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        AnimatedContent(
            targetState = displayPreview,
            contentKey = { preview -> preview.title to preview.backdrop },
            transitionSpec = {
                (fadeIn(tween(heroMotion.durations.medium)) +
                    slideInVertically(
                        animationSpec = tween(heroMotion.durations.medium),
                        initialOffsetY = { it / 14 }
                    )).togetherWith(
                    fadeOut(tween(heroMotion.durations.fast)) +
                        slideOutVertically(
                            animationSpec = tween(heroMotion.durations.fast),
                            targetOffsetY = { -it / 16 }
                        )
                )
            },
            label = "telumiaHeroTitleTransition"
        ) { animatedPreview ->
            HeroTitleContent(
                previewProvider = { animatedPreview },
                portraitMode = portraitMode,
                showImdbRatings = showImdbRatings,
                mdbListShowOnHero = mdbListShowOnHero,
                mdbListRatingOrder = mdbListRatingOrder,
                onPrimaryAction = onPrimaryAction,
                trailerPlaying = trailerPlaying
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
internal fun HeroTitleContent(
    previewProvider: () -> HeroPreview?,
    portraitMode: Boolean,
    showImdbRatings: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    onPrimaryAction: (() -> Unit)? = null,
    trailerPlaying: () -> Boolean = { false }
) {
    val preview = previewProvider() ?: return
    val colors = NuvioTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val mobileLayout = configuration.screenWidthDp < 600
    val shortViewport = configuration.screenHeightDp < 480
    val compact = configuration.screenHeightDp < 600
    val motion = LocalUiMotion.current
    val metaAlpha by animateFloatAsState(
        targetValue = if (trailerPlaying()) 0.84f else 1f,
        animationSpec = tween(motion.durationMillis(180)), label = "telumiaHeroMetadata")
    val headline = MaterialTheme.typography.headlineLarge.copy(
        fontSize = if (shortViewport) 24.sp else if (mobileLayout) 28.sp else if (compact) 34.sp else 40.sp,
        lineHeight = if (shortViewport) 28.sp else if (mobileLayout) 33.sp else if (compact) 38.sp else 44.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
        textDirection = preview.title.contentTextDirection())
    val contextLine = remember(preview.contentTypeText, preview.yearText, preview.runtimeText, preview.genres, context) {
        buildList {
            preview.contentTypeText?.takeIf(String::isNotBlank)?.let(::add)
            preview.yearText?.takeIf(String::isNotBlank)?.let(::add)
            preview.runtimeText?.takeIf(String::isNotBlank)?.let(::add)
            preview.genres.firstOrNull()?.takeIf(String::isNotBlank)?.let {
                add(com.nuvio.tv.ui.util.localizedGenreLabel(context, it))
            }
        }.joinToString("  ·  ")
    }
    val status = when (preview.statusText?.trim()?.lowercase()) {
        "ended" -> stringResource(if (preview.isSeries) R.string.series_status_ended else R.string.movie_status_ended)
        "continuing", "returning series" -> stringResource(if (preview.isSeries) R.string.series_status_continuing else R.string.movie_status_continuing)
        "current" -> stringResource(if (preview.isSeries) R.string.series_status_current else R.string.movie_status_current)
        "cancelled", "canceled" -> stringResource(if (preview.isSeries) R.string.series_status_cancelled else R.string.movie_status_cancelled)
        "released" -> stringResource(if (preview.isSeries) R.string.series_status_released else R.string.movie_status_released)
        "planned" -> stringResource(if (preview.isSeries) R.string.series_status_planned else R.string.movie_status_planned)
        "rumored" -> stringResource(if (preview.isSeries) R.string.series_status_rumored else R.string.movie_status_rumored)
        "in production" -> stringResource(if (preview.isSeries) R.string.series_status_in_production else R.string.movie_status_in_production)
        "post production" -> stringResource(if (preview.isSeries) R.string.series_status_post_production else R.string.movie_status_post_production)
        else -> preview.statusText?.trim()?.takeIf(String::isNotBlank)
    }
    val secondary = listOfNotNull(preview.ageRatingText, status, preview.secondaryHighlightText,
        preview.languageText, preview.countryText).filter(String::isNotBlank).distinct()
    val imdb = preview.imdbText?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..10f }
    var logoError by remember(preview.title, preview.logo) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (mobileLayout || compact) 16.dp else 22.dp)
    val panelPadding = if (mobileLayout) 14.dp else 0.dp
    val logoHeight = if (shortViewport) 32.dp else if (mobileLayout) 64.dp else if (compact) 72.dp else 104.dp
    val logoRequest = remember(context, preview.logo, configuration.screenWidthDp, logoHeight, density, mobileLayout) {
        preview.logo?.let {
            val widthPx = with(density) {
                (configuration.screenWidthDp.dp * if (mobileLayout) 0.84f else 0.42f).roundToPx()
            }.coerceAtLeast(1)
            val heightPx = with(density) { logoHeight.roundToPx() }.coerceAtLeast(1)
            ImageRequest.Builder(context)
                .data(it)
                .size(width = widthPx, height = heightPx)
                .crossfade(false)
                .build()
        }
    }

    Column(Modifier.fillMaxWidth()
        .then(if (mobileLayout) Modifier.clip(shape)
            .background(colors.Background.copy(alpha = 0.58f)) else Modifier)
        .padding(panelPadding),
        verticalArrangement = Arrangement.spacedBy(if (shortViewport) 6.dp else if (mobileLayout || compact) 9.dp else 14.dp)) {
        if (!preview.logo.isNullOrBlank() && !logoError) {
            AsyncImage(model = logoRequest, contentDescription = preview.title,
                modifier = Modifier.height(logoHeight).fillMaxWidth(),
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                onError = { logoError = true })
        } else if (preview.title.isNotBlank()) {
            Text(preview.title, style = headline, color = colors.TextPrimary,
                maxLines = if (shortViewport) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.graphicsLayer { alpha = metaAlpha }
            .then(if (metaAlpha < 0.01f) Modifier.clearAndSetSemantics {} else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (shortViewport) 6.dp else if (compact) 8.dp else 12.dp)) {
            if (contextLine.isNotBlank()) {
                Text(contextLine, style = MaterialTheme.typography.labelMedium,
                    color = colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!shortViewport && (secondary.isNotEmpty() || (compact && showImdbRatings && (imdb != null || preview.mdbListRatings?.isEmpty() == false)))) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), maxLines = if (compact) 1 else 2) {
                    if (compact && showImdbRatings) {
                        if (mdbListShowOnHero && preview.mdbListRatings?.isEmpty() == false) {
                            com.nuvio.tv.ui.components.MDBListRatingsRow(ratings = preview.mdbListRatings!!,
                                maxItems = 3, order = mdbListRatingOrder)
                        } else if (imdb != null) {
                            HeroImdbMeta(preview.imdbText.orEmpty(), MaterialTheme.typography.labelSmall,
                                colors.TextSecondary, 14.dp, 4.dp)
                        }
                    }
                    (if (compact) secondary.take(2) else secondary).forEach { value ->
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.TextPrimary.copy(alpha = 0.08f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Text(value, style = MaterialTheme.typography.labelSmall,
                                color = colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (showImdbRatings && !compact) {
                if (mdbListShowOnHero && preview.mdbListRatings?.isEmpty() == false) {
                    com.nuvio.tv.ui.components.MDBListRatingsRow(ratings = preview.mdbListRatings!!,
                        maxItems = 3, order = mdbListRatingOrder)
                } else if (imdb != null) {
                    HeroImdbMeta(preview.imdbText.orEmpty(), MaterialTheme.typography.labelMedium,
                        colors.TextSecondary, 16.dp, 6.dp)
                }
            }
            preview.description?.takeIf { !shortViewport && it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = it.contentTextDirection()),
                    color = colors.TextSecondary, maxLines = if (shortViewport) 1 else 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        onPrimaryAction?.let { action ->
            Button(
                onClick = action,
                modifier = Modifier
                    .height(if (shortViewport) 40.dp else 48.dp)
                    .then(
                        if (mobileLayout) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.width(136.dp).align(Alignment.Start)
                        }
                    ),
                shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                colors = ButtonDefaults.colors(
                    containerColor = colors.TextPrimary,
                    contentColor = colors.Background,
                    focusedContainerColor = colors.Secondary,
                    focusedContentColor = colors.OnSecondary
                ),
                border = ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = androidx.tv.material3.Border(
                        border = BorderStroke(2.dp, colors.FocusRing),
                        shape = RoundedCornerShape(24.dp)
                    )
                )
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.cw_action_go_to_details),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
@Composable
private fun HeroImdbMeta(
    imdbText: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    textColor: Color,
    logoSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    visible: Boolean = true
) {
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        ImdbRatingSourceLabel(
            logoModifier = Modifier.size(logoSize),
            textStyle = textStyle,
            textColor = textColor
        )
        Text(
            text = imdbText,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
