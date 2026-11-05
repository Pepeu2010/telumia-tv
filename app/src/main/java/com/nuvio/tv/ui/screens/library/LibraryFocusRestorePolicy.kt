package com.nuvio.tv.ui.screens.library

/** Title, mode switch and selectors precede saved posters; tracking adds an action row. */
internal fun savedLibraryPosterGridIndex(posterIndex: Int, hasTrackingActions: Boolean): Int {
    require(posterIndex >= 0)
    return posterIndex + 3 + if (hasTrackingActions) 1 else 0
}
