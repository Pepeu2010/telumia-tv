package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LayoutPreferenceDataStoreTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun focusedPosterBackdropExpansionDefaultsOffAndPreservesSavedChoice() = runTest {
        val activeProfile = MutableStateFlow(1)
        val profileManager = mockk<ProfileManager> {
            every { activeProfileId } returns activeProfile
        }
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            directory.root.resolve("layout.preferences_pb")
        }
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(1, "layout_settings") } returns dataStore
        }
        val preferences = LayoutPreferenceDataStore(factory, profileManager)

        assertFalse(preferences.focusedPosterBackdropExpandEnabled.first())

        preferences.setFocusedPosterBackdropExpandEnabled(true)

        assertTrue(preferences.focusedPosterBackdropExpandEnabled.first())
        assertTrue(
            LayoutPreferenceDataStore(factory, profileManager)
                .focusedPosterBackdropExpandEnabled
                .first()
        )
    }
}
