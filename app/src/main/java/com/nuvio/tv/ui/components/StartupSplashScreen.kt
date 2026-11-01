package com.nuvio.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.tv.ui.theme.accentHorizontalBrush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.LocalUiMotion
import kotlinx.coroutines.delay

@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    val accentColor = NuvioTheme.palette.accentGradient.lastOrNull() ?: NuvioTheme.colors.Secondary
    val avatarColor = remember(profileColorHex) {
        profileColorHex?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: Color(0xFF1E88E5)
    }
    var revealBrand by remember { mutableStateOf(false) }
    var revealLoader by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealBrand = true
        if (motion.allowsSpatialEffects) delay(150L)
        revealLoader = true
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val imageData: Any = if (profileBackgroundUrl.startsWith("file:")) {
                java.io.File(java.net.URI(profileBackgroundUrl))
            } else {
                profileBackgroundUrl
            }
            val request = ImageRequest.Builder(LocalContext.current)
                .data(imageData)
                .crossfade(false)
            if (backgroundCacheKey != null) {
                request.memoryCacheKey(backgroundCacheKey)
                    .diskCacheKey(backgroundCacheKey)
                    .placeholderMemoryCacheKey(backgroundCacheKey)
            }
            AsyncImage(
                model = request.build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!skipGradient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val baseBg = Color(0xFF121212)
                        val gradientTop = androidx.compose.ui.graphics.lerp(Color(0xFF1E1E1E), avatarColor, 0.3f)
                        val gradientMid = androidx.compose.ui.graphics.lerp(baseBg, avatarColor, 0.14f)
                        val vertical = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gradientTop,
                                0.42f to gradientMid,
                                1f to baseBg
                            )
                        )
                        val horizontal = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to avatarColor.copy(alpha = 0.22f),
                                0.45f to avatarColor.copy(alpha = 0.07f),
                                0.74f to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                        val glow = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.43f),
                            radius = size.minDimension * 0.72f
                        )
                        val vignette = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.32f)
                            )
                        )
                        onDrawBehind {
                            drawRect(baseBg)
                            drawRect(brush = vertical)
                            drawRect(brush = horizontal)
                            drawRect(brush = glow)
                            drawRect(brush = vignette)
                        }
                    }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = revealBrand,
                enter = fadeIn(tween(motion.durationMillis(560))) +
                    slideInVertically(
                        animationSpec = tween(motion.durationMillis(560), easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 12 }
                    ) + scaleIn(
                        animationSpec = tween(motion.durationMillis(560), easing = FastOutSlowInEasing),
                        initialScale = 0.96f
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandWordmark(
                        modifier = Modifier.height(54.dp),
                        contentDescription = stringResource(R.string.cd_nuvio_logo),
                        drawableOverride = brandWordmarkRes
                    )
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NuvioTheme.palette.accentHorizontalBrush())
                    )
                }
            }
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxl))
            AnimatedVisibility(
                visible = revealLoader,
                enter = fadeIn(tween(motion.durationMillis(320))) +
                    slideInVertically(
                        animationSpec = tween(motion.durationMillis(320), easing = FastOutSlowInEasing),
                        initialOffsetY = { -it / 6 }
                    )
            ) {
                LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xxxl))
            }
        }
    }
}
