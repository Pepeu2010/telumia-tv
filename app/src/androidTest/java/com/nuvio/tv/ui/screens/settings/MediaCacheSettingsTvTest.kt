package com.nuvio.tv.ui.screens.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.system.Os
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.core.storage.*
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCacheSettingsTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixtureId = "telumia_cache_test_" + UUID.randomUUID().toString()
    private val fixtureDirectory by lazy { File(instrumentation.targetContext.cacheDir, fixtureId).apply { mkdirs() } }
    private val context by lazy { object : ContextWrapper(instrumentation.targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = fixtureDirectory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(fixtureId + "_" + name, mode)
    } }
    @After fun removeOnlyOwnedFixtures() {
        instrumentation.targetContext.deleteSharedPreferences(fixtureId + "_telumia_media_cache_v1")
        require(fixtureDirectory.canonicalFile.parentFile == instrumentation.targetContext.cacheDir.canonicalFile)
        require(fixtureDirectory.name == fixtureId)
        fixtureDirectory.deleteRecursively()
    }

    @Test fun remoteSelectsManualBudgetAndAutoWithoutChangingTheActiveBudget() {
        val focus = FocusRequester()
        val settings = mutableStateOf(MediaCacheSettings(MediaCacheMode.AUTO, 256L * MIB))
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Column(Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(24.dp)) {
                SettingsGroupCard { MediaCacheSettingsRows(settings.value,
                    MediaCacheBudget(256L * MIB, 256L * MIB, emptySet()), initialFocusRequester = focus) { settings.value = it } }
                LaunchedEffect(Unit) { focus.requestFocusAfterFrames() }
            }
        } }
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(MediaCacheMode.MANUAL, settings.value.mode) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnIdle { assertEquals(512L * MIB, settings.value.manualBytes) }
        compose.onNodeWithText("512 MiB").assertIsDisplayed()
        File(context.getExternalFilesDir(null), "telumia-cache-settings-tv.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(MediaCacheMode.AUTO, settings.value.mode); assertEquals(512L * MIB, settings.value.manualBytes) }
        compose.onNodeWithText("512 MiB").assertDoesNotExist()
    }

    @Test fun manualPreferenceSurvivesAStoreRecreationAndAutoKeepsTheChosenValue() {
        val cache = TvMediaCache(context)
        val previous = cache.loadSettings()
        try {
            assertTrue(cache.saveSettings(MediaCacheSettings(MediaCacheMode.MANUAL, 2048L * MIB)))
            val reopened = TvMediaCache(context).loadSettings()
            assertEquals(MediaCacheMode.MANUAL, reopened.mode)
            assertEquals(2048L * MIB, reopened.manualBytes)
            assertTrue(cache.saveSettings(reopened.copy(mode = MediaCacheMode.AUTO)))
            assertEquals(2048L * MIB, TvMediaCache(context).loadSettings().manualBytes)
        } finally { assertTrue(cache.saveSettings(previous)) }
    }

    @Test fun futureSchemaFallsBackSafelyWithoutErasingThePreferenceFile() {
        val preferences = context.getSharedPreferences("telumia_media_cache_v1", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putInt("schema", 999).putString("mode", "FUTURE")
            .putString("unknown_future_field", "preserve-me").commit())
        val original = preferences.all.toMap()
        val cache = TvMediaCache(context)
        assertEquals(MediaCacheSettings(), cache.loadSettings())
        assertFalse(cache.saveSettings(MediaCacheSettings(MediaCacheMode.MANUAL, 2048L * MIB)))
        assertEquals(original, preferences.all)
        assertEquals(999, preferences.getInt("schema", 0))
        assertEquals(MediaCacheSettings(), TvMediaCache(context).loadSettings())
    }

    @Test fun wrongTypedSchemaCannotBeDowngradedBySaving() {
        val preferences = context.getSharedPreferences("telumia_media_cache_v1", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putString("schema", "next").putLong("future_limit", 42).commit())
        val original = preferences.all.toMap()
        val cache = TvMediaCache(context)
        assertEquals(MediaCacheSettings(), cache.loadSettings())
        assertFalse(cache.saveSettings(MediaCacheSettings()))
        assertEquals(original, preferences.all)
    }

    @Test fun runtimeMeasurementCountsImageBadgeAndGuideCachesAndExcludesDurableAndLinkedFiles() {
        fun write(name: String, size: Int): File = File(context.cacheDir, name).apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(size))
        }
        write("image_cache/image", 101)
        write("badge_cache/badge", 23)
        write("live_tv_guide/guide.json.gz", 17)
        val avatar = write("profile-studio/avatar", 211)
        val download = write("downloads/movie", 307)
        val link = File(context.cacheDir, "image_cache/linked-profile")
        Os.symlink(avatar.parentFile!!.absolutePath, link.absolutePath)
        try {
            val cache = TvMediaCache(context)
            val deadlineNanos = System.nanoTime() + 5_000_000_000L
            var occupancy = cache.occupiedCache
            while ((!occupancy.complete || occupancy.bytes != 141L) && System.nanoTime() < deadlineNanos) {
                Thread.sleep(10L)
                occupancy = cache.occupiedCache
            }
            assertTrue(occupancy.complete)
            assertEquals(141L, occupancy.bytes)
            assertEquals(211L, avatar.length())
            assertEquals(307L, download.length())
            assertEquals(141L, cache.deviceSnapshot.occupiedCacheBytes)
            assertEquals(MediaCachePolicy.resolve(MediaCachePlatform.TV, cache.activeSettings, cache.deviceSnapshot), cache.activeBudget)
        } finally { java.nio.file.Files.delete(link.toPath()) }
    }

    @Test fun runtimeMeasurementCountsPlaybackCachesAndStaleVodFolders() {
        fun write(root: File, name: String, size: Int): File = File(root, name).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(size))
        }
        val cacheDirectory = context.cacheDir
        val filesDirectory = File(cacheDirectory, "files-fixture").apply { mkdirs() }
        write(cacheDirectory, "nuvio_vod_cache/current-span", 37)
        write(cacheDirectory, "nuvio_vod_cache_stale_1761582000/old-span", 19)
        write(cacheDirectory, "nuvio-engine/payload/chunk", 23)
        write(filesDirectory, "scene-bookmarks-v1/bookmarks.json", 11)
        write(filesDirectory, "downloads/movie.mkv", 31)

        val result = measureTvMediaCacheOccupancy(cacheDirectory.toPath(), filesDirectory.toPath())

        assertTrue(result.complete)
        assertEquals(79L, result.bytes)
        assertEquals(11L, File(filesDirectory, "scene-bookmarks-v1/bookmarks.json").length())
        assertEquals(31L, File(filesDirectory, "downloads/movie.mkv").length())
    }

    @Test fun runtimeMeasurementCountsProfilePluginCodeAndExcludesDurableFiles() {
        fun write(root: File, name: String, size: Int): File = File(root, name).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(size))
        }
        val cacheDirectory = File(fixtureDirectory, "plugin-cache-scope").apply { mkdirs() }
        val filesDirectory = File(fixtureDirectory, "plugin-files-scope").apply { mkdirs() }
        write(filesDirectory, "plugin_code/scraper.js", 7)
        write(filesDirectory, "plugin_code_p2/scraper.js", 11)
        write(filesDirectory, "plugin_code_p17/other.js", 13)
        val unrelated = write(filesDirectory, "plugin_code_p2_backup/ignored.js", 17)
        val datastore = write(filesDirectory, "datastore/account.preferences_pb", 23)
        val avatar = write(filesDirectory, "profile-studio-v1/avatars/local.png", 29)

        val result = measureTvMediaCacheOccupancy(cacheDirectory.toPath(), filesDirectory.toPath())

        assertTrue(result.complete)
        assertEquals(31L, result.bytes)
        assertEquals(17L, unrelated.length())
        assertEquals(23L, datastore.length())
        assertEquals(29L, avatar.length())
    }

    @Test fun runtimeMeasurementCountsStagedUpdaterApkWithoutChangingIt() {
        fun write(root: File, name: String, bytes: ByteArray): File = File(root, name).apply {
            parentFile!!.mkdirs()
            writeBytes(bytes)
        }
        val cacheDirectory = File(fixtureDirectory, "updater-cache-scope").apply { mkdirs() }
        val filesDirectory = File(fixtureDirectory, "updater-files-scope").apply { mkdirs() }
        val originalBytes = ByteArray(41) { it.toByte() }
        val stagedApk = write(cacheDirectory, "updates/telumia-update.apk", originalBytes)
        val unrelatedUpdate = write(cacheDirectory, "updates-backup/old.apk", ByteArray(43))
        val download = write(cacheDirectory, "downloads/movie.mkv", ByteArray(47))

        val result = measureTvMediaCacheOccupancy(cacheDirectory.toPath(), filesDirectory.toPath())

        assertTrue(result.complete)
        assertEquals(41L, result.bytes)
        assertArrayEquals(originalBytes, stagedApk.readBytes())
        assertEquals(43L, unrelatedUpdate.length())
        assertEquals(47L, download.length())
    }

    @Test fun runtimeMeasurementCountsManagedHttpAddonMetadataAndLegacyCacheFolders() {
        fun write(relativePath: String, size: Int): File = File(context.cacheDir, relativePath).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(size))
        }
        val http = write("http_cache_v2/first-party-entry", 13)
        val addons = write("addon_http_cache/addon-entry", 17)
        val metadata = write("metadata/mdblist/ratings.json", 19)
        val legacy = write("http_cache/legacy-entry", 23)
        val filesDirectory = File(context.cacheDir, "files-fixture").apply { mkdirs() }

        val result = measureTvMediaCacheOccupancy(context.cacheDir.toPath(), filesDirectory.toPath())

        assertTrue(result.complete)
        assertEquals(72L, result.bytes)
        assertEquals(13L, http.length())
        assertEquals(17L, addons.length())
        assertEquals(19L, metadata.length())
        assertEquals(23L, legacy.length())
    }

    @Test fun nativeDirectoryIterationStopsAtItsEntryBudget() {
        val images = File(context.cacheDir, "image_cache").apply { mkdirs() }
        repeat(20) { File(images, "$it").writeBytes(ByteArray(4)) }
        val result = OwnedMediaCacheOccupancy.measure(context.cacheDir.toPath(), listOf(listOf("image_cache")),
            maxEntries = 3, maxMillis = 10_000)
        assertFalse(result.complete)
        assertEquals(3, result.visitedEntries)
        assertEquals(8L, result.bytes)
    }
}
