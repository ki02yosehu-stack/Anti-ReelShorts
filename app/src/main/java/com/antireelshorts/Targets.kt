package com.antireelshorts

/**
 * What to look for in each blocked app.
 *
 * View IDs are internal to Instagram / YouTube and can change when those apps
 * update. If blocking stops working after an update, these lists are the
 * place to fix it.
 */
data class Target(
    val packageName: String,
    /** Shown in the "blocked" toast. */
    val label: String,
    /** Preference key that turns this target on/off. */
    val prefKey: String,
    /** IDs of views that only exist while the short-video player is on screen. */
    val viewerIds: List<String>,
    /** IDs of the bottom-navigation button that opens the short-video tab. */
    val tabIds: List<String>,
    /** Content descriptions of that button, for app versions without a stable ID. */
    val tabDescriptions: List<String>,
)

object Targets {
    val YOUTUBE = Target(
        packageName = "com.google.android.youtube",
        label = "Shorts",
        prefKey = Prefs.KEY_BLOCK_YOUTUBE,
        viewerIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "shorts_container",
        ),
        tabIds = emptyList(),
        tabDescriptions = listOf("Shorts", "ショート"),
    )

    val INSTAGRAM = Target(
        packageName = "com.instagram.android",
        label = "リール",
        prefKey = Prefs.KEY_BLOCK_INSTAGRAM,
        viewerIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_viewer_fragment_container",
        ),
        tabIds = listOf("clips_tab"),
        tabDescriptions = listOf("Reels", "リール"),
    )

    private val all = listOf(YOUTUBE, INSTAGRAM)

    fun forPackage(packageName: String?): Target? = all.firstOrNull { it.packageName == packageName }
}
