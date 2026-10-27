package com.nuvio.tv.core.network

import java.nio.file.Files
import java.util.Comparator
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HttpCacheWritePolicyInterceptorTest {
    private fun withCache(test: (java.io.File, Cache) -> Unit) {
        val directory = Files.createTempDirectory("telumia-http-cache-")
        val cache = Cache(directory.toFile(), 1024L * 1024L)
        try {
            test(directory.toFile(), cache)
        } finally {
            cache.close()
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun client(cache: Cache, writesEnabled: () -> Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(HttpCacheWritePolicyInterceptor(writesEnabled))
            .build()

    @Test fun readonlyPolicyKeepsExistingEntriesReadableWithoutAnotherNetworkRequest() = withCache { _, cache ->
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("cached").addHeader("Cache-Control", "public, max-age=3600"))
            var writesEnabled = true
            val client = client(cache) { writesEnabled }
            val request = Request.Builder().url(server.url("/metadata")).build()

            client.newCall(request).execute().use { assertEquals("cached", it.body.string()) }
            writesEnabled = false
            client.newCall(request).execute().use { response ->
                assertEquals("cached", response.body.string())
                assertNotNull(response.cacheResponse)
                assertNull(response.networkResponse)
            }

            assertEquals(1, server.requestCount)
        }
    }

    @Test fun sharedAddonCacheServesCloudstreamEntriesToOtherAddonClients() = withCache { _, cache ->
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("addon-catalog").addHeader("Cache-Control", "public, max-age=3600"))
            val networkAddonClient = client(cache) { true }
            val cloudstreamClient = client(cache) { false }
            val request = Request.Builder().url(server.url("/addon-catalog")).build()

            networkAddonClient.newCall(request).execute().use { assertEquals("addon-catalog", it.body.string()) }
            cloudstreamClient.newCall(request).execute().use { response ->
                assertEquals("addon-catalog", response.body.string())
                assertNotNull(response.cacheResponse)
                assertNull(response.networkResponse)
            }

            assertEquals(1, server.requestCount)
        }
    }

    @Test fun readonlyPolicyDoesNotPersistNewNetworkResponses() = withCache { _, cache ->
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("fresh").addHeader("Cache-Control", "public, max-age=3600")) }
            val client = client(cache) { false }
            val request = Request.Builder().url(server.url("/metadata")).build()

            repeat(2) {
                client.newCall(request).execute().use { response ->
                    assertEquals("fresh", response.body.string())
                    assertEquals("no-store", response.header("Cache-Control"))
                    assertNull(response.cacheResponse)
                }
            }

            assertEquals(2, server.requestCount)
        }
    }

    @Test fun errorResponsesAreNeverPersistedEvenWhenWritesAreEnabled() = withCache { _, cache ->
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable").addHeader("Cache-Control", "public, max-age=3600")) }
            val client = client(cache) { true }
            val request = Request.Builder().url(server.url("/metadata")).build()

            repeat(2) {
                client.newCall(request).execute().use { response ->
                    assertEquals(503, response.code)
                    assertEquals("no-store", response.header("Cache-Control"))
                    assertNull(response.cacheResponse)
                }
            }

            assertEquals(2, server.requestCount)
        }
    }
}
