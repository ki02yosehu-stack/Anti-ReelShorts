package com.antireelshorts.webbox

import android.net.Uri

/**
 * URL rules. These catch Reels / Shorts however they're reached (a link, a
 * page's own navigation, a link from another app), while hide.css in the
 * extension removes the buttons and shelves that lead to them.
 */
object Rules {
    sealed interface Decision {
        data object Allow : Decision
        data class Redirect(val url: String) : Decision
        data object Block : Decision
    }

    private val SHORTS = Regex("""^/shorts/([A-Za-z0-9_-]+)""")
    private val INSTAGRAM_REELS_TAB = Regex("""^/reels(/.*)?$""")
    private val INSTAGRAM_PROFILE_REELS = Regex("""^/[^/]+/reels/?$""")

    /** @param previousUrl the page the user was on before navigating to [url]. */
    fun check(url: String, previousUrl: String?): Decision {
        val uri = Uri.parse(url)
        val host = uri.host ?: return Decision.Allow
        val path = uri.path.orEmpty()

        if (isYouTube(host)) {
            // Shorts play as normal videos instead.
            SHORTS.find(path)?.let {
                return Decision.Redirect("https://m.youtube.com/watch?v=${it.groupValues[1]}")
            }
        }

        if (isInstagram(host)) {
            if (INSTAGRAM_REELS_TAB.containsMatchIn(path)) return Decision.Block
            if (INSTAGRAM_PROFILE_REELS.containsMatchIn(path)) return Decision.Block
            // A single reel is fine when it was opened from a DM.
            if (path.startsWith("/reel/") && !isInstagramDm(previousUrl)) return Decision.Block
        }

        return Decision.Allow
    }

    /** Turns a link opened from another app into the page to load, and the tab to show it in. */
    fun fromExternalLink(uri: Uri): Pair<Site, String>? {
        val host = uri.host ?: return null
        return when {
            host == "youtu.be" -> uri.lastPathSegment?.let { Site.YOUTUBE to "https://m.youtube.com/watch?v=$it" }
            isYouTube(host) -> Site.YOUTUBE to uri.toString()
            isInstagram(host) -> Site.INSTAGRAM to uri.toString()
            else -> null
        }
    }

    private fun isInstagramDm(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return isInstagram(uri.host.orEmpty()) && uri.path.orEmpty().startsWith("/direct/")
    }

    private fun isYouTube(host: String) = host == "youtube.com" || host.endsWith(".youtube.com")

    private fun isInstagram(host: String) = host == "instagram.com" || host.endsWith(".instagram.com")
}

enum class Site(val homeUrl: String) {
    INSTAGRAM("https://www.instagram.com/"),
    YOUTUBE("https://m.youtube.com/"),
}
