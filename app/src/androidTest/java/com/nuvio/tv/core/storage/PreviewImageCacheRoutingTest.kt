package com.nuvio.tv.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.NuvioApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewImageCacheRoutingTest {
    @Test fun previewArtworkLoaderUsesItsOwnCategoryQuotaAndDirectory() {
        val app = (ApplicationProvider.getApplicationContext<Context>().applicationContext as NuvioApplication)
        val expectedDirectory = File(app.cacheDir, "media-cache-v1/previews/coil")

        val mainLoader = coil3.SingletonImageLoader.get(app)
        val loader = app.previewImageLoader()
        assertSame(loader, app.previewImageLoader())
        assertNotSame(mainLoader, loader)
        assertNotNull(loader.diskCache)
        assertEquals(expectedDirectory.toPath().toString(), loader.diskCache!!.directory.toString())
    }
}
