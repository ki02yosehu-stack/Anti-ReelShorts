package com.antireelshorts

/**
 * Turns short-form URLs into the action that erases that player.
 * YouTube Shorts with a video id become a normal watch URL. Everything else
 * that is a Reel or a Shorts destination is closed.
 */
object LinkPolicy {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val shortsMarker = Regex("(?i)(?:^|/)shorts(?:/|$)")
    private val instagramScheme = Regex("(?i)^instagram://reels?(?:/|\\?|#|$)")

    fun youtube(raw: String?): LinkAction? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        val youtube = "youtube.com" in lower ||
            "youtube-nocookie.com" in lower ||
            lower.startsWith("vnd.youtube:") ||
            lower.startsWith("youtube:")
        if (!youtube) return null
        val noQuery = trimmed.substringBefore('?').substringBefore('#')
        val match = shortsMarker.find(noQuery) ?: return null
        val after = noQuery.substring(match.range.last + 1).trim('/')
        val id = after.substringBefore('/').substringBefore('&')
        return if (videoId.matches(id)) {
            LinkAction.OpenWatch("https://www.youtube.com/watch?v=$id")
        } else {
            LinkAction.Close
        }
    }

    fun instagram(raw: String?): LinkAction? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (instagramScheme.containsMatchIn(trimmed)) return LinkAction.Close
        if (!trimmed.contains("instagram.com", ignoreCase = true)) return null
        val noQuery = trimmed.substringBefore('#').substringBefore('?')
        val afterHost = noQuery.substringAfter("instagram.com")
        val segments = afterHost.split('/').filter { it.isNotEmpty() }
        val reel = segments.any { it.equals("reel", ignoreCase = true) || it.equals("reels", ignoreCase = true) }
        return if (reel) LinkAction.Close else null
    }
}
