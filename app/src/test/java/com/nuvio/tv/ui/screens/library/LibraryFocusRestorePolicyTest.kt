package com.nuvio.tv.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFocusRestorePolicyTest {
    @Test fun firstPosterIsAfterTheThreeHeaderRows() {
        assertEquals(3, savedLibraryPosterGridIndex(0, false))
    }
    @Test fun trackingActionsShiftEveryPosterByOneMoreRow() {
        assertEquals(4, savedLibraryPosterGridIndex(0, true))
        assertEquals(84, savedLibraryPosterGridIndex(80, true))
    }
    @Test fun posterIndexIsNotConfusedWithRowNumberInMultiColumnGrids() {
        assertEquals(83, savedLibraryPosterGridIndex(80, false))
    }
}
