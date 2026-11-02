package com.antireelshorts

/** What to do with a link that would open a short-form player. */
sealed class LinkAction {
    /** Open this watch URL instead of the short-form player. */
    data class OpenWatch(val url: String) : LinkAction()

    /** Do not open the screen at all. */
    data object Close : LinkAction()
}
