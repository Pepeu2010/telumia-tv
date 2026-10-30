package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktEpisodeDto
import com.nuvio.tv.data.remote.dto.trakt.TraktSeasonDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.repository.MetaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response

class TraktEpisodeMappingCacheTest {
    @Test
    fun `resolved episode mapping is reused without repeating provider requests`() = runTest {
        val traktApi = mockk<TraktApi>()
        val auth = mockk<TraktAuthService>()
        val metaRepository = mockk<MetaRepository>()
        val contentId = "tt1234567"
        val addonMeta = seriesMeta(contentId)
        val traktSeasons = listOf(
            TraktSeasonDto(
                number = 1,
                episodes = listOf(
                    TraktEpisodeDto(season = 1, number = 1, title = "Piloto"),
                    TraktEpisodeDto(season = 1, number = 2, title = "Outro episódio"),
                    TraktEpisodeDto(season = 1, number = 3, title = "A grande virada"),
                ),
            ),
        )

        coEvery { auth.getCurrentAuthState() } returns TraktAuthState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
        coEvery { traktApi.getShowSeasons("Bearer test", contentId, "episodes") } returns
            Response.success(traktSeasons)
        coEvery {
            auth.executeAuthorizedRequest<List<TraktSeasonDto>>(any())
        } coAnswers {
            firstArg<suspend (String) -> Response<List<TraktSeasonDto>>>()("Bearer test")
        }
        every { metaRepository.getMetaFromAllAddons(any(), any(), any()) } returns
            flowOf(NetworkResult.Success(addonMeta))

        val service = TraktEpisodeMappingService(
            appContext = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthService = auth,
            metaRepository = metaRepository,
        )

        val first = service.resolveEpisodeMapping(
            contentId = contentId,
            contentType = "series",
            videoId = "addon-episode-2",
            season = 1,
            episode = 2,
            episodeTitle = "A grande virada",
        )
        val repeated = service.resolveEpisodeMapping(
            contentId = contentId,
            contentType = "series",
            videoId = "addon-episode-2",
            season = 1,
            episode = 2,
            episodeTitle = "A grande virada",
        )

        assertNotNull(first)
        assertEquals(EpisodeMappingEntry(season = 1, episode = 3, title = "A grande virada"), first)
        assertEquals(first, repeated)
        coVerify(exactly = 1) { traktApi.getShowSeasons("Bearer test", contentId, "episodes") }
        io.mockk.verify(exactly = 1) { metaRepository.getMetaFromAllAddons("series", contentId, null) }
    }

    private fun seriesMeta(contentId: String) = Meta(
        id = contentId,
        type = ContentType.SERIES,
        rawType = "series",
        name = "Série de teste",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
        runtime = null,
        director = emptyList(),
        cast = emptyList(),
        videos = listOf(
            Video(
                id = "addon-episode-1",
                title = "Piloto",
                released = null,
                thumbnail = null,
                season = 1,
                episode = 1,
                overview = null,
            ),
            Video(
                id = "addon-episode-2",
                title = "A grande virada",
                released = null,
                thumbnail = null,
                season = 1,
                episode = 2,
                overview = null,
            ),
        ),
        country = "BR",
        awards = null,
        language = "pt-BR",
        links = emptyList(),
    )
}
