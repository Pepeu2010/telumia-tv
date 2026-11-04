package com.nuvio.tv.ui.screens.player

/** Prefer two real frames on each side, then fill available slots at the ends of a video. */
internal fun <T> selectFilmstripNeighbors(
    centerMs: Long,
    candidates: List<T>,
    positionOf: (T) -> Long,
    limit: Int = 4,
): List<T> {
    if (limit <= 0) return emptyList()
    val distinct = candidates.distinctBy(positionOf).filter { positionOf(it) != centerMs }
    val before = distinct.filter { positionOf(it) < centerMs }.sortedByDescending(positionOf)
    val after = distinct.filter { positionOf(it) > centerMs }.sortedBy(positionOf)
    val selected = (before.take(limit / 2) + after.take(limit - limit / 2)).toMutableList()
    val used = selected.mapTo(mutableSetOf(), positionOf)
    val remaining = distinct.filter { positionOf(it) !in used }
        .sortedBy { kotlin.math.abs(positionOf(it) - centerMs) }
    selected.addAll(remaining.take((limit - selected.size).coerceAtLeast(0)))
    return selected.sortedBy(positionOf)
}
