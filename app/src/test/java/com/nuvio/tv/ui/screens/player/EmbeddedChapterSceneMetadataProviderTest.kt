package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.player.metadata.TimedMetadataBody
import com.nuvio.tv.core.player.metadata.TimedMetadataEvent
import com.nuvio.tv.core.player.metadata.TimedMetadataKind
import com.nuvio.tv.core.player.metadata.TimedMetadataProvenance
import com.nuvio.tv.core.player.metadata.TimedMetadataScope
import com.nuvio.tv.core.player.metadata.TimedMetadataTimeline
import com.nuvio.tv.core.player.metadata.loadTimedMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedChapterSceneMetadataProviderTest {
    private val baseScope = TimedMetadataScope("movie-1", "movie", "movie-1")
    private val scope = sceneInfoScopeForSource(baseScope, "https://media.example/film.mkv?token=private")!!
    private val sourceTimeline = TimedMetadataTimeline.create(
        scope,
        listOf(
            TimedMetadataEvent("chapter:opening", 0, body = TimedMetadataBody.Chapter("A chegada"),
                provenance = TimedMetadataProvenance(MEDIA3_ID3_PROVIDER, "opening")),
            TimedMetadataEvent("chapter:ending", 5_000, body = TimedMetadataBody.Chapter("O retorno"),
                provenance = TimedMetadataProvenance(MEDIA3_ID3_PROVIDER, "ending")),
            TimedMetadataEvent("untrusted:actor", 5_000, 8_000,
                TimedMetadataBody.Actor("person-1", "Nome sem provider"), TimedMetadataProvenance("untrusted"))
        ),
        durationMs = 10_000
    )

    @Test
    fun editionScopeHashesSourceAndRejectsMissingOrOversizedIdentity() {
        assertTrue(scope.editionId!!.matches(Regex("[a-f0-9]{64}")))
        assertTrue(scope.editionId != "https://media.example/film.mkv?token=private")
        assertNull(sceneInfoScopeForSource(baseScope, null))
        assertNull(sceneInfoScopeForSource(baseScope, "x".repeat(16_385)))
        assertNull(sceneInfoScopeForSource(baseScope.copy(videoId = " "), "cut"))
    }

    @Test
    fun providerCreatesEditionBoundChapterIntervalsAndTimelineProjectsOnlyActiveFacts() = runBlocking {
        val provider = EmbeddedChapterSceneMetadataProvider(sourceTimeline, durationMs = 10_000)
        val loaded = loadTimedMetadata(scope, listOf(provider), durationMs = 10_000,
            trustedSceneProviderIds = setOf(provider.id))

        assertTrue(loaded.failures.isEmpty())
        assertEquals(listOf("A chegada", "O retorno"), loaded.timeline.events.map {
            (it.body as TimedMetadataBody.Chapter).title
        })
        assertEquals(listOf(5_000L, 10_000L), loaded.timeline.events.map(TimedMetadataEvent::endMs))
        assertTrue(loaded.timeline.events.all { it.confidence == 1.0 })
        assertEquals(listOf("A chegada"), sceneInfoEntriesAt(loaded.timeline, scope, 4_999).map(TvSceneInfoEntry::title))
        assertEquals(listOf("O retorno"), sceneInfoEntriesAt(loaded.timeline, scope, 5_000).map(TvSceneInfoEntry::title))
        assertTrue(sceneInfoEntriesAt(loaded.timeline, scope, 10_000).isEmpty())
        assertTrue(sceneInfoEntriesAt(loaded.timeline, scope.copy(editionId = "0".repeat(64)), 5_000).isEmpty())
    }

    @Test
    fun providerRejectsUnboundOrMismatchedSourceTimelines() = runBlocking {
        val provider = EmbeddedChapterSceneMetadataProvider(sourceTimeline, durationMs = 10_000)
        assertTrue(provider.load(baseScope).isEmpty())
        assertTrue(provider.load(scope.copy(videoId = "movie-2")).isEmpty())
        assertTrue(provider.load(scope.copy(editionId = "0".repeat(64))).isEmpty())
    }

    @Test
    fun providerDoesNotConvertOtherKindsOrProvidersIntoSceneFacts() = runBlocking {
        val mixed = TimedMetadataTimeline.create(scope, sourceTimeline.events + TimedMetadataEvent(
            id = "forged:chapter", startMs = 1_000, endMs = 4_000,
            body = TimedMetadataBody.Chapter("Injected chapter"),
            provenance = TimedMetadataProvenance("untrusted")
        ), durationMs = 10_000)
        val events = EmbeddedChapterSceneMetadataProvider(mixed, 10_000).load(scope)
        assertEquals(listOf("A chegada", "O retorno"), events.map { (it.body as TimedMetadataBody.Chapter).title })
        assertTrue(events.none { it.body.kind == TimedMetadataKind.ACTOR })
    }

    @Test
    fun providerDoesNotInventAnEndForAnUnboundedFinalChapter() = runBlocking {
        val oneChapter = TimedMetadataTimeline.create(scope, listOf(TimedMetadataEvent(
            id = "chapter:open-ended", startMs = 1_000,
            body = TimedMetadataBody.Chapter("Sem duração"),
            provenance = TimedMetadataProvenance(MEDIA3_ID3_PROVIDER, "open-ended")
        )))
        assertTrue(EmbeddedChapterSceneMetadataProvider(oneChapter, durationMs = null).load(scope).isEmpty())
    }
}
