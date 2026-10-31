package com.nuvio.tv.data.simkl

import com.nuvio.tv.data.repository.SimklIdResolver
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklRelatedServiceTest {
    @Test
    fun `reuses related response for a cached Simkl target`() = runBlocking {
        val (service, apiClient) = service()

        assertTrue(service.getRelated(meta(1)).isEmpty())
        assertTrue(service.getRelated(meta(1)).isEmpty())

        coVerify(exactly = 1) { apiClient.execute(any(), any()) }
    }

    @Test
    fun `fetches again after the oldest of 64 targets is evicted`() = runBlocking {
        val (service, apiClient) = service()

        for (id in 1..(RELATED_CACHE_MAX_ENTRIES + 1)) service.getRelated(meta(id))
        service.getRelated(meta(1))

        coVerify(exactly = RELATED_CACHE_MAX_ENTRIES + 2) { apiClient.execute(any(), any()) }
    }

    @Test
    fun `force refresh bypasses cached related response`() = runBlocking {
        val (service, apiClient) = service()
        val item = meta(1)

        service.getRelated(item)
        service.getRelated(item, forceRefresh = true)

        coVerify(exactly = 2) { apiClient.execute(any(), any()) }
    }

    private fun service(): Pair<SimklRelatedService, SimklApiClient> {
        val apiClient = mockk<SimklApiClient>()
        val idResolver = mockk<SimklIdResolver>()
        coEvery { idResolver.resolveIds(any(), any(), any()) } coAnswers {
            SimklIdResolver.ResolvedIds(
                simklId = secondArg<String>().toLong(),
                type = "movies",
            )
        }
        coEvery { apiClient.execute(any(), any()) } returns SimklApiResponse(
            status = 200,
            body = "{}",
            headers = emptyMap(),
        )
        return SimklRelatedService(apiClient, idResolver) to apiClient
    }

    private fun meta(id: Int) = Meta(
        id = "simkl:$id",
        type = ContentType.MOVIE,
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
    )
}
