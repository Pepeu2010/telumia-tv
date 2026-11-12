package com.nuvio.tv.ui.screens.livetv

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.sports.GameBroadcast
import com.nuvio.tv.core.sports.GameState
import com.nuvio.tv.core.sports.SportsGame
import com.nuvio.tv.core.sports.SportsGroup
import com.nuvio.tv.core.sports.SportsRepository
import com.nuvio.tv.core.sports.SportsTeam
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val Live = Color(0xFFFF4D4F)

private sealed interface GamesFilter {
    data object All : GamesFilter
    data object Live : GamesFilter
    data object InGuide : GamesFilter
    data class Group(val group: SportsGroup) : GamesFilter
}

/** Today's games: a card per match, grouped by league, with a direct "watch" action when the guide has it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvGamesView(
    games: List<SportsGame>,
    broadcasts: Map<String, GameBroadcast>,
    zone: ZoneId,
    loading: Boolean,
    failed: Boolean,
    updatedMillis: Long,
    dayOffset: Int,
    onDayChange: (Int) -> Unit,
    onWatch: (LiveTvChannel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
    val dateText = remember(zone, dayOffset) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(zone)
            .format(Instant.now().plusSeconds(dayOffset * 86_400L))
    }
    var filter by remember { mutableStateOf<GamesFilter>(GamesFilter.All) }
    val liveCount = games.count { it.state == GameState.LIVE }
    val groups = remember(games) { SportsGroup.values().filter { g -> games.any { it.league.group == g } } }
    val shown = remember(games, filter, broadcasts) {
        games.filter { game ->
            when (val f = filter) {
                GamesFilter.All -> true
                GamesFilter.Live -> game.state == GameState.LIVE
                GamesFilter.InGuide -> broadcasts.containsKey(game.id)
                is GamesFilter.Group -> game.league.group == f.group
            }
        }
    }
    val sections = remember(shown) {
        shown.groupBy { it.league }.entries
            .sortedWith(compareBy({ entry -> entry.value.none { it.state == GameState.LIVE } }, { it.key.group.ordinal }))
            .map { it.key to it.value }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.live_tv_games_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(dateText.replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary)
            }
            if (liveCount > 0) GamesPill("● " + stringResource(R.string.live_tv_games_live_count, liveCount), Live)
            GamesPill(stringResource(R.string.live_tv_games_count, games.size), NuvioTheme.colors.TextSecondary)
            LiveTvChip(
                if (loading) stringResource(R.string.live_tv_games_updating)
                else stringResource(R.string.live_tv_games_refresh, if (updatedMillis > 0) timeFormat.format(Instant.ofEpochMilli(updatedMillis)) else "--:--"),
                false,
                onRefresh,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { LiveTvChip(stringResource(R.string.live_tv_games_yesterday), dayOffset == -1) { onDayChange(-1) } }
            item { LiveTvChip(stringResource(R.string.live_tv_games_today), dayOffset == 0) { onDayChange(0) } }
            item { LiveTvChip(stringResource(R.string.live_tv_games_tomorrow), dayOffset == 1) { onDayChange(1) } }
            item { Spacer(Modifier.width(18.dp)) }
            item { LiveTvChip(stringResource(R.string.live_tv_games_all), filter == GamesFilter.All) { filter = GamesFilter.All } }
            if (liveCount > 0) item { LiveTvChip(stringResource(R.string.live_tv_games_live), filter == GamesFilter.Live) { filter = GamesFilter.Live } }
            if (broadcasts.isNotEmpty()) item { LiveTvChip(stringResource(R.string.live_tv_games_in_guide), filter == GamesFilter.InGuide) { filter = GamesFilter.InGuide } }
            items(groups, key = { it.name }) { group ->
                LiveTvChip(group.label, filter == GamesFilter.Group(group)) { filter = GamesFilter.Group(group) }
            }
        }

        if (games.isEmpty() && !loading) {
            LiveTvEmptyHero(
                stringResource(if (failed) R.string.live_tv_games_failed_title else R.string.live_tv_games_empty_title),
                stringResource(if (failed) R.string.live_tv_games_failed_body else R.string.live_tv_games_empty_body),
            )
        } else if (shown.isEmpty() && games.isNotEmpty()) {
            Text(stringResource(R.string.live_tv_games_filter_empty), color = NuvioTheme.colors.TextSecondary)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sections, key = { it.first.slug }) { (league, leagueGames) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(NuvioTheme.colors.Secondary))
                        Text(league.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${league.sport} • ${leagueGames.size}", style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(leagueGames, key = { it.id }) { game ->
                            GameCard(game, broadcasts[game.id], timeFormat, onWatch)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GamesPill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(
    game: SportsGame,
    broadcast: GameBroadcast?,
    timeFormat: DateTimeFormatter,
    onWatch: (LiveTvChannel) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val awayColor = remember(game.away.colorHex) { parseTeamColor(game.away.colorHex) }
    val homeColor = remember(game.home.colorHex) { parseTeamColor(game.home.colorHex) }
    Surface(
        onClick = { broadcast?.let { onWatch(it.channel) } },
        modifier = Modifier.width(340.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, NuvioTheme.focusRing.solidColor), shape = shape),
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().background(Color(0xFF12181D))
                .background(Brush.horizontalGradient(listOf(awayColor.copy(alpha = 0.30f), Color.Transparent, homeColor.copy(alpha = 0.30f))))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow(game, timeFormat)
                TeamRow(game.away, game.state)
                TeamRow(game.home, game.state)
                when {
                    broadcast != null -> Box(
                        Modifier.clip(RoundedCornerShape(50)).background(NuvioTheme.colors.Secondary)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            stringResource(R.string.live_tv_games_watch, broadcast.channel.displayName),
                            style = MaterialTheme.typography.labelLarge, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    game.broadcasts.isNotEmpty() -> Text(
                        game.broadcasts.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    else -> Text(
                        game.venue ?: " ",
                        style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(game: SportsGame, timeFormat: DateTimeFormatter) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (game.state) {
            GameState.LIVE -> {
                val pulse by rememberInfiniteTransition(label = "live").animateFloat(
                    initialValue = 1f, targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
                )
                Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(Live))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.live_tv_games_live_label) + game.statusText.takeIf(String::isNotBlank)?.let { "  $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Live, maxLines = 1,
                )
            }
            GameState.UPCOMING -> Text(
                timeFormat.format(Instant.ofEpochMilli(game.startMillis)) + game.statusText.takeIf(String::isNotBlank)?.let { "  $it" }.orEmpty(),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NuvioTheme.colors.Secondary,
            )
            GameState.FINISHED -> Text(
                stringResource(R.string.live_tv_games_finished),
                style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun TeamRow(team: SportsTeam, state: GameState) {
    val dim = state == GameState.FINISHED && !team.isWinner
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (team.logoUrl != null) AsyncImage(
                model = team.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            ) else Text(team.abbreviation.take(3), style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
        }
        Text(
            team.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Medium,
            color = if (dim) NuvioTheme.colors.TextSecondary else NuvioTheme.colors.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (state != GameState.UPCOMING) {
            Text(
                team.score ?: "-",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = if (dim) NuvioTheme.colors.TextSecondary else NuvioTheme.colors.TextPrimary,
            )
        }
    }
}

private fun parseTeamColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color(0xFF3A4651))
