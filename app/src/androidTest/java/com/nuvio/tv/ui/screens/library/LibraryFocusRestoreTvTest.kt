package com.nuvio.tv.ui.screens.library

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LibraryFocusRestoreTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoresOffscreenPosterAfterHeaders() = restore(false)
    @Test fun restoresOffscreenPosterAfterTrackingActions() = restore(true)

    private fun restore(tracking: Boolean) {
        val target = FocusRequester()
        var restoredIndex = -1
        compose.setContent {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize().testTag("library-grid"), state = grid) {
                repeat(if (tracking) 4 else 3) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.height(80.dp))
                    }
                }
                items(100, key = { "poster-$it" }) { index ->
                    Box(Modifier.height(150.dp).testTag("poster-$index")
                        .then(if (index == 80) Modifier.focusRequester(target) else Modifier)
                        .focusable())
                }
            }
            LaunchedEffect(Unit) {
                grid.scrollToItem(savedLibraryPosterGridIndex(80, tracking))
                restoredIndex = grid.firstVisibleItemIndex
                withFrameNanos { }
                target.requestFocus()
            }
        }
        compose.onNodeWithTag("poster-80").assertIsDisplayed().assertIsFocused()
        compose.runOnIdle { assertEquals(if (tracking) 84 else 83, restoredIndex) }
    }
}
