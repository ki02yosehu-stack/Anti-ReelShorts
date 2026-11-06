package com.antireelshorts.webbox

import android.app.Activity
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/**
 * YouTube in GeckoView (Firefox's engine): Google refuses to let you log in
 * from a WebView, but allows Firefox. The built-in extension hides Shorts and
 * talks to the app for picture-in-picture (extension/player.js).
 */
class YouTubeTab(
    activity: Activity,
    private val runtime: GeckoRuntime,
    prompts: Prompts,
    private val host: TabHost,
    /** Called when a video starts or stops playing. */
    private val onPlayingChanged: (Boolean) -> Unit,
) : BrowserTab(Site.YOUTUBE) {

    private val geckoView = GeckoView(activity)
    override val view = geckoView

    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
    )
    private var url: String? = null
    private var backAvailable = false
    private var port: WebExtension.Port? = null

    var playing = false
        private set

    override val canGoBack get() = backAvailable

    init {
        session.navigationDelegate = Navigation()
        session.contentDelegate = Content()
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                host.onProgress(this@YouTubeTab, progress)
            }
        }
        session.promptDelegate = prompts
        session.open(runtime)
        geckoView.setSession(session)
    }

    /** Lets the extension's content script on this tab's pages talk to us. */
    fun attachExtension(extension: WebExtension) {
        session.webExtensionController.setMessageDelegate(extension, Messages(), NATIVE_APP)
    }

    override fun load(url: String) {
        loaded = true
        session.loadUri(url)
    }

    override fun goBack() = session.goBack()

    override fun exitFullScreen() = session.exitFullScreen()

    override fun setShown(shown: Boolean) = session.setActive(shown)

    override fun close() = session.close()

    /** Shows only the player, for the small picture-in-picture window. */
    fun setPictureInPicture(enabled: Boolean) {
        port?.postMessage(JSONObject().put("pip", enabled))
    }

    fun play() {
        port?.postMessage(JSONObject().put("command", "play"))
    }

    fun pause() {
        port?.postMessage(JSONObject().put("command", "pause"))
    }

    private fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        onPlayingChanged(value)
    }

    private inner class Messages : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            this@YouTubeTab.port = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message is JSONObject && message.has("playing")) {
                        setPlaying(message.getBoolean("playing"))
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    // The page was left or reloaded; its replacement connects again.
                    if (this@YouTubeTab.port === port) {
                        this@YouTubeTab.port = null
                        setPlaying(false)
                    }
                }
            })
        }
    }

    private inner class Navigation : NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            backAvailable = canGoBack
        }

        /** Full page loads: links, typed URLs, redirects. */
        override fun onLoadRequest(
            session: GeckoSession,
            request: NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny> {
            val scheme = request.uri.substringBefore(':').lowercase()
            // Pages try to hand off to the official app (intent:, vnd.youtube:).
            if (scheme !in ALLOWED_SCHEMES) return GeckoResult.deny()

            when (val decision = Rules.check(request.uri, url)) {
                is Rules.Decision.Redirect -> {
                    session.loadUri(decision.url)
                    return GeckoResult.deny()
                }
                Rules.Decision.Block -> {
                    host.onBlocked()
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
         * onLoadRequest (YouTube is a single-page app).
         */
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url == null) return
            when (val decision = Rules.check(url, this@YouTubeTab.url)) {
                is Rules.Decision.Redirect -> session.load(
                    GeckoSession.Loader()
                        .uri(decision.url)
                        // Otherwise Back would return to the Short and redirect again.
                        .flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
                )
                Rules.Decision.Block -> {
                    host.onBlocked()
                    session.goBack()
                }
                Rules.Decision.Allow -> this@YouTubeTab.url = url
            }
        }
    }

    private inner class Content : GeckoSession.ContentDelegate {
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            host.onFullScreen(fullScreen)
        }

        // The page's process died (e.g. low memory): reopen where we were.
        override fun onCrash(session: GeckoSession) = reopen(session)

        override fun onKill(session: GeckoSession) = reopen(session)

        private fun reopen(session: GeckoSession) {
            if (!session.isOpen) session.open(runtime)
            session.loadUri(url ?: site.homeUrl)
        }
    }

    private companion object {
        /** Must match runtime.connectNative() in extension/player.js. */
        const val NATIVE_APP = "webbox"
        val ALLOWED_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")
    }
}
