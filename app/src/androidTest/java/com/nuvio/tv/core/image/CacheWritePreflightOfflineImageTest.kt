package com.nuvio.tv.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheWritePreflightOfflineImageTest {
    @OptIn(ExperimentalCoilApi::class)
    @Test fun aPreviouslyCachedImageStillLoadsFromDiskWhenStoragePreflightBlocksWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDirectory = File(context.cacheDir, "image-preflight-${UUID.randomUUID()}").apply { mkdirs() }
        val server = MockWebServer()
        val storageAllowsWrites = AtomicBoolean(true)
        val clockMillis = AtomicLong(10L)
        val imageBytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(imageBytes))
                .addHeader("Content-Type", "image/png")
                .addHeader("Cache-Control", "public, max-age=3600"),
        )
        server.start()

        val loader = ImageLoader.Builder(context)
            .components {
                add(CacheWritePreflightInterceptor(clockMillis = { clockMillis.get() }) { storageAllowsWrites.get() })
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { OkHttpClient() },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(testDirectory, "coil").toOkioPath())
                    .maxSizeBytes(4L * 1024L * 1024L)
                    .build()
            }
            .build()

        try {
            val request = ImageRequest.Builder(context)
                .data(server.url("/artwork.png").toString())
                .size(2, 2)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val first = loader.execute(request)
            assertTrue("Fixture response should decode: $first", first is SuccessResult)
            assertEquals("NETWORK", (first as SuccessResult).dataSource.name)

            storageAllowsWrites.set(false)
            clockMillis.addAndGet(1_000L)
            server.shutdown()

            val offline = loader.execute(request)
            assertTrue("Read-only policy should retain the offline disk hit", offline is SuccessResult)
            assertEquals("DISK", (offline as SuccessResult).dataSource.name)
            assertEquals("Only the initial online request should reach the fixture server", 1, server.requestCount)
        } finally {
            loader.shutdown()
            runCatching { server.shutdown() }
            testDirectory.deleteRecursively()
        }
    }
}
