package com.antireelshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPolicyTest {
    @Test
    fun youtubeShortsWithIdBecomesWatchUrl() {
        val cases = listOf(
            "https://www.youtube.com/shorts/abcdefghijk",
            "https://m.youtube.com/shorts/abcdefghijk?feature=share",
            "https://youtube.com/shorts/abcdefghijk/",
            "https://www.youtube-nocookie.com/shorts/abcdefghijk",
            "vnd.youtube://shorts/abcdefghijk",
            "youtube://shorts/abcdefghijk",
        )
        for (raw in cases) {
            assertEquals(raw, LinkAction.OpenWatch("https://www.youtube.com/watch?v=abcdefghijk"), LinkPolicy.youtube(raw))
        }
    }

    @Test
    fun youtubeShortsWithoutIdCloses() {
        assertEquals(LinkAction.Close, LinkPolicy.youtube("https://www.youtube.com/shorts"))
        assertEquals(LinkAction.Close, LinkPolicy.youtube("https://www.youtube.com/shorts/"))
        assertEquals(LinkAction.Close, LinkPolicy.youtube("https://www.youtube.com/shorts/not-an-id"))
    }

    @Test
    fun ordinaryYoutubeLinksAreLeftAlone() {
        assertNull(LinkPolicy.youtube("https://www.youtube.com/watch?v=abcdefghijk"))
        assertNull(LinkPolicy.youtube("https://youtu.be/abcdefghijk"))
        assertNull(LinkPolicy.youtube("https://www.youtube.com/feed/subscriptions"))
        assertNull(LinkPolicy.youtube("https://example.com/shorts/abcdefghijk"))
        assertNull(LinkPolicy.youtube(null))
        assertNull(LinkPolicy.youtube("  "))
    }

    @Test
    fun instagramReelLinksClose() {
        val cases = listOf(
            "https://www.instagram.com/reel/CxYz/",
            "https://instagram.com/reels/CxYz/?igsh=abc",
            "https://www.instagram.com/reels/",
            "https://www.instagram.com/some.user/reel/CxYz/",
            "instagram://reels",
            "instagram://reel/CxYz",
        )
        for (raw in cases) {
            assertEquals(raw, LinkAction.Close, LinkPolicy.instagram(raw))
        }
    }

    @Test
    fun instagramPostsAndStoriesStay() {
        assertNull(LinkPolicy.instagram("https://www.instagram.com/p/CxYz/"))
        assertNull(LinkPolicy.instagram("https://www.instagram.com/stories/name/1"))
        assertNull(LinkPolicy.instagram("https://www.instagram.com/"))
        assertNull(LinkPolicy.instagram("instagram://story/name"))
        assertNull(LinkPolicy.instagram(null))
    }
}
