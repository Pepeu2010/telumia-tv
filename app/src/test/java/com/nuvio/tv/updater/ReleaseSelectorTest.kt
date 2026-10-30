package com.nuvio.tv.updater

import com.nuvio.tv.data.remote.dto.GitHubReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseSelectorTest {
    @Test
    fun `stable channel excludes GitHub and tag prereleases`() {
        val releases = listOf(
            release("1.1.0-beta.2", prerelease = true),
            release("1.1.0-beta.1"),
            release("1.0.1"),
            release("1.0.0")
        )

        val selected = ReleaseSelector.eligibleReleases(releases)

        assertEquals(listOf("1.0.1", "1.0.0"), selected.map { it.tagName })
    }

    @Test
    fun `stable channel sorts by semantic version instead of GitHub response order`() {
        val releases = listOf(
            release("1.0.8"),
            release("1.0.11"),
            release("1.0.9")
        )

        val selected = ReleaseSelector.eligibleReleases(releases)

        assertEquals(listOf("1.0.11", "1.0.9", "1.0.8"), selected.map { it.tagName })
    }

    @Test
    fun `legacy release title marks beta without tag suffix`() {
        val releases = listOf(
            release(tag = "0.7.18", name = "Beta 0.7.18 Hotfix"),
            release(tag = "0.7.17")
        )

        val selected = ReleaseSelector.eligibleReleases(releases)

        assertEquals(listOf("0.7.17"), selected.map { it.tagName })
    }

    @Test
    fun `drafts and invalid tags are excluded`() {
        val releases = listOf(
            release("1.2.0", draft = true),
            release("nightly"),
            release("1.1.0")
        )

        val selected = ReleaseSelector.eligibleReleases(releases)

        assertEquals(listOf("1.1.0"), selected.map { it.tagName })
    }

    private fun release(
        tag: String,
        name: String = tag,
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = GitHubReleaseDto(
        tagName = tag,
        name = name,
        draft = draft,
        prerelease = prerelease
    )
}
