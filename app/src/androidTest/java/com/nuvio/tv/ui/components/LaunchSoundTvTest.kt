package com.nuvio.tv.ui.components

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSoundTvTest {
    @Test fun bundledLaunchSoundDecodes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loaded = CountDownLatch(1)
        var result = -1
        val pool = SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
        try {
            pool.setOnLoadCompleteListener { _, _, status -> result = status; loaded.countDown() }
            assertTrue(pool.load(context, R.raw.telumia_launch, 1) > 0)
            assertTrue("Launch audio must decode", loaded.await(5, TimeUnit.SECONDS))
            assertEquals(0, result)
        } finally { pool.release() }
    }
}
