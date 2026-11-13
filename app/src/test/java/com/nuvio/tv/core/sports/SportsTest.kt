package com.nuvio.tv.core.sports

import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SportsTest {
    private val league = SportsLeague("soccer/bra.1", "Brasileirão Série A", SportsGroup.BRAZIL)

    private fun event(state: String, name: String = "STATUS_SCHEDULED", clock: String = "0'") = """
        {"events":[{"id":"1","date":"2026-11-14T21:30Z","competitions":[{
          "status":{"displayClock":"$clock","type":{"name":"$name","state":"$state"}},
          "venue":{"fullName":"Estadio X"},
          "broadcasts":[{"names":["ESPN","Disney+"]}],
          "competitors":[
            {"homeAway":"home","score":"2","winner":true,"team":{"displayName":"Mirassol","abbreviation":"MIR","logo":"http://l/m.png","color":"FEDC00"}},
            {"homeAway":"away","score":"1","winner":false,"team":{"displayName":"Atlético-MG","abbreviation":"CAM","logo":"http://l/a.png","color":"000000"}}
          ]}]}]}
    """.trimIndent()

    @Test fun parsesAnUpcomingGame() {
        val game = SportsRepository().parse(league, event("pre")).single()
        assertEquals(GameState.UPCOMING, game.state)
        assertEquals("Mirassol", game.home.name)
        assertEquals("Atlético-MG", game.away.name)
        assertEquals(listOf("ESPN", "Disney+"), game.broadcasts)
        assertEquals(1_794_691_800_000L, game.startMillis)
    }

    @Test fun parsesLiveAndFinishedStatus() {
        val live = SportsRepository().parse(league, event("in", clock = "67'")).single()
        assertEquals(GameState.LIVE, live.state); assertEquals("67'", live.statusText); assertEquals("2", live.home.score)
        val half = SportsRepository().parse(league, event("in", "STATUS_HALFTIME")).single()
        assertEquals("Intervalo", half.statusText)
        val done = SportsRepository().parse(league, event("post", "STATUS_FULL_TIME")).single()
        assertEquals(GameState.FINISHED, done.state); assertEquals(true, done.home.isWinner)
    }

    @Test fun ignoresEventsWithoutTwoTeams() {
        assertEquals(0, SportsRepository().parse(league, """{"events":[{"id":"9","date":"2026-11-14T21:30Z","competitions":[{"competitors":[]}]}]}""").size)
    }

    private fun channel(id: String) = LiveTvChannel(id = id, sourceId = "s", displayName = id)
    private fun program(channelId: String, title: String, start: Long) =
        LiveTvProgram("s", channelId, title, start, start + 7_200_000L)

    @Test fun findsTheChannelShowingTheGameFromTheGuide() {
        val game = SportsRepository().parse(league, event("pre")).single()
        val channels = listOf(channel("ESPN 2"), channel("Telecine"))
        val guide = mapOf(
            "ESPN 2" to listOf(program("ESPN 2", "Atlético-MG x Mirassol", game.startMillis - 10 * 60_000L)),
            "Telecine" to listOf(program("Telecine", "Filme qualquer", game.startMillis)),
        )
        assertEquals("ESPN 2", GameChannelMatcher.find(game, channels, guide)?.channel?.id)
    }

    @Test fun findsEveryChannelShowingTheSameGame() {
        val game = SportsRepository().parse(league, event("pre")).single()
        val channels = listOf(channel("addon-a"), channel("addon-b"))
        val guide = channels.associate { channel ->
            channel.id to listOf(program(channel.id, "Atlético-MG x Mirassol", game.startMillis))
        }
        assertEquals(listOf("addon-a", "addon-b"),
            GameChannelMatcher.findAll(game, channels, guide).map { it.channel.id })
    }

    @Test fun doesNotMatchAnotherAtleticoOrAnotherTime() {
        val game = SportsRepository().parse(league, event("pre")).single()
        val channels = listOf(channel("A"))
        val other = mapOf("A" to listOf(program("A", "Atlético de Madrid x Mirassol", game.startMillis)))
        assertNull(GameChannelMatcher.find(game, channels, other))
        val late = mapOf("A" to listOf(program("A", "Atlético-MG x Mirassol", game.startMillis + 5 * 3_600_000L)))
        assertNull(GameChannelMatcher.find(game, channels, late))
        assertNotNull(GameChannelMatcher.find(game, channels, mapOf("A" to listOf(program("A", "Mirassol x Atlético-MG", game.startMillis)))))
    }
}
