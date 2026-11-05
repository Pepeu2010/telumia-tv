package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.TestPreferencesStore
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LiveTvSourceStoreTest {
    @Test fun configurationsRemainIsolatedAcrossProfilesAndStoreRecreation() = runTest {
        val profile = MutableStateFlow(1)
        val manager = mockk<ProfileManager> { every { activeProfileId } returns profile }
        val data = mapOf(1 to TestPreferencesStore(), 2 to TestPreferencesStore())
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(any(), "live_tv_sources") } answers { data.getValue(firstArg()) }
        }
        val store = LiveTvSourceStore(factory, manager)
        val source = store.add("Brasil", "https://example.com/list.m3u", "https://example.com/epg.xml")
        profile.value = 2
        assertTrue(store.list().isEmpty())
        store.add("Outro", "https://other.example/list.m3u")
        profile.value = 1
        assertEquals(listOf(source), LiveTvSourceStore(factory, manager).list())
    }

    @Test fun corruptDataCannotBeOverwrittenByAnEdit() = runTest {
        val key = stringPreferencesKey("sources_v1")
        val data = TestPreferencesStore(preferencesOf(key to "{invalid"))
        val manager = mockk<ProfileManager> { every { activeProfileId } returns MutableStateFlow(1) }
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(1, "live_tv_sources") } returns data
        }
        val store = LiveTvSourceStore(factory, manager)
        try {
            store.add("TV", "https://example.com/list.m3u")
            fail("Corrupt data must block the write")
        } catch (_: IllegalStateException) { }
        assertEquals("{invalid", data.value[key])
    }
}
