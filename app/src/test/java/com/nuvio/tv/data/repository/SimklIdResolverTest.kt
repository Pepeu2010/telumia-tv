package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.data.simkl.SimklApiConfiguration
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SimklIdResolverTest {
    @Test
    fun `resolved ids and episode mappings are reused while fresh`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/anime/42")),
            )
            server.enqueue(MockResponse().setBody("""{"ids":{"mal":123,"imdb":"tt123"},"season":1}"""))
            server.enqueue(
                MockResponse().setBody(
                    """[{"episode":1,"tvdb":{"season":1,"episode":2}}]""",
                ),
            )

            val resolver = resolver(server)
            val ids = resolver.resolveIds(source = "imdb", id = "tt123")

            assertNotNull(ids)
            assertEquals(ids, resolver.resolveIds(source = "imdb", id = "tt123"))
            assertEquals(listOf(SimklIdResolver.EpisodeMapping(1, 1, 2)), resolver.getEpisodeMapping(42))
            assertEquals(listOf(SimklIdResolver.EpisodeMapping(1, 1, 2)), resolver.getEpisodeMapping(42))
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun `anime season and sibling id lookups are reused`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/anime/42")),
            )
            server.enqueue(MockResponse().setBody("""{"ids":{"imdb":"tt456"},"season":1}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"mapped_tvdb_seasons":[{"simkl_id":84,"tvdb_season":2}]}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"ids":{"mal":456,"imdb":"tt456"},"season":2}"""))

            val resolver = resolver(server)
            val first = resolver.resolveIdsForImdbEpisode("tt456", season = 2, episode = 1)

            assertEquals(84L, first?.simklId)
            assertEquals(first, resolver.resolveIdsForImdbEpisode("tt456", season = 2, episode = 1))
            assertEquals(4, server.requestCount)
        }
    }

    private fun resolver(server: MockWebServer) = SimklIdResolver(
        appContext = mockk<Context>(relaxed = true),
        okHttpClient = OkHttpClient(),
        simklConfig = SimklApiConfiguration(
            clientId = "test-client",
            appName = "Telumia",
            appVersion = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
        ),
    )
}
