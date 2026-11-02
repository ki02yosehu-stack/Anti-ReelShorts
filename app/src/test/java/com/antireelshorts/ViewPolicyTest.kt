package com.antireelshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPolicyTest {
    private val youtube = Targets.YOUTUBE
    private val instagram = Targets.INSTAGRAM

    @Test
    fun bottomShortsButtonIsTheTab() {
        val facts = facts(
            contentDescription = "Shorts",
            widthFraction = 0.2f,
            heightFraction = 0.08f,
            topFraction = 0.92f,
        )
        assertEquals(ViewPolicy.Role.Tab, ViewPolicy.role(facts, youtube))
    }

    @Test
    fun shortsHeadingInTheFeedIsAShelf() {
        val facts = facts(
            text = "ショート",
            widthFraction = 0.4f,
            heightFraction = 0.04f,
            topFraction = 0.2f,
        )
        assertEquals(ViewPolicy.Role.Shelf, ViewPolicy.role(facts, youtube))
    }

    @Test
    fun videoTitleThatMentionsShortsIsKept() {
        val facts = facts(text = "My favorite Shorts compilation", heightFraction = 0.2f)
        assertEquals(ViewPolicy.Role.None, ViewPolicy.role(facts, youtube))
    }

    @Test
    fun fullScreenPlayerClosesAndASmallCopyCollapses() {
        val full = facts(resourceName = "reel_watch_player", heightFraction = 0.95f)
        val shelf = facts(resourceName = "reel_watch_player", heightFraction = 0.2f)
        assertEquals(ViewPolicy.Role.Viewer, ViewPolicy.role(full, youtube))
        assertEquals(ViewPolicy.Role.Shelf, ViewPolicy.role(shelf, youtube))
    }

    @Test
    fun instagramTabIdIsRemovedWhereverItSits() {
        val facts = facts(resourceName = "clips_tab", topFraction = 0.1f, heightFraction = 0.08f)
        assertEquals(ViewPolicy.Role.Tab, ViewPolicy.role(facts, instagram))
    }

    @Test
    fun shelfIdIsARowAndANormalPostIsNot() {
        assertEquals(
            ViewPolicy.Role.Shelf,
            ViewPolicy.role(facts(resourceName = "shorts_shelf", heightFraction = 0.3f), youtube),
        )
        assertEquals(
            ViewPolicy.Role.None,
            ViewPolicy.role(facts(resourceName = "video_title", text = "A normal video"), youtube),
        )
    }

    @Test
    fun feedShelfCollapsesTheRowNotTheList() {
        val chain = listOf(
            node(0.03f, parentChildCount = 2),
            node(0.04f, parentChildCount = 2),
            node(0.25f, parentChildCount = 8),
            node(0.85f, parentChildCount = 1),
        )
        assertEquals(2, ViewPolicy.collapseHostIndex(chain))
    }

    @Test
    fun profileReelTabDoesNotSwallowTheGrid() {
        val chain = listOf(
            node(0.02f, parentChildCount = 1),
            node(0.06f, parentChildCount = 3),
            node(0.08f, parentChildCount = 2),
            node(0.70f, parentChildCount = 2),
        )
        assertEquals(1, ViewPolicy.collapseHostIndex(chain))
    }

    @Test
    fun tallCardCollapsesItself() {
        val chain = listOf(
            node(0.85f, parentChildCount = 4),
            node(0.95f, parentChildCount = 1),
        )
        assertEquals(0, ViewPolicy.collapseHostIndex(chain))
    }

    @Test
    fun unmeasuredAndFullScreenChainsAreLeftAlone() {
        assertNull(ViewPolicy.collapseHostIndex(listOf(node(0f, 1), node(0f, 1))))
        assertNull(ViewPolicy.collapseHostIndex(listOf(node(0.95f, 1))))
        assertNull(ViewPolicy.collapseHostIndex(emptyList()))
    }

    @Test
    fun equalFixedTabsWithAGapAreRedistributed() {
        val children = listOf(
            ViewPolicy.BarChild(gone = true, width = 100, layoutWidth = 100),
            ViewPolicy.BarChild(gone = false, width = 100, layoutWidth = 100),
            ViewPolicy.BarChild(gone = false, width = 100, layoutWidth = 100),
            ViewPolicy.BarChild(gone = false, width = 110, layoutWidth = 100),
        )
        assertTrue(ViewPolicy.shouldRedistribute(500, children))
    }

    @Test
    fun weightedOrUnevenBarsAreNotForced() {
        val filling = listOf(
            ViewPolicy.BarChild(gone = false, width = 160, layoutWidth = 160),
            ViewPolicy.BarChild(gone = false, width = 160, layoutWidth = 160),
            ViewPolicy.BarChild(gone = false, width = 160, layoutWidth = 160),
        )
        val uneven = listOf(
            ViewPolicy.BarChild(gone = false, width = 80, layoutWidth = 80),
            ViewPolicy.BarChild(gone = false, width = 200, layoutWidth = 200),
        )
        val weighted = listOf(
            ViewPolicy.BarChild(gone = false, width = 120, layoutWidth = 0),
            ViewPolicy.BarChild(gone = false, width = 120, layoutWidth = 0),
        )
        assertFalse(ViewPolicy.shouldRedistribute(500, filling))
        assertFalse(ViewPolicy.shouldRedistribute(500, uneven))
        assertFalse(ViewPolicy.shouldRedistribute(500, weighted))
    }

    private fun facts(
        resourceName: String? = null,
        contentDescription: String? = null,
        text: String? = null,
        widthFraction: Float = 0.3f,
        heightFraction: Float = 0.1f,
        topFraction: Float = 0.3f,
    ) = ViewPolicy.Facts(resourceName, contentDescription, text, widthFraction, heightFraction, topFraction)

    private fun node(heightFraction: Float, parentChildCount: Int) =
        ViewPolicy.ChainNode(heightFraction, parentChildCount)
}
