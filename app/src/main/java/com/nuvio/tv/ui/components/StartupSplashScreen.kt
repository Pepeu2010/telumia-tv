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
    // The intro only starts once startup work has finished: while the main thread is busy the
    // animation would drop every frame and the user would only ever see its last one.
    introStarted: Boolean = true,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    // One value drives the whole "tudum": line of light -> halo -> wordmark -> beams -> loader.
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    // The clock never depends on the motion preference: the preference starts as OFF until it is
    // read from disk, and restarting the intro when it flips would replay or skip it. Only the
    // light layer is gated by the preference.
    LaunchedEffect(introStarted) {
        if (!introStarted) return@LaunchedEffect
        progress.animateTo(
            1f,
            tween(durationMillis = TUDUM_DURATION_MILLIS, easing = androidx.compose.animation.core.LinearEasing),
        )
    }
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
            modifier = Modifier
                .fillMaxSize()
                .then(if (motion.allowsSpatialEffects) Modifier.tudumLight({ progress.value }) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                contentAlignment = Alignment.Center,
            ) {
                BrandWordmark(
                    modifier = Modifier
                        .height(56.dp)
                        .graphicsLayer {
                            val p = progress.value
                            alpha = tudumBrandAlpha(p)
                            val scale = if (motion.allowsSpatialEffects) tudumBrandScale(p) else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                    contentDescription = stringResource(R.string.cd_nuvio_logo),
                    drawableOverride = brandWordmarkRes,
                )
            }
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxl))
            // The brand leads; the loader only appears once the intro has landed.
            LoadingIndicator(
                modifier = Modifier
                    .size(NuvioTheme.spacing.xxxl)
                    .graphicsLayer { alpha = tudumLoaderAlpha(progress.value) }
            )
        }
    }
}

internal fun startupBackgroundData(url: String): Any =
    if (url.startsWith("file:")) {
        runCatching { java.io.File(java.net.URI(url)) }.getOrElse { url }
    } else {
        url
    }
