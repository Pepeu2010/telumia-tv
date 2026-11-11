package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelCatalogUtilsTest {
    @Test
    fun channelCatalogsAreReservedForLiveTv() {
        val channelCatalog = CatalogDescriptor(
            type = ContentType.CHANNEL,
            rawType = " Channel ",
            id = "channels",
            name = "Channels",
        )
        val tvCatalog = CatalogDescriptor(
            type = ContentType.TV,
            rawType = "tv",
            id = "shows",
            name = "Shows",
        )
        val movieCatalog = CatalogDescriptor(
            type = ContentType.MOVIE,
            rawType = "movie",
            id = "movies",
            name = "Movies",
        )

        assertFalse(channelCatalog.shouldShowOnHome())
        assertFalse(tvCatalog.shouldShowOnHome())
        assertTrue(movieCatalog.shouldShowOnHome())
    }
}
