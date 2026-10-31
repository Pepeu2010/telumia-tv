package com.nuvio.tv.ui.screens.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SettingsWorkspaceLayoutTest {
    private data class TvViewport(
        val label: String,
        val widthPixels: Int,
        val heightPixels: Int,
        val densityDpi: Int = 320
    ) {
        val widthDp = (widthPixels * 160f / densityDpi).dp
        val heightDp = (heightPixels * 160f / densityDpi).dp
    }

    private fun assertViewportDp(viewport: TvViewport, widthDp: Int, heightDp: Int) {
        assertEquals("${viewport.label} width at ${viewport.densityDpi} dpi", widthDp.dp, viewport.widthDp)
        assertEquals("${viewport.label} height at ${viewport.densityDpi} dpi", heightDp.dp, viewport.heightDp)
    }

    @Test
    fun `720p tv viewport gives more room to the detail pane`() {
        val viewport = TvViewport(label = "720p", widthPixels = 1280, heightPixels = 720)
        assertViewportDp(viewport, widthDp = 640, heightDp = 360)
        val layout = settingsWorkspaceLayout(viewport.widthDp)

        assertEquals(16.dp, layout.horizontalInset)
        assertEquals(168.dp, layout.railWidth)
        assertEquals(14.dp, layout.columnSpacing)
        assertEquals(640.dp, layout.workspaceMaxWidth)
    }

    @Test
    fun `1080p tv viewport interpolates between compact and full settings widths`() {
        val viewport = TvViewport(label = "1080p", widthPixels = 1920, heightPixels = 1080)
        assertViewportDp(viewport, widthDp = 960, heightDp = 540)
        val layout = settingsWorkspaceLayout(viewport.widthDp)

        assertTrue(layout.horizontalInset > 16.dp)
        assertTrue(layout.horizontalInset < 24.dp)
        assertTrue(layout.railWidth > 168.dp)
        assertTrue(layout.railWidth < 192.dp)
        assertTrue(layout.columnSpacing > 14.dp)
        assertTrue(layout.columnSpacing < 20.dp)
        assertTrue(layout.detailMaxWidth > 720.dp)
        assertTrue(layout.detailMaxWidth < 880.dp)
        assertEquals(viewport.widthDp, layout.workspaceMaxWidth)
    }

    @Test
    fun `4k 3840 by 2160 viewport centers a bounded workspace and wider detail pane`() {
        val viewport = TvViewport(label = "4K", widthPixels = 3840, heightPixels = 2160)
        assertViewportDp(viewport, widthDp = 1920, heightDp = 1080)
        val layout = settingsWorkspaceLayout(viewport.widthDp)

        assertEquals(48.dp, layout.horizontalInset)
        assertEquals(240.dp, layout.railWidth)
        assertEquals(32.dp, layout.columnSpacing)
        assertEquals(1200.dp, layout.detailMaxWidth)
        assertEquals(1600.dp, layout.workspaceMaxWidth)
        assertTrue(layout.workspaceMaxWidth < viewport.widthDp)
    }

    @Test
    fun `workspace dimensions change smoothly across viewport breakpoints`() {
        val justBelow = settingsWorkspaceLayout(1099.dp)
        val justAbove = settingsWorkspaceLayout(1101.dp)

        assertTrue(abs(justAbove.horizontalInset.value - justBelow.horizontalInset.value) < 1.dp.value)
        assertTrue(abs(justAbove.railWidth.value - justBelow.railWidth.value) < 1.dp.value)
        assertTrue(abs(justAbove.columnSpacing.value - justBelow.columnSpacing.value) < 1.dp.value)
        assertTrue(abs(justAbove.detailMaxWidth.value - justBelow.detailMaxWidth.value) < 1.dp.value)
    }
}
