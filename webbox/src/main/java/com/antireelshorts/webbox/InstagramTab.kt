package com.antireelshorts.webbox

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Instagram in the system WebView (Chrome's engine), which runs Instagram's
 * site faster than GeckoView. Unlike Google, Instagram allows logging in
 * from a WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
class InstagramTab(
    private val activity: Activity,
    private val host: TabHost,
) : BrowserTab(Site.INSTAGRAM) {

    override val view = FrameLayout(activity)
    private val webView = WebView(activity)
    private var url: String? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** Applies the hide rules as each page starts, before anything is drawn. */
    private val hideScript: String = run {
        val css = activity.assets.open("instagram.css").bufferedReader().use { it.readText() } +
            "\n" + RemoteRules.cached(activity)
        """
        (function () {
          var style = document.createElement("style");
          style.textContent = ${JSONObject.quote(css)};
          (document.head || document.documentElement).appendChild(style);
        })();
        """.trimIndent()
    }
    private val earlyScriptSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    override val canGoBack get() = webView.canGoBack()

    init {
        view.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // target=_blank links open in place: there's only one window.
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        if (earlyScriptSupported) {
            WebViewCompat.addDocumentStartJavaScript(
                webView, hideScript, setOf("https://www.instagram.com", "https://instagram.com")
            )
        }
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
    }

    override fun load(url: String) {
        loaded = true
        webView.loadUrl(url)
    }

    override fun goBack() = webView.goBack()

    override fun exitFullScreen() {
        customViewCallback?.onCustomViewHidden()
    }

    override fun setShown(shown: Boolean) {
        if (shown) webView.onResume() else webView.onPause()
        if (!shown) CookieManager.getInstance().flush()
    }

    override fun close() {
        CookieManager.getInstance().flush()
        webView.destroy()
    }

    /** Called from the activity's onActivityResult. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_FILE) return
        val callback = fileCallback ?: return
        fileCallback = null
        val uris = mutableListOf<Uri>()
        if (resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
            } else {
                data.data?.let { uris += it }
            }
        }
        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    private inner class Client : WebViewClient() {
        /** Full page loads: links, typed URLs, redirects. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val target = request.url.toString()
            // Pages try to hand off to the official app (intent:, instagram:).
            if (request.url.scheme !in setOf("http", "https")) return true
            return when (val decision = Rules.check(target, url)) {
                is Rules.Decision.Redirect -> {
                    view.loadUrl(decision.url)
                    true
                }
                Rules.Decision.Block -> {
                    host.onBlocked()
                    true
                }
                Rules.Decision.Allow -> false
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!earlyScriptSupported) view.evaluateJavascript(hideScript, null)
        }

        /**
         * Every URL change, including in-page navigation that never reaches
         * shouldOverrideUrlLoading (Instagram is a single-page app).
         */
        override fun doUpdateVisitedHistory(view: WebView, newUrl: String, isReload: Boolean) {
            when (Rules.check(newUrl, url)) {
                Rules.Decision.Block -> {
                    host.onBlocked()
                    if (view.canGoBack()) view.goBack() else view.loadUrl(site.homeUrl)
                }
                else -> url = newUrl
            }
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            host.onProgress(this@InstagramTab, newProgress)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView?.let { this@InstagramTab.view.removeView(it) }
            customView = view
            customViewCallback = callback
            this@InstagramTab.view.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            host.onFullScreen(true)
        }

        override fun onHideCustomView() {
            customView?.let { this@InstagramTab.view.removeView(it) }
            customView = null
            customViewCallback = null
            host.onFullScreen(false)
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val intent = params.createIntent().apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            activity.startActivityForResult(Intent.createChooser(intent, null), REQUEST_FILE)
            return true
        }
    }

    private companion object {
        // Prompts (the YouTube tab's file picker) uses 1.
        const val REQUEST_FILE = 2
    }
}
