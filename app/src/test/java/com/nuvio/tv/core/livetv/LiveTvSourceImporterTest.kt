package com.nuvio.tv.core.livetv

import com.nuvio.tv.domain.model.LiveTvSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class LiveTvSourceImporterTest {
    @Test
    fun importsConfiguredPlaylistAndXmlTvGuideAsOneChannelSource() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val importer = LiveTvSourceImporter(OkHttpClient.Builder().followRedirects(false).build())
            val source = LiveTvSource(
                id = "source-br",
                name = "Brasil",
                playlistUrl = server.url("/brasil.m3u").toString(),
                epgUrl = server.url("/brasil.xml").toString(),
            )
            server.enqueue(MockResponse().setBody(
                """#EXTM3U
                    |#EXTINF:-1 tvg-id="br.news" tvg-name="Canal Brasil" group-title="Jornalismo",Canal Brasil
                    |https://cdn.example/live.m3u8
                """.trimMargin()
            ))
            server.enqueue(MockResponse().setBody(
                """<?xml version="1.0" encoding="UTF-8"?>
                    |<tv>
                    |  <channel id="br.news"><display-name lang="pt-BR">Canal Brasil</display-name></channel>
                    |  <programme channel="br.news" start="20261025190000 +0000" stop="20261025200000 +0000">
                    |    <title lang="pt-BR">Jornal do Brasil</title>
                    |  </programme>
                    |</tv>
                """.trimMargin()
            ))

            val imported = importer.import(source)

            assertEquals(1, imported.channels.size)
            assertEquals("Canal Brasil", imported.channels.single().displayName)
            assertEquals("br.news", imported.channels.single().tvgId)
            assertEquals("https://cdn.example/live.m3u8", imported.channels.single().streamUrl)
            assertEquals("Jornal do Brasil", imported.programs.single().title)
            assertEquals(source.id, imported.programs.single().sourceId)
            assertEquals("br.news", imported.programs.single().channelId)
            assertEquals(Instant.parse("2026-10-25T19:00:00Z").toEpochMilli(), imported.programs.single().startEpochMillis)
        }
    }

    @Test
    fun importsConfiguredPlaylistAndRejectsRedirects() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val importer = LiveTvSourceImporter(
                OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build(),
            )
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1 tvg-id=\"br\",Canal Brasil\nhttps://example.org/live.m3u8\n"))
            val imported = importer.import(LiveTvSource("source-1", "Brasil", server.url("/list.m3u").toString()))
            assertEquals("Canal Brasil", imported.channels.single().displayName)

            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.org/next"))
            val error = assertThrows(LiveTvSourceException::class.java) {
                runBlocking { importer.import(LiveTvSource("source-2", "Brasil", server.url("/redirect").toString())) }
            }
            assertEquals("A fonte redirecionou; atualize a URL configurada", error.message)
        }
    }

    @Test
    fun rejectsBodiesAbovePlaylistByteLimit() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val importer = LiveTvSourceImporter(OkHttpClient())
            server.enqueue(MockResponse().setBody("x".repeat(M3uPlaylistParser.MAX_PLAYLIST_CHARS + 1)))
            val error = assertThrows(LiveTvSourceException::class.java) {
                runBlocking { importer.import(LiveTvSource("source-1", "Brasil", server.url("/large").toString())) }
            }
            assertEquals("A fonte excede o tamanho permitido", error.message)
        }
    }
}

