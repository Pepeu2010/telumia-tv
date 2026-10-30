package com.nuvio.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeTitleNormalizerTest {
    @Test
    fun `normalization preserves shared title matching semantics`() {
        val normalizer = EpisodeTitleNormalizer(capacity = 2)

        assertEquals("a grande virada", normalizer.normalize("A Grande—Virada!"))
        assertEquals("a grande virada", normalizer.normalize("A Grande—Virada!"))
        assertEquals(1, normalizer.size)
    }

    @Test
    fun `normalization cache never exceeds its entry budget`() {
        val normalizer = EpisodeTitleNormalizer(capacity = 2)

        normalizer.normalize("Primeiro episodio")
        normalizer.normalize("Segundo episodio")
        normalizer.normalize("Terceiro episodio")

        assertEquals(2, normalizer.size)
        assertEquals("terceiro episodio", normalizer.normalize("Terceiro episodio"))
        assertEquals(2, normalizer.size)
    }
}
