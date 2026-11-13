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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.sports.GameBroadcast
import com.nuvio.tv.ui.components.NuvioDialog
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

private sealed interface GamesFilter {
    data object All : GamesFilter
    data object OnlyLive : GamesFilter
    data object InGuide : GamesFilter
    data class Group(val group: SportsGroup) : GamesFilter
}

/** Today's games: a card per match, grouped by league, with a direct "watch" action when the guide has it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvGamesView(
    games: List<SportsGame>,
    broadcasts: Map<String, List<GameBroadcast>>,
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
    var selectedGame by remember { mutableStateOf<SportsGame?>(null) }
    val channelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(selectedGame) {
        if (selectedGame != null) channelFocusRequester.requestFocus()
    }
    val liveCount = games.count { it.state == GameState.LIVE }
    val groups = remember(games) { SportsGroup.values().filter { g -> games.any { it.league.group == g } } }
    val shown = remember(games, filter, broadcasts) {
        games.filter { game ->
            when (val f = filter) {
                GamesFilter.All -> true
                GamesFilter.OnlyLive -> game.state == GameState.LIVE
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
            Text(
                dateText.replaceFirstChar { it.titlecase() } + "  |  " + stringResource(R.string.live_tv_games_count, games.size),
                modifier = Modifier.weight(1f),
                style = scoreboard(30, 1.5f),
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 1,
            )
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
            if (liveCount > 0) item { LiveTvChip(stringResource(R.string.live_tv_games_live), filter == GamesFilter.OnlyLive) { filter = GamesFilter.OnlyLive } }
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
            val featured = if (filter == GamesFilter.All || filter == GamesFilter.OnlyLive)
                shown.firstOrNull { it.state == GameState.LIVE } ?: shown.firstOrNull { it.state == GameState.UPCOMING }
            else null
            featured?.let { game ->
                item(key = "featured") {
                    GameCard(game, broadcasts[game.id].orEmpty(), timeFormat, { selectedGame = game }, featured = true, modifier = Modifier.fillMaxWidth())
                }
            }
            items(sections, key = { it.first.slug }) { (league, leagueGames) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(NuvioTheme.colors.Secondary))
                        Text(league.name.uppercase(), style = scoreboard(30, 1.5f))
                        Text("${league.sport} • ${leagueGames.size}", style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(leagueGames, key = { it.id }) { game ->
                            GameCard(game, broadcasts[game.id].orEmpty(), timeFormat, { selectedGame = game }, modifier = Modifier.width(330.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    selectedGame?.let { game ->
        NuvioDialog(
            onDismiss = { selectedGame = null },
            title = "${game.away.name} × ${game.home.name}",
            subtitle = stringResource(R.string.live_tv_games_choose_channel),
            suppressFirstKeyUp = false,
        ) {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(broadcasts[game.id].orEmpty(), key = { it.channel.id }) { broadcast ->
                    Button(onClick = {
                        selectedGame = null
                        onWatch(broadcast.channel)
                    }, modifier = Modifier.fillMaxWidth().then(
                        if (broadcast.channel.id == broadcasts[game.id]?.firstOrNull()?.channel?.id)
                            Modifier.focusRequester(channelFocusRequester) else Modifier
                    )) {
                        Text("${broadcast.channel.displayName} • ${broadcast.channel.addonDisplayName ?: broadcast.channel.groupName ?: broadcast.channel.sourceId}")
                    }
                }
            }
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
    broadcasts: List<GameBroadcast>,
    timeFormat: DateTimeFormatter,
    onChoose: () -> Unit,
    featured: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (featured) 28.dp else 22.dp)
    val awayColor = remember(game.away.colorHex) { parseTeamColor(game.away.colorHex) }
    val homeColor = remember(game.home.colorHex) { parseTeamColor(game.home.colorHex) }
    val crest = if (featured) 84.dp else 60.dp
    Surface(
        onClick = { if (broadcasts.isNotEmpty()) onChoose() },
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, NuvioTheme.focusRing.solidColor), shape = shape),
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().background(Color(0xFF10161B))
                .background(
                    Brush.horizontalGradient(
                        listOf(awayColor.copy(alpha = if (featured) 0.55f else 0.34f), Color.Transparent, homeColor.copy(alpha = if (featured) 0.55f else 0.34f))
                    )
                )
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
                .padding(if (featured) 18.dp else 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (featured) 8.dp else 10.dp)) {
                StatusRow(game, featured)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Crest(game.away, crest, Modifier.weight(1f), featured)
                    Scoreboard(game, timeFormat, featured)
                    Crest(game.home, crest, Modifier.weight(1f), featured)
                }
                when {
                    broadcasts.isNotEmpty() -> Box(
                        Modifier.clip(RoundedCornerShape(50)).background(OnAirGreen)
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (broadcasts.size == 1) stringResource(R.string.live_tv_games_watch, broadcasts.first().channel.displayName)
                            else stringResource(R.string.live_tv_games_channels_count, broadcasts.size),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = Color(0xFF04110B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    game.broadcasts.isNotEmpty() -> Text(
                        game.broadcasts.take(3).joinToString("  |  "),
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
private fun StatusRow(game: SportsGame, featured: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        when (game.state) {
            GameState.LIVE -> {
                val pulse = if (com.nuvio.tv.ui.theme.LocalUiMotion.current.allowsSpatialEffects) {
                    val animated by rememberInfiniteTransition(label = "live").animateFloat(
                        initialValue = 1f, targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
                    )
                    animated
                } else 1f
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(LiveRed).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).alpha(pulse).clip(CircleShape).background(Color.White))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.live_tv_games_live_label), style = scoreboard(if (featured) 24 else 18, 1f), color = Color.White)
                    }
                }
                game.statusText.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = scoreboard(if (featured) 26 else 20, 1f), color = LiveRed)
                }
            }
            GameState.UPCOMING -> Text(
                game.league.name.uppercase(), style = scoreboard(if (featured) 22 else 17, 1.5f),
                color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            GameState.FINISHED -> Text(
                stringResource(R.string.live_tv_games_finished).uppercase(), style = scoreboard(if (featured) 22 else 17, 1.5f),
                color = NuvioTheme.colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Crest(team: SportsTeam, size: androidx.compose.ui.unit.Dp, modifier: Modifier, featured: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (team.logoUrl != null) AsyncImage(
                model = team.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            ) else Text(team.abbreviation.take(3), style = scoreboard(28), color = NuvioTheme.colors.TextSecondary)
        }
        Text(
            team.name,
            style = if (featured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTheme.colors.TextPrimary,
            // Two lines reserved so every card has the same height and the score never drifts.
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Centre of the card: the score once the game has started, the kick-off time before it. */
@Composable
private fun Scoreboard(game: SportsGame, timeFormat: DateTimeFormatter, featured: Boolean) {
    val big = if (featured) 70 else 50
    if (game.state == GameState.UPCOMING) {
        Text(
            timeFormat.format(Instant.ofEpochMilli(game.startMillis)),
            style = scoreboard(if (featured) 60 else 42, 1f), color = NuvioTheme.colors.Secondary,
            modifier = Modifier.padding(horizontal = if (featured) 28.dp else 12.dp),
        )
    } else {
        val dim = NuvioTheme.colors.TextSecondary
        val finished = game.state == GameState.FINISHED
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (featured) 28.dp else 8.dp)) {
            Text(game.away.score ?: "-", style = scoreboard(big, 1f), color = if (finished && game.home.isWinner) dim else Color.White)
            Text(" : ", style = scoreboard(big, 1f), color = dim)
            Text(game.home.score ?: "-", style = scoreboard(big, 1f), color = if (finished && game.away.isWinner) dim else Color.White)
        }
    }
}

private fun parseTeamColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color(0xFF3A4651))
