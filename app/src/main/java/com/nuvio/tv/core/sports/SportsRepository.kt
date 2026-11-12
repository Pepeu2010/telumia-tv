package com.nuvio.tv.core.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class SportsGroup(val label: String) {
    BRAZIL("Brasil"),
    SOUTH_AMERICA("Copas da América"),
    EUROPE("Europa"),
    NATIONAL("Seleções"),
    USA("EUA"),
}

data class SportsLeague(val slug: String, val name: String, val group: SportsGroup, val sport: String = "Futebol")

enum class GameState { UPCOMING, LIVE, FINISHED }

data class SportsTeam(
    val name: String,
    val abbreviation: String,
    val logoUrl: String?,
    val colorHex: String?,
    val score: String?,
    val isWinner: Boolean,
)

data class SportsGame(
    val id: String,
    val league: SportsLeague,
    val startMillis: Long,
    val state: GameState,
    /** "67'", "Intervalo", "FT"... straight from the provider, already short. */
    val statusText: String,
    val home: SportsTeam,
    val away: SportsTeam,
    val venue: String?,
    val broadcasts: List<String>,
    val note: String?,
)

/**
 * Games of the day for the main leagues, from ESPN's public scoreboard (no key needed). One request per
 * league and UTC day; the games are then filtered to the viewer's local day.
 */
@Singleton
class SportsRepository @Inject constructor() {
    @Volatile private var cache: Map<Int, Pair<Long, List<SportsGame>>> = emptyMap()

    suspend fun forDay(zone: ZoneId, dayOffset: Int = 0, force: Boolean = false): List<SportsGame> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache[dayOffset]?.takeIf { !force && now - it.first < CACHE_MILLIS }?.let { return@withContext it.second }
        val localToday = LocalDate.now(zone).plusDays(dayOffset.toLong())
        val utcDays = setOf(
            localToday.atStartOfDay(zone).toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
            localToday.plusDays(1).atStartOfDay(zone).minusSeconds(1).toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
        )
        val semaphore = Semaphore(6)
        val games = coroutineScope {
            LEAGUES.flatMap { league -> utcDays.map { day -> league to day } }.map { (league, day) ->
                async { semaphore.withPermit { fetch(league, day) } }
            }.awaitAll().flatten()
        }
        val result = games.distinctBy { it.id }
            .filter { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() == localToday || (dayOffset == 0 && it.state == GameState.LIVE) }
            .sortedWith(compareBy<SportsGame>({ it.state != GameState.LIVE }, { it.startMillis }))
        cache = cache + (dayOffset to (now to result))
        result
    }

    private fun fetch(league: SportsLeague, day: LocalDate): List<SportsGame> = try {
        val url = "$BASE/${league.slug}/scoreboard?dates=${day.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Telumia")
        }
        try {
            // ESPN answers 400 for a day without a schedule in that league: that is simply "no games".
            if (connection.responseCode !in 200..299) emptyList()
            else parse(league, connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        emptyList()
    }

    internal fun parse(league: SportsLeague, body: String): List<SportsGame> {
        val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { index -> parseEvent(league, events.getJSONObject(index)) }
    }

    private fun parseEvent(league: SportsLeague, event: JSONObject): SportsGame? {
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val competitors = competition.optJSONArray("competitors") ?: return null
        var home: SportsTeam? = null
        var away: SportsTeam? = null
        for (i in 0 until competitors.length()) {
            val item = competitors.getJSONObject(i)
            val team = item.optJSONObject("team") ?: continue
            val parsed = SportsTeam(
                name = team.optString("displayName").ifBlank { team.optString("name") },
                abbreviation = team.optString("abbreviation"),
                logoUrl = team.optString("logo").takeIf(String::isNotBlank),
                colorHex = team.optString("color").takeIf { it.length == 6 },
                score = item.optString("score").takeIf(String::isNotBlank),
                isWinner = item.optBoolean("winner"),
            )
            if (item.optString("homeAway") == "away") away = parsed else home = parsed
        }
        if (home == null || away == null || home.name.isBlank() || away.name.isBlank()) return null
        val start = runCatching { Instant.parse(event.optString("date").let { if (it.length == 17) it.dropLast(1) + ":00Z" else it }) }
            .getOrNull()?.toEpochMilli() ?: return null
        val type = competition.optJSONObject("status")?.optJSONObject("type")
        val state = when (type?.optString("state")) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINISHED
            else -> GameState.UPCOMING
        }
        val statusName = type?.optString("name").orEmpty()
        val clock = competition.optJSONObject("status")?.optString("displayClock").orEmpty()
        val statusText = when {
            statusName.contains("HALFTIME") -> "Intervalo"
            statusName.contains("POSTPONED") -> "Adiado"
            statusName.contains("CANCEL") -> "Cancelado"
            statusName.contains("DELAY") -> "Atrasado"
            state == GameState.LIVE -> clock.ifBlank { type?.optString("shortDetail").orEmpty() }
            state == GameState.FINISHED -> "Encerrado"
            else -> ""
        }
        val broadcasts = LinkedHashSet<String>()
        competition.optJSONArray("broadcasts")?.let { array ->
            for (i in 0 until array.length()) array.getJSONObject(i).optJSONArray("names")?.let { names ->
                for (n in 0 until names.length()) names.optString(n).takeIf(String::isNotBlank)?.let(broadcasts::add)
            }
        }
        competition.optJSONArray("geoBroadcasts")?.let { array ->
            for (i in 0 until array.length()) array.getJSONObject(i).optJSONObject("media")?.optString("shortName")
                ?.takeIf(String::isNotBlank)?.let(broadcasts::add)
        }
        return SportsGame(
            id = "${league.slug}:${event.optString("id")}",
            league = league,
            startMillis = start,
            state = state,
            statusText = statusText,
            home = home,
            away = away,
            venue = competition.optJSONObject("venue")?.optString("fullName")?.takeIf(String::isNotBlank),
            broadcasts = broadcasts.toList(),
            note = competition.optJSONArray("notes")?.optJSONObject(0)?.optString("headline")?.takeIf(String::isNotBlank),
        )
    }

    companion object {
        private const val BASE = "https://site.api.espn.com/apis/site/v2/sports"
        private const val CACHE_MILLIS = 45_000L

        val LEAGUES = listOf(
            SportsLeague("soccer/bra.1", "Brasileirão Série A", SportsGroup.BRAZIL),
            SportsLeague("soccer/bra.2", "Brasileirão Série B", SportsGroup.BRAZIL),
            SportsLeague("soccer/bra.copa_do_brasil", "Copa do Brasil", SportsGroup.BRAZIL),
            SportsLeague("soccer/conmebol.libertadores", "Copa Libertadores", SportsGroup.SOUTH_AMERICA),
            SportsLeague("soccer/conmebol.sudamericana", "Copa Sul-Americana", SportsGroup.SOUTH_AMERICA),
            SportsLeague("soccer/arg.1", "Liga Argentina", SportsGroup.SOUTH_AMERICA),
            SportsLeague("soccer/uefa.champions", "Champions League", SportsGroup.EUROPE),
            SportsLeague("soccer/uefa.europa", "Europa League", SportsGroup.EUROPE),
            SportsLeague("soccer/uefa.europa.conf", "Conference League", SportsGroup.EUROPE),
            SportsLeague("soccer/eng.1", "Premier League", SportsGroup.EUROPE),
            SportsLeague("soccer/esp.1", "LaLiga", SportsGroup.EUROPE),
            SportsLeague("soccer/ita.1", "Serie A (Itália)", SportsGroup.EUROPE),
            SportsLeague("soccer/ger.1", "Bundesliga", SportsGroup.EUROPE),
            SportsLeague("soccer/fra.1", "Ligue 1", SportsGroup.EUROPE),
            SportsLeague("soccer/por.1", "Liga Portugal", SportsGroup.EUROPE),
            SportsLeague("soccer/fifa.world", "Copa do Mundo", SportsGroup.NATIONAL),
            SportsLeague("soccer/conmebol.worldq", "Eliminatórias Sul-Americanas", SportsGroup.NATIONAL),
            SportsLeague("soccer/uefa.worldq", "Eliminatórias Europeias", SportsGroup.NATIONAL),
            SportsLeague("soccer/uefa.nations", "Liga das Nações", SportsGroup.NATIONAL),
            SportsLeague("soccer/fifa.friendly", "Amistosos", SportsGroup.NATIONAL),
            SportsLeague("soccer/usa.1", "MLS", SportsGroup.USA),
            SportsLeague("basketball/nba", "NBA", SportsGroup.USA, "Basquete"),
            SportsLeague("football/nfl", "NFL", SportsGroup.USA, "Futebol americano"),
            SportsLeague("hockey/nhl", "NHL", SportsGroup.USA, "Hóquei"),
            SportsLeague("baseball/mlb", "MLB", SportsGroup.USA, "Beisebol"),
        )
    }
}
