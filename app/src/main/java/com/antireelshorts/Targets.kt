package com.antireelshorts

/**
 * What to erase in each app.
 *
 * View ids are internal to Instagram / YouTube and can change when those apps
 * update. Class names are obfuscated, so they are intentionally not listed.
 * If a surface comes back after an update, these lists are the place to fix it.
 */
data class Target(
    val packageName: String,
    /** Shown in the settings screen. */
    val label: String,
    /** Preference key that turns this target on or off. */
    val prefKey: String,
    /** Ids of views that fill the screen only while the short-form player is up. */
    val viewerIds: List<String>,
    /** Ids of the bottom-bar button that opens the short-form tab. */
    val tabIds: List<String>,
    /** Exact content descriptions of that button. */
    val tabDescriptions: List<String>,
    /** Ids of a shelf or card that is the short-form row in a feed. */
    val shelfIds: List<String>,
    /** Exact text or description of a shelf header or a short-form card. */
    val shelfDescriptions: List<String>,
)

object Targets {
    val YOUTUBE = Target(
        packageName = "com.google.android.youtube",
        label = "YouTube Shorts",
        prefKey = Prefs.KEY_BLOCK_YOUTUBE,
        viewerIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "shorts_container",
        ),
        tabIds = emptyList(),
        tabDescriptions = listOf("Shorts", "ショート"),
        shelfIds = listOf(
            "reel_shelf_container",
            "shorts_shelf",
            "reel_multiple_items_shelf",
            "shorts_shelf_item",
        ),
        shelfDescriptions = listOf("Shorts", "ショート"),
    )

    val INSTAGRAM = Target(
        packageName = "com.instagram.android",
        label = "Instagram リール",
        prefKey = Prefs.KEY_BLOCK_INSTAGRAM,
        viewerIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_viewer_fragment_container",
        ),
        tabIds = listOf("clips_tab"),
        tabDescriptions = listOf("Reels", "リール"),
        shelfIds = listOf(
            "clips_preview",
            "reel_preview",
            "clips_item_container",
        ),
        shelfDescriptions = listOf("Reels", "リール"),
    )

    private val all = listOf(YOUTUBE, INSTAGRAM)

    fun forPackage(packageName: String?): Target? = all.firstOrNull { it.packageName == packageName }
}
