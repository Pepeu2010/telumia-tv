package com.nuvio.tv.core.sports

import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import java.text.Normalizer

/** A channel that is showing (or will show) a game, found through the programme guide. */
data class GameBroadcast(val channel: LiveTvChannel, val program: LiveTvProgram)

object GameChannelMatcher {
    private val minuteMillis = 60_000L

    /**
     * Looks for a programme whose title or description names both teams and starts around the kick-off.
     * The guide is the only reliable link between a game and the channels the user actually has.
     */
    fun find(
        game: SportsGame,
        channels: List<LiveTvChannel>,
        programsByChannel: Map<String, List<LiveTvProgram>>,
    ): GameBroadcast? = findAll(game, channels, programsByChannel).firstOrNull()

    fun findAll(
        game: SportsGame,
        channels: List<LiveTvChannel>,
        programsByChannel: Map<String, List<LiveTvProgram>>,
    ): List<GameBroadcast> {
        val home = TeamKey.of(game.home.name)
        val away = TeamKey.of(game.away.name)
        if (home.tokens.isEmpty() || away.tokens.isEmpty()) return emptyList()
        val from = game.startMillis - 40 * minuteMillis
        val to = game.startMillis + 20 * minuteMillis
        val matches = mutableListOf<GameBroadcast>()
        for (channel in channels) {
            val programs = programsByChannel[channel.id] ?: continue
            var best: GameBroadcast? = null
            for (program in programs) {
                if (program.startEpochMillis !in from..to && !(game.state == GameState.LIVE &&
                        program.startEpochMillis <= System.currentTimeMillis() && program.endEpochMillis > System.currentTimeMillis())
                ) continue
                val text = " " + normalize(program.title + " " + program.description.orEmpty()) + " "
                if (home.matches(text) && away.matches(text)) {
                    // Prefer a programme whose title (not just the description) names the teams.
                    val inTitle = (" " + normalize(program.title) + " ").let { t -> home.matches(t) && away.matches(t) }
                    if (best == null || inTitle) best = GameBroadcast(channel, program)
                    if (inTitle) break
                }
            }
            best?.let(matches::add)
        }
        return matches
    }

    /** Words of a team name that identify it; short or generic-only names must match in full. */
    internal class TeamKey private constructor(val tokens: List<String>, private val needAll: Boolean) {
        fun matches(paddedText: String): Boolean {
            val hit = { token: String -> paddedText.contains(" $token ") }
            return if (needAll) tokens.all(hit) else tokens.any(hit)
        }

        companion object {
            private val generic = setOf("football", "futbol", "united", "city", "real", "sporting", "athletic", "atletico", "club")

            fun of(teamName: String): TeamKey {
                val words = normalize(teamName).split(' ').filter(String::isNotBlank)
                val strong = words.filter { it.length >= 5 && it !in generic }
                return if (strong.isNotEmpty()) TeamKey(strong, needAll = false)
                else TeamKey(words.filter { it.length >= 2 }, needAll = true)
            }
        }
    }

    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "")
        .lowercase()
        .replace(nonAlnum, " ")
        .trim()
}
