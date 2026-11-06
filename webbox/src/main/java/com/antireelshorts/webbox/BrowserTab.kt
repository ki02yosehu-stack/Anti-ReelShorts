package com.antireelshorts.webbox

import android.content.Context
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** One tab's page. Instagram and YouTube use different browser engines. */
abstract class BrowserTab(val site: Site) {
    abstract val view: View
    abstract val canGoBack: Boolean

    /** Set once the tab has started loading something, so selecting it doesn't reload. */
    var loaded = false

    abstract fun load(url: String)
    abstract fun goBack()
    abstract fun exitFullScreen()

    /** A hidden tab is paused so it doesn't keep running scripts and video. */
    abstract fun setShown(shown: Boolean)

    abstract fun close()
}

/** What a tab needs from the activity around it. */
interface TabHost {
    fun onBlocked()
    fun onFullScreen(enabled: Boolean)
    fun onProgress(tab: BrowserTab, progress: Int)
}

/**
 * Extra hide rules from webbox/rules/remote.css in the repository, so a rule
 * that stops working can be fixed without reinstalling. The YouTube tab's
 * extension downloads them itself; this copy is for the Instagram tab.
 */
object RemoteRules {
    private const val URL_RULES =
        "https://raw.githubusercontent.com/ki02yosehu-stack/Anti-ReelShorts/main/webbox/rules/remote.css"
    private const val PREFS = "remote_rules"
    private const val KEY_CSS = "css"

    /** The rules from the last successful download (used from the next page load on). */
    fun cached(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CSS, "").orEmpty()

    fun refresh(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        thread(isDaemon = true) {
            try {
                val connection = URL(URL_RULES).openConnection() as HttpURLConnection
                connection.useCaches = false
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val css = connection.inputStream.bufferedReader().use { it.readText() }
                    prefs.edit().putString(KEY_CSS, css).apply()
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Offline: keep the last rules we downloaded.
            }
        }
    }
}
