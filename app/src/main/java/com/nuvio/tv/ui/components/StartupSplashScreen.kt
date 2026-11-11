package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.LocalUiMotion
import com.nuvio.tv.ui.theme.NuvioTheme
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
    var brandRevealed by remember { mutableStateOf(false) }
    var lightSweepStarted by remember { mutableStateOf(false) }
    LaunchedEffect(motion.allowsSpatialEffects) {
        brandRevealed = true
        if (motion.allowsSpatialEffects) {
            delay(120L)
            lightSweepStarted = true
        }
    }
    val brandReveal by animateFloatAsState(
        targetValue = if (brandRevealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.durationMillis(760),
            easing = FastOutSlowInEasing,
        ),
        label = "telumiaTvLaunchBrandReveal",
    )
    val lightSweepProgress by animateFloatAsState(
        targetValue = if (lightSweepStarted && motion.allowsSpatialEffects) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.durationMillis(820),
            easing = FastOutSlowInEasing,
        ),
        label = "telumiaTvLaunchLightSweep",
    )
    val avatarColor = remember(profileColorHex) {
        profileColorHex?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: Color(0xFF1E88E5)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val imageData = startupBackgroundData(profileBackgroundUrl)
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
            val baseBg = Color(0xFF050608)
            val baseBgElevated = Color(0xFF0C0F13)
            val gradientTop = lerp(baseBgElevated, avatarColor, 0.035f)
            val gradientMid = lerp(baseBg, avatarColor, 0.012f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gradientTop,
                                0.42f to gradientMid,
                                1f to baseBg
                            )
                        )
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(avatarColor.copy(alpha = 0.045f), Color.Transparent),
                            radius = 900f,
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(88.dp).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 128.dp)
                        .graphicsLayer { alpha = brandReveal * 0.42f }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFA55A).copy(alpha = 0.40f), Color.Transparent),
                            ),
                        ),
                )
                BrandWordmark(
                    modifier = Modifier
                        .height(56.dp)
                        .graphicsLayer {
                            alpha = brandReveal
                            val scale = 0.9f + brandReveal * 0.1f
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - brandReveal) * 16.dp.toPx()
                        },
                    contentDescription = stringResource(R.string.cd_nuvio_logo),
                    drawableOverride = brandWordmarkRes,
                )
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 84.dp)
                        .graphicsLayer {
                            val travel = size.width + 220.dp.toPx()
                            translationX = (lightSweepProgress * 2f - 1f) * travel
                            rotationZ = 12f
                            alpha = if (lightSweepStarted && motion.allowsSpatialEffects) {
                                brandReveal * (1f - lightSweepProgress).coerceIn(0f, 1f) * 0.88f
                            } else {
                                0f
                            }
                        }
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.2f to Color.Transparent,
                                    0.42f to Color.White.copy(alpha = 0.34f),
                                    0.5f to Color(0xFFFFC27A).copy(alpha = 0.78f),
                                    0.58f to Color.White.copy(alpha = 0.58f),
                                    0.8f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxl))
            LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xxxl))
        }
    }
}

internal fun startupBackgroundData(url: String): Any =
    if (url.startsWith("file:")) {
        runCatching { java.io.File(java.net.URI(url)) }.getOrElse { url }
    } else {
        url
    }
