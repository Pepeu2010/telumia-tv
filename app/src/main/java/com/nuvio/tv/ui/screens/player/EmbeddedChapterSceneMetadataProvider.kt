package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.player.metadata.SceneBookmarkScope
import com.nuvio.tv.core.player.metadata.SceneMetadataProvider
import com.nuvio.tv.core.player.metadata.TimedMetadataBody
import com.nuvio.tv.core.player.metadata.TimedMetadataEvent
import com.nuvio.tv.core.player.metadata.TimedMetadataKind
import com.nuvio.tv.core.player.metadata.TimedMetadataProvenance
import com.nuvio.tv.core.player.metadata.TimedMetadataScope
import com.nuvio.tv.core.player.metadata.TimedMetadataTimeline

/** The current source is hashed into the scope; the URL and its credentials never enter the UI. */
internal fun sceneInfoScopeForSource(
    scope: TimedMetadataScope,
    sourceIdentity: String?,
): TimedMetadataScope? {
    val identity = sourceIdentity?.takeIf { it.isNotBlank() && it.length <= 16_384 } ?: return null
    if (listOf(scope.mediaId, scope.mediaType, scope.videoId).any { it.isBlank() || it.length > 1_024 }) return null
    return scope.copy(editionId = SceneBookmarkScope.sourceEdition(identity))
}

/** Only ID3 chapter frames emitted by the currently playing Media3 item become scene metadata. */
internal class EmbeddedChapterSceneMetadataProvider(
    private val sourceTimeline: TimedMetadataTimeline,
    private val durationMs: Long?,
) : SceneMetadataProvider {
    override val id: String = MEDIA3_SCENE_PROVIDER_ID

    override suspend fun load(scope: TimedMetadataScope): List<TimedMetadataEvent> {
        if (scope.editionId.isNullOrBlank() || sourceTimeline.scope != scope) return emptyList()
        val chapters = sourceTimeline.events.filter { event ->
            event.provenance.providerId == MEDIA3_ID3_PROVIDER && event.body is TimedMetadataBody.Chapter
        }
        val boundedDuration = durationMs?.takeIf { it > 0L }
        val nextStarts = LongArray(chapters.size) { Long.MAX_VALUE }
        var nearestLaterStart = Long.MAX_VALUE
        for (index in chapters.indices.reversed()) {
            val start = chapters[index].startMs
            nextStarts[index] = nearestLaterStart.takeIf { it > start } ?: Long.MAX_VALUE
            nearestLaterStart = minOf(nearestLaterStart, start)
        }
        return chapters.mapIndexedNotNull { index, event ->
            val body = event.body as TimedMetadataBody.Chapter
            val nextStart = nextStarts[index].takeIf { it != Long.MAX_VALUE }
            val end = event.endMs?.takeIf { it > event.startMs }
                ?: nextStart
                ?: boundedDuration?.takeIf { it > event.startMs }
                ?: return@mapIndexedNotNull null
            val boundedEnd = boundedDuration?.let { minOf(end, it) } ?: end
            if (boundedEnd <= event.startMs || body.title.isBlank()) return@mapIndexedNotNull null
            event.copy(
                endMs = boundedEnd,
                provenance = TimedMetadataProvenance(id, event.provenance.evidenceId ?: event.id),
                confidence = 1.0,
            )
        }
    }
}

internal data class TvSceneInfoEntry(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

internal data class TvSceneInfoChapterContext(
    val previous: TvSceneInfoEntry?,
    val current: TvSceneInfoEntry?,
    val next: TvSceneInfoEntry?,
)

internal enum class TvSceneInfoChapterPosition {
    PREVIOUS,
    CURRENT,
    NEXT,
}

internal data class TvSceneInfoDialogState(
    val scope: TimedMetadataScope,
    val context: TvSceneInfoChapterContext,
)

private const val MEDIA3_SCENE_PROVIDER_ID = "media3-id3-scene-chapters"

internal fun sceneInfoEntriesAt(
    timeline: TimedMetadataTimeline?,
    expectedScope: TimedMetadataScope?,
    positionMs: Long,
): List<TvSceneInfoEntry> {
    if (timeline == null || expectedScope == null || timeline.scope != expectedScope) return emptyList()
    return timeline.sceneEventsAt(positionMs).mapNotNull { event ->
        val chapter = event.body as? TimedMetadataBody.Chapter ?: return@mapNotNull null
        val endMs = event.endMs ?: return@mapNotNull null
        if (event.provenance.providerId != MEDIA3_SCENE_PROVIDER_ID || event.body.kind != TimedMetadataKind.CHAPTER) {
            return@mapNotNull null
        }
        TvSceneInfoEntry(event.id, chapter.title, event.startMs, endMs)
    }
}

internal fun sceneInfoChapterContextAt(
    timeline: TimedMetadataTimeline?,
    expectedScope: TimedMetadataScope?,
    positionMs: Long,
): TvSceneInfoChapterContext? {
    return sceneInfoChapterContextAt(sceneInfoChapterEntries(timeline, expectedScope), positionMs)
}

internal fun sceneInfoChapterEntries(
    timeline: TimedMetadataTimeline?,
    expectedScope: TimedMetadataScope?,
): List<TvSceneInfoEntry> {
    if (timeline == null || expectedScope == null || timeline.scope != expectedScope ||
        expectedScope.editionId.isNullOrBlank()
    ) return emptyList()

    return timeline.events.mapNotNull { event ->
        val chapter = event.body as? TimedMetadataBody.Chapter ?: return@mapNotNull null
        val endMs = event.endMs ?: return@mapNotNull null
        if (event.provenance.providerId != MEDIA3_SCENE_PROVIDER_ID ||
            event.confidence?.let { it >= MIN_SCENE_INFO_CONFIDENCE } != true ||
            event.body.kind != TimedMetadataKind.CHAPTER || chapter.title.isBlank()
        ) return@mapNotNull null
        TvSceneInfoEntry(event.id, chapter.title, event.startMs, endMs)
    }
}

internal fun sceneInfoChapterContextAt(
    chapters: List<TvSceneInfoEntry>,
    positionMs: Long,
): TvSceneInfoChapterContext? {
    if (chapters.isEmpty()) return null

    val currentIndex = chapters.indexOfLast { positionMs >= it.startMs && positionMs < it.endMs }
    if (currentIndex >= 0) {
        return TvSceneInfoChapterContext(
            previous = chapters.getOrNull(currentIndex - 1),
            current = chapters[currentIndex],
            next = chapters.getOrNull(currentIndex + 1),
        )
    }

    val nextIndex = chapters.indexOfFirst { it.startMs > positionMs }
    return TvSceneInfoChapterContext(
        previous = chapters.getOrNull(if (nextIndex < 0) chapters.lastIndex else nextIndex - 1),
        current = null,
        next = chapters.getOrNull(nextIndex),
    )
}

private const val MIN_SCENE_INFO_CONFIDENCE = 0.8
