package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto
import com.nuvio.tv.data.remote.dto.trakt.TraktMovieDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class TraktRelatedServiceTest {
    @Test
    fun `reuses related response for a cached target`() = runBlocking {
        val (service, api, _) = service()

        service.getRelated(meta(1))
        service.getRelated(meta(1))

        coVerify(exactly = 1) { api.getMovieRelated(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `evicts least recently used target at capacity`() = runBlocking {
        val (service, api, _) = service()

        for (id in 1..RELATED_CACHE_MAX_ENTRIES) service.getRelated(meta(id))
        service.getRelated(meta(1)) // Refresh recency for the oldest entry.
        service.getRelated(meta(RELATED_CACHE_MAX_ENTRIES + 1))
        service.getRelated(meta(2)) // This entry was least recently used and must be fetched again.
        service.getRelated(meta(1))

        coVerify(exactly = RELATED_CACHE_MAX_ENTRIES + 2) {
            api.getMovieRelated(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `force refresh removes cached response and fetches again`() = runBlocking {
        val (service, api, _) = service()
        val item = meta(1)

        service.getRelated(item)
        service.getRelated(item, forceRefresh = true)

        coVerify(exactly = 2) { api.getMovieRelated(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `404 empty response is cached`() = runBlocking {
        val (service, api, _) = service(response = Response.error(404, "missing".toResponseBody()))

        assertTrue(service.getRelated(meta(1)).isEmpty())
        assertTrue(service.getRelated(meta(1)).isEmpty())

        coVerify(exactly = 1) { api.getMovieRelated(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `failed response is not cached`() = runBlocking {
        val (service, api, _) = service(response = Response.error(500, "server error".toResponseBody()))

        repeat(2) {
            runCatching { service.getRelated(meta(1)) }
                .onSuccess { throw AssertionError("Expected the failed Trakt response to propagate") }
        }

        coVerify(exactly = 2) { api.getMovieRelated(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returns no more than twenty distinct results`() = runBlocking {
        val (service, api, _) = service(
            response = Response.success(200, (1..25).map { movie(it) })
        )

        assertEquals(20, service.getRelated(meta(1)).size)
        coVerify(exactly = 1) { api.getMovieRelated(any(), any(), any(), any(), 20) }
    }

    private fun service(
        response: Response<List<TraktMovieDto>> = Response.success(200, listOf(movie(100))),
    ): Triple<TraktRelatedService, TraktApi, TraktAuthService> {
        val api = mockk<TraktApi>()
        val auth = mockk<TraktAuthService>()
        coEvery { auth.executeAuthorizedRequest<List<TraktMovieDto>>(any()) } coAnswers {
            firstArg<suspend (String) -> Response<List<TraktMovieDto>>>().invoke("Bearer test")
        }
        coEvery { api.getMovieRelated(any(), any(), any(), any(), any()) } returns response

        return Triple(
            TraktRelatedService(mockk<Context>(relaxed = true), api, auth),
            api,
            auth,
        )
    }

    private fun meta(id: Int) = Meta(
        id = "meta:$id",
        type = ContentType.MOVIE,
        rawType = "movie",
        name = "Movie $id",
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
        videos = emptyList(),
        country = null,
        awards = null,
        language = null,
        links = emptyList(),
        imdbId = "tt${id.toString().padStart(7, '0')}",
    )

    private fun movie(id: Int) = TraktMovieDto(
        title = "Related $id",
        ids = TraktIdsDto(trakt = id, imdb = "tt${id.toString().padStart(7, '0')}"),
    )
}
