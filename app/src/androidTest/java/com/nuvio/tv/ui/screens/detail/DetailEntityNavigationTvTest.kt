package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbEntityBrowseData
import com.nuvio.tv.core.tmdb.TmdbEntityHeader
import com.nuvio.tv.core.tmdb.TmdbEntityKind
import com.nuvio.tv.core.tmdb.TmdbEntityMediaType
import com.nuvio.tv.core.tmdb.TmdbEntityRail
import com.nuvio.tv.core.tmdb.TmdbEntityRailType
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.domain.model.MetaCompany
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PersonDetail
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.cast.CastDetailContent
import com.nuvio.tv.ui.screens.detail.CompanyLogosSection
import com.nuvio.tv.ui.screens.detail.CastSection
import com.nuvio.tv.ui.screens.tmdb.TmdbEntityBrowseContent
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailEntityNavigationTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun castCardRequiresTmdbIdAndReturningFromPersonPageRestoresFocusAndScroll() {
        val unavailable = MetaCastMember(name = "Actor without TMDB ID")
        val actor = MetaCastMember(name = "Ada Actor", character = "Lead", tmdbId = 1201)
        val cast = listOf(unavailable, actor)
        val person = personDetail(actor.tmdbId!!, actor.name, movieCredits = listOf(sampleMovie))
        val pageScrollState = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 24)
        val castRowState = LazyListState()

        setPersonNavigationContent(cast, person, pageScrollState, castRowState)

        compose.onNodeWithContentDescription(unavailable.name)
            .assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressDpadCenter()
        compose.onAllNodesWithTag("child-route").assertCountEquals(0)

        compose.onNodeWithContentDescription(actor.name)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        pressDpadCenter()

        val expectedRoute = Screen.CastDetail.createRoute(actor.tmdbId!!, actor.name)
        compose.onNodeWithTag("child-route").assertTextEquals(expectedRoute)
        compose.onNodeWithText(sampleMovie.name).assertIsDisplayed()

        var pageScrollBeforeReturn = emptyList<Pair<Int, Int>>()
        var rowScrollBeforeReturn = emptyList<Pair<Int, Int>>()
        compose.runOnIdle {
            pageScrollBeforeReturn = pageScrollState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
            rowScrollBeforeReturn = castRowState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
        }
        compose.onNodeWithText("Back to details")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressDpadCenter()

        compose.onNodeWithContentDescription(actor.name).assertIsFocused()
        compose.runOnIdle {
            assertEquals(pageScrollBeforeReturn, pageScrollState.layoutInfo.visibleItemsInfo.map { it.index to it.offset })
            assertEquals(rowScrollBeforeReturn, castRowState.layoutInfo.visibleItemsInfo.map { it.index to it.offset })
        }
    }

    @Test
    fun productionCardRequiresTmdbIdAndReturningFromCompanyPageRestoresFocusAndScroll() {
        val unavailable = MetaCompany(name = "Studio without TMDB ID")
        val company = MetaCompany(name = "Example Studio", tmdbId = 42)
        val companies = listOf(unavailable, company)
        val browseData = companyBrowseData(company.name, listOf(sampleMovie))
        val pageScrollState = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 24)
        val companyRowState = LazyListState()

        setCompanyNavigationContent(companies, browseData, pageScrollState, companyRowState)

        compose.onNodeWithContentDescription(unavailable.name)
            .assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressDpadCenter()
        compose.onAllNodesWithTag("child-route").assertCountEquals(0)

        compose.onNodeWithContentDescription(company.name)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        pressDpadCenter()

        val expectedRoute = Screen.TmdbEntityBrowse.createRoute("company", company.tmdbId!!, company.name, "movie")
        compose.onNodeWithTag("child-route").assertTextEquals(expectedRoute)
        compose.onNodeWithText(sampleMovie.name).assertIsDisplayed()

        var pageScrollBeforeReturn = emptyList<Pair<Int, Int>>()
        var rowScrollBeforeReturn = emptyList<Pair<Int, Int>>()
        compose.runOnIdle {
            pageScrollBeforeReturn = pageScrollState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
            rowScrollBeforeReturn = companyRowState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
        }
        compose.onNodeWithText("Back to details")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressDpadCenter()

        compose.onNodeWithContentDescription(company.name).assertIsFocused()
        compose.runOnIdle {
            assertEquals(pageScrollBeforeReturn, pageScrollState.layoutInfo.visibleItemsInfo.map { it.index to it.offset })
            assertEquals(rowScrollBeforeReturn, companyRowState.layoutInfo.visibleItemsInfo.map { it.index to it.offset })
        }
    }

    @Test
    fun personPageDisplaysCreditsAndAnExplicitEmptyState() {
        val personState = mutableStateOf(personDetail(1201, "Ada Actor", movieCredits = listOf(sampleMovie)))
        setContent {
            CastDetailContent(
                person = personState.value,
                isBiographyExpanded = false,
                onBiographyExpandedChange = {},
                onNavigateToDetail = { _, _, _ -> }
            )
        }
        compose.onNodeWithText(sampleMovie.name).assertIsDisplayed()

        compose.runOnIdle {
            personState.value = personDetail(1202, "Actor without credits")
        }
        compose.waitForIdle()
        val context = instrumentation.targetContext
        compose.onNodeWithText(context.getString(R.string.tmdb_entity_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tmdb_entity_empty_subtitle)).assertIsDisplayed()
    }

    @Test
    fun companyPageDisplaysWorksAndAnExplicitEmptyState() {
        val browseState = mutableStateOf(companyBrowseData("Example Studio", listOf(sampleMovie)))
        setContent {
            TmdbEntityBrowseContent(
                data = browseState.value,
                sourceType = "movie",
                watchedMovieIds = emptySet(),
                watchedSeriesIds = emptySet(),
                onNavigateToDetail = { _, _, _ -> },
                onLoadMoreRail = { _, _ -> }
            )
        }
        compose.onNodeWithText(sampleMovie.name).assertIsDisplayed()

        compose.runOnIdle {
            browseState.value = companyBrowseData("Empty Studio", emptyList())
        }
        compose.waitForIdle()
        val context = instrumentation.targetContext
        compose.onNodeWithText(context.getString(R.string.tmdb_entity_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tmdb_entity_empty_subtitle)).assertIsDisplayed()
    }

    private fun setPersonNavigationContent(
        cast: List<MetaCastMember>,
        person: PersonDetail,
        pageScrollState: LazyListState,
        castRowState: LazyListState
    ) {
        compose.setContent {
            NuvioTheme {
                var selectedPerson by remember { mutableStateOf<PersonDetail?>(null) }
                var route by remember { mutableStateOf<String?>(null) }
                var restorePersonId by remember { mutableStateOf<Int?>(null) }
                var restoreToken by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = pageScrollState) {
                        item { Spacer(Modifier.height(420.dp)) }
                        item {
                            CastSection(
                                cast = cast,
                                listState = castRowState,
                                restorePersonId = if (selectedPerson == null) restorePersonId else null,
                                restoreFocusToken = if (selectedPerson == null) restoreToken else 0,
                                onCastMemberClick = { member ->
                                    member.tmdbId?.takeIf { it > 0 }?.let { id ->
                                        restorePersonId = id
                                        route = Screen.CastDetail.createRoute(id, member.name)
                                        selectedPerson = person
                                    }
                                }
                            )
                        }
                    }

                    selectedPerson?.let { currentPerson ->
                        Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
                            CastDetailContent(
                                person = currentPerson,
                                isBiographyExpanded = false,
                                onBiographyExpandedChange = {},
                                onNavigateToDetail = { _, _, _ -> }
                            )
                            androidx.compose.material3.Text(
                                text = route.orEmpty(),
                                modifier = Modifier.align(Alignment.TopStart).testTag("child-route")
                            )
                            androidx.tv.material3.Button(
                                onClick = {
                                    selectedPerson = null
                                    restoreToken += 1
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                androidx.tv.material3.Text("Back to details")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setCompanyNavigationContent(
        companies: List<MetaCompany>,
        browseData: TmdbEntityBrowseData,
        pageScrollState: LazyListState,
        companyRowState: LazyListState
    ) {
        compose.setContent {
            NuvioTheme {
                var selectedCompany by remember { mutableStateOf<MetaCompany?>(null) }
                var route by remember { mutableStateOf<String?>(null) }
                var restoreCompanyId by remember { mutableStateOf<Int?>(null) }
                var restoreToken by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = pageScrollState) {
                        item { Spacer(Modifier.height(420.dp)) }
                        item {
                            CompanyLogosSection(
                                title = "Production",
                                companies = companies,
                                listState = companyRowState,
                                restoreCompanyId = if (selectedCompany == null) restoreCompanyId else null,
                                restoreFocusToken = if (selectedCompany == null) restoreToken else 0,
                                onCompanyClick = { clicked ->
                                    clicked.tmdbId?.takeIf { it > 0 }?.let { id ->
                                        restoreCompanyId = id
                                        route = Screen.TmdbEntityBrowse.createRoute(
                                            "company", id, clicked.name, "movie"
                                        )
                                        selectedCompany = clicked
                                    }
                                }
                            )
                        }
                    }

                    selectedCompany?.let {
                        Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
                            TmdbEntityBrowseContent(
                                data = browseData,
                                sourceType = "movie",
                                watchedMovieIds = emptySet(),
                                watchedSeriesIds = emptySet(),
                                onNavigateToDetail = { _, _, _ -> },
                                onLoadMoreRail = { _, _ -> }
                            )
                            androidx.compose.material3.Text(
                                text = route.orEmpty(),
                                modifier = Modifier.align(Alignment.TopStart).testTag("child-route")
                            )
                            androidx.tv.material3.Button(
                                onClick = {
                                    selectedCompany = null
                                    restoreToken += 1
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                androidx.tv.material3.Text("Back to details")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            NuvioTheme {
                Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
                    content()
                }
            }
        }
    }

    private fun pressDpadCenter() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

    private fun personDetail(
        id: Int,
        name: String,
        movieCredits: List<MetaPreview> = emptyList()
    ) = PersonDetail(
        tmdbId = id,
        name = name,
        biography = null,
        birthday = null,
        deathday = null,
        placeOfBirth = null,
        profilePhoto = null,
        knownFor = null,
        movieCredits = movieCredits,
        tvCredits = emptyList()
    )

    private fun companyBrowseData(name: String, works: List<MetaPreview>) = TmdbEntityBrowseData(
        header = TmdbEntityHeader(
            id = 42,
            kind = TmdbEntityKind.COMPANY,
            name = name,
            logo = null,
            originCountry = null,
            secondaryLabel = null,
            description = null
        ),
        rails = if (works.isEmpty()) {
            emptyList()
        } else {
            listOf(
                TmdbEntityRail(
                    mediaType = TmdbEntityMediaType.MOVIE,
                    railType = TmdbEntityRailType.POPULAR,
                    items = works
                )
            )
        }
    )

    private companion object {
        val sampleMovie = MetaPreview(
            id = "tmdb:sample-movie",
            type = ContentType.MOVIE,
            name = "A Test Film",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "2024",
            imdbRating = null,
            genres = emptyList()
        )
    }
}
