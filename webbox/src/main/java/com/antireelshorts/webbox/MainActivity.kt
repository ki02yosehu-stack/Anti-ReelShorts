package com.antireelshorts.webbox

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * A "box" for the Instagram and YouTube websites: one tab each, full screen,
 * with Reels / Shorts removed by the built-in extension and [Rules].
 */
class MainActivity : Activity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var progress: ProgressBar
    private lateinit var tabBar: View
    private lateinit var prompts: Prompts

    private lateinit var tabs: Map<Site, Tab>
    private var current: Tab? = null
    private var fullScreen = false
    /** A link that arrived before the extension finished installing. */
    private var pendingLink: Pair<Site, String>? = null
    private var ready = false

    private inner class Tab(val site: Site, val label: TextView) {
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build()
        )
        var canGoBack = false
        var url: String? = null
        var loaded = false

        init {
            session.navigationDelegate = Navigation(this)
            session.contentDelegate = Content(this)
            session.progressDelegate = Progress(this)
            session.promptDelegate = prompts
            session.open(runtime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        geckoView = findViewById(R.id.gecko)
        progress = findViewById(R.id.progress)
        tabBar = findViewById(R.id.tab_bar)
        prompts = Prompts(this)

        runtime = GeckoRuntime.getDefault(applicationContext)
        tabs = mapOf(
            Site.INSTAGRAM to Tab(Site.INSTAGRAM, findViewById(R.id.tab_instagram)),
            Site.YOUTUBE to Tab(Site.YOUTUBE, findViewById(R.id.tab_youtube)),
        )
        tabs.values.forEach { tab -> tab.label.setOnClickListener { select(tab.site) } }

        pendingLink = intent?.data?.let(Rules::fromExternalLink)

        // Load pages only once the extension is in place, so Reels / Shorts
        // never flash up on the first page.
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept({ start() }, { e -> Log.e(TAG, "Couldn't install the extension", e); start() })
    }

    private fun start() {
        ready = true
        val link = pendingLink
        pendingLink = null
        if (link != null) {
            open(link)
        } else {
            select(Site.entries.getOrElse(getPreferences(MODE_PRIVATE).getInt(KEY_TAB, 0)) { Site.INSTAGRAM })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = intent.data?.let(Rules::fromExternalLink) ?: return
        if (ready) open(link) else pendingLink = link
    }

    private fun open(link: Pair<Site, String>) {
        val (site, url) = link
        val tab = tabs.getValue(site)
        tab.loaded = true
        tab.session.loadUri(url)
        select(site)
    }

    private fun select(site: Site) {
        val tab = tabs.getValue(site)
        if (current !== tab) {
            current?.let {
                geckoView.releaseSession()
                // Otherwise the hidden tab keeps running scripts and playing video.
                it.session.setActive(false)
            }
            geckoView.setSession(tab.session)
            tab.session.setActive(true)
            current = tab
            getPreferences(MODE_PRIVATE).edit().putInt(KEY_TAB, site.ordinal).apply()
        }
        if (!tab.loaded) {
            tab.loaded = true
            tab.session.loadUri(site.homeUrl)
        }
        tabs.values.forEach {
            val selected = it === tab
            it.label.setTextColor(getColor(if (selected) R.color.tab_selected else R.color.tab_unselected))
            it.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val tab = current
        when {
            fullScreen -> tab?.session?.exitFullScreen()
            tab != null && tab.canGoBack -> tab.session.goBack()
            // Keep the tabs alive in the background, like an app would.
            else -> moveTaskToBack(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        prompts.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        tabs.values.forEach { it.session.close() }
        super.onDestroy()
    }

    private fun setFullScreen(enabled: Boolean) {
        fullScreen = enabled
        tabBar.visibility = if (enabled) View.GONE else View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            if (enabled) {
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (enabled) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                0
            }
        }
    }

    private fun showBlocked() {
        Toast.makeText(this, R.string.blocked_reels, Toast.LENGTH_SHORT).show()
    }

    private inner class Navigation(private val tab: Tab) : NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            tab.canGoBack = canGoBack
        }

        /** Full page loads: links, typed URLs, redirects. */
        override fun onLoadRequest(
            session: GeckoSession,
            request: NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny> {
            val scheme = request.uri.substringBefore(':').lowercase()
            // Pages try to hand off to the official apps (intent:, instagram:, vnd.youtube:).
            if (scheme !in ALLOWED_SCHEMES) return GeckoResult.deny()

            when (val decision = Rules.check(request.uri, tab.url)) {
                is Rules.Decision.Redirect -> {
                    session.loadUri(decision.url)
                    return GeckoResult.deny()
                }
                Rules.Decision.Block -> {
                    showBlocked()
                    return GeckoResult.deny()
                }
                Rules.Decision.Allow -> {}
            }

            // There's only one window per tab, so pop-ups open in place.
            if (request.target == NavigationDelegate.TARGET_WINDOW_NEW) {
                session.loadUri(request.uri)
                return GeckoResult.deny()
            }
            return GeckoResult.allow()
        }

        /**
         * Every URL change, including in-page navigation that never reaches
         * onLoadRequest (Instagram and YouTube are single-page apps).
         */
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url == null) return
            when (val decision = Rules.check(url, tab.url)) {
                is Rules.Decision.Redirect -> session.load(
                    GeckoSession.Loader()
                        .uri(decision.url)
                        // Otherwise Back would return to the Short and redirect again.
                        .flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
                )
                Rules.Decision.Block -> {
                    showBlocked()
                    if (tab.url != null) {
                        session.goBack()
                    } else {
                        session.load(
                            GeckoSession.Loader()
                                .uri(tab.site.homeUrl)
                                .flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
                        )
                    }
                }
                Rules.Decision.Allow -> tab.url = url
            }
        }
    }

    private inner class Content(private val tab: Tab) : GeckoSession.ContentDelegate {
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            setFullScreen(fullScreen)
        }

        // The page's process died (e.g. low memory): reopen where we were.
        override fun onCrash(session: GeckoSession) = reopen(session)

        override fun onKill(session: GeckoSession) = reopen(session)

        private fun reopen(session: GeckoSession) {
            if (!session.isOpen) session.open(runtime)
            session.loadUri(tab.url ?: tab.site.homeUrl)
        }
    }

    private inner class Progress(private val tab: Tab) : GeckoSession.ProgressDelegate {
        override fun onProgressChange(session: GeckoSession, progress: Int) {
            if (current !== tab) return
            this@MainActivity.progress.progress = progress
            this@MainActivity.progress.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        const val TAG = "AntiReelShortsWeb"
        const val EXTENSION_URI = "resource://android/assets/extension/"
        const val EXTENSION_ID = "webbox@antireelshorts"
        const val KEY_TAB = "tab"
        val ALLOWED_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")
    }
}
