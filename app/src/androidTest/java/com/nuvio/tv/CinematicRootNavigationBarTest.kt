package com.nuvio.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.screens.livetv.LiveTvOverviewHeader
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CinematicRootNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteSelectsTvAndReturnsToContentWithoutLosingTheSelectedDestination() {
        val route = mutableStateOf("home")
        val contentFocus = FocusRequester()
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Column {
                CinematicRootNavigationBar(
                    items = listOf(DrawerItem("home", "Início", icon = Icons.Default.Home),
                        DrawerItem("tv", "TV", icon = Icons.Default.Tv),
                        DrawerItem("search", "Busca", iconRes = R.raw.sidebar_search)),
                    selectedRoute = route.value, onNavigate = { route.value = it }, onExpandSidebar = {},
                    onEnterContent = { contentFocus.requestFocus() },
                )
                Button(onClick = {}, modifier = Modifier.padding(top = 48.dp)
                    .focusRequester(contentFocus).testTag("content")) { Text("Canal de teste") }
            }
            LaunchedEffect(Unit) { contentFocus.requestFocusAfterFrames() }
        } }
        val tv = compose.onNodeWithContentDescription("TV")
        tv.performSemanticsAction(SemanticsActions.RequestFocus)
        tv.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("tv", route.value) }
        tv.assertIsSelected().assertIsFocused()
        tv.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("content").assertIsFocused()
        tv.assertIsSelected()
    }
    @Test fun activeNavigationLabelIsVerticallyCenteredInsideItsButton() {
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            CinematicRootNavigationBar(
                items = listOf(DrawerItem("home", "Início", icon = Icons.Default.Home)),
                selectedRoute = "home", onNavigate = {}, onExpandSidebar = {},
            )
        } }
        val button = compose.onNodeWithContentDescription("Início").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Início", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The label must be centered vertically", kotlin.math.abs(button.center.y - label.center.y) < 2f)
    }

    @Test fun downFromTvNavigationFocusesTheRealSourceAction() {
        val contentFocus = FocusRequester()
        var opened = 0
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            CompositionLocalProvider(LocalContentFocusRequester provides contentFocus) {
                Column {
                    CinematicRootNavigationBar(
                        items = listOf(DrawerItem("tv", "TV", icon = Icons.Default.Tv)),
                        selectedRoute = "tv", onNavigate = {}, onExpandSidebar = {},
                        onEnterContent = { contentFocus.requestFocus() },
                    )
                    LiveTvOverviewHeader(onManageSources = { opened++ }, onRefresh = {}, refreshEnabled = false)
                }
            }
        } }
        val tv = compose.onNodeWithContentDescription("TV")
        tv.performSemanticsAction(SemanticsActions.RequestFocus)
        tv.assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.live_tv_sources)
        compose.onNodeWithText(label).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, opened) }
    }

}
