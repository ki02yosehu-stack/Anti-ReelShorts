package com.antireelshorts

/**
 * Decides which views to erase. Resource names and exact labels only — class
 * names inside YouTube and Instagram are obfuscated and are not consulted.
 */
object ViewPolicy {
    enum class Role { None, Tab, Shelf, Viewer }

    data class Facts(
        val resourceName: String?,
        val contentDescription: String?,
        val text: String?,
        /** Width, height, and distance from the top of the screen, each 0..1+. */
        val widthFraction: Float,
        val heightFraction: Float,
        val topFraction: Float,
    )

    /**
     * One step from a shelf marker up toward the decor view.
     * [parentChildCount] is how many children that parent has.
     */
    data class ChainNode(val heightFraction: Float, val parentChildCount: Int)

    /** A child of the bottom bar, used to decide whether fixed widths left a gap. */
    data class BarChild(val gone: Boolean, val width: Int, val layoutWidth: Int)

    fun matchesAnyLabel(value: String?, labels: Collection<String>): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        return labels.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun role(facts: Facts, target: Target): Role {
        if (isTab(facts, target)) return Role.Tab
        val name = facts.resourceName
        if (name != null && name in target.viewerIds && facts.heightFraction >= VIEWER_HEIGHT) {
            return Role.Viewer
        }
        if (name != null && (name in target.viewerIds || name in target.shelfIds)) return Role.Shelf
        val labels = target.shelfDescriptions
        if (matchesAnyLabel(facts.contentDescription, labels) || matchesAnyLabel(facts.text, labels)) {
            return Role.Shelf
        }
        return Role.None
    }

    /**
     * Index into [chain] (index 0 is the shelf marker) of the row to collapse.
     * Null when nothing is laid out yet, or the only candidate is the screen itself.
     */
    fun collapseHostIndex(chain: List<ChainNode>): Int? {
        if (chain.isEmpty() || chain.all { it.heightFraction <= 0f }) return null
        var best = 0
        for (i in 1 until chain.size) {
            val here = chain[i].heightFraction
            if (here <= 0f || here >= SCREEN_FRACTION) break
            val prev = chain[i - 1]
            // The previous view is already one item in a list. Don't swallow the list.
            if (prev.parentChildCount >= LIST_CHILD_COUNT && prev.heightFraction > TINY_FRACTION) break
            // A big jump means this ancestor contains more than the shelf.
            if (prev.heightFraction >= ROW_FRACTION && here > prev.heightFraction * JUMP) break
            best = i
        }
        val chosen = chain[best].heightFraction
        if (chosen <= 0f || chosen >= SCREEN_FRACTION) return null
        return best
    }

    /**
     * True when hiding a tab left an equal-width bar with a gap. Weighted bars
     * already reflow when a child is GONE, and those report a non-positive
     * layout width or a sum that still fills the parent.
     */
    fun shouldRedistribute(parentWidth: Int, children: List<BarChild>): Boolean {
        if (parentWidth <= 0) return false
        val visible = children.filter { !it.gone }
        if (visible.size < 2) return false
        if (visible.any { it.layoutWidth <= 0 || it.width <= 0 }) return false
        val min = visible.minOf { it.width }
        val max = visible.maxOf { it.width }
        if (max > min * 1.15f) return false
        return visible.sumOf { it.width } < parentWidth * 0.85f
    }

    private fun isTab(facts: Facts, target: Target): Boolean {
        val name = facts.resourceName
        if (name != null && name in target.tabIds) return true
        val labels = target.tabDescriptions
        val labeled = matchesAnyLabel(facts.contentDescription, labels) ||
            matchesAnyLabel(facts.text, labels)
        return labeled &&
            facts.topFraction >= TAB_TOP &&
            facts.heightFraction <= TAB_HEIGHT &&
            facts.widthFraction <= TAB_WIDTH
    }

    private const val VIEWER_HEIGHT = 0.6f
    private const val TAB_TOP = 0.75f
    private const val TAB_HEIGHT = 0.18f
    private const val TAB_WIDTH = 0.5f
    private const val SCREEN_FRACTION = 0.9f
    private const val TINY_FRACTION = 0.05f
    private const val ROW_FRACTION = 0.12f
    private const val JUMP = 2.2f
    private const val LIST_CHILD_COUNT = 3
}
