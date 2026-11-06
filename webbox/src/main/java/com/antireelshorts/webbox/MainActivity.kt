package com.antireelshorts.webbox

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.GeckoRuntime

/**
 * A "box" for the Instagram and YouTube websites: one tab each, full screen,
 * with Reels / Shorts removed. YouTube videos continue in picture-in-picture
 * when leaving the app.
 */
class MainActivity : Activity(), TabHost {

    private lateinit var runtime: GeckoRuntime
    private lateinit var progress: ProgressBar
    private lateinit var tabBar: View
    private lateinit var prompts: Prompts

    private lateinit var instagram: InstagramTab
    private lateinit var youtube: YouTubeTab
    private lateinit var tabs: Map<Site, BrowserTab>
    private lateinit var labels: Map<Site, TextView>
    private var current: BrowserTab? = null
    private var fullScreen = false
    private var inPictureInPicture = false

    /** A link that arrived before the extension finished installing. */
    private var pendingLink: Pair<Site, String>? = null
    private var ready = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (youtube.playing) youtube.pause() else youtube.play()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progress = findViewById(R.id.progress)
        tabBar = findViewById(R.id.tab_bar)
        prompts = Prompts(this)
        runtime = GeckoRuntime.getDefault(applicationContext)
        RemoteRules.refresh(this)

        instagram = InstagramTab(this, this)
        youtube = YouTubeTab(this, runtime, prompts, this) { updatePictureInPicture() }
        tabs = mapOf(Site.INSTAGRAM to instagram, Site.YOUTUBE to youtube)
        labels = mapOf(
            Site.INSTAGRAM to findViewById(R.id.tab_instagram),
            Site.YOUTUBE to findViewById(R.id.tab_youtube),
        )
        val content = findViewById<FrameLayout>(R.id.content)
        tabs.values.forEach { tab ->
            tab.view.visibility = View.GONE
            tab.setShown(false)
            content.addView(tab.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        labels.forEach { (site, label) -> label.setOnClickListener { select(site) } }

        val filter = IntentFilter(ACTION_PIP_PLAY_PAUSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }

        pendingLink = intent?.data?.let(Rules::fromExternalLink)

        // Load YouTube only once the extension is in place, so Shorts never
        // flash up on the first page.
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept(
                { extension ->
                    extension?.let(youtube::attachExtension)
                    start()
                },
                { e ->
                    Log.e(TAG, "Couldn't install the extension", e)
                    start()
                },
            )
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
        tabs.getValue(site).load(url)
        select(site)
    }

    private fun select(site: Site) {
        val tab = tabs.getValue(site)
        if (current !== tab) {
            current?.let {
                it.view.visibility = View.GONE
                it.setShown(false)
            }
            tab.view.visibility = View.VISIBLE
            tab.setShown(true)
            current = tab
            progress.visibility = View.GONE
            getPreferences(MODE_PRIVATE).edit().putInt(KEY_TAB, site.ordinal).apply()
        }
        if (!tab.loaded) tab.load(site.homeUrl)
        labels.forEach { (labelSite, label) ->
            val selected = labelSite == site
            label.setTextColor(getColor(if (selected) R.color.tab_selected else R.color.tab_unselected))
            label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        updatePictureInPicture()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val tab = current
        when {
            fullScreen -> tab?.exitFullScreen()
            tab != null && tab.canGoBack -> tab.goBack()
            // Keep the tabs alive in the background, like an app would.
            else -> moveTaskToBack(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        prompts.onActivityResult(requestCode, resultCode, data)
        instagram.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        // Leaving without picture-in-picture, or closing its window.
        if (youtube.playing) youtube.pause()
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        tabs.values.forEach { it.close() }
        super.onDestroy()
    }

    // ---------- Picture-in-picture ----------

    private val pipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private fun wantsPictureInPicture() = current === youtube && youtube.playing

    private fun pictureInPictureParams(): PictureInPictureParams {
        val playing = youtube.playing
        val label = getString(if (playing) R.string.pip_pause else R.string.pip_play)
        val action = RemoteAction(
            Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
            label,
            label,
            PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE
            ),
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
            .apply {
                // Android 12+: go into picture-in-picture smoothly on the home gesture.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(wantsPictureInPicture())
            }
            .build()
    }

    /** Keeps the play/pause button and auto-enter in step with the video. */
    private fun updatePictureInPicture() {
        if (!pipSupported) return
        try {
            setPictureInPictureParams(pictureInPictureParams())
        } catch (e: IllegalStateException) {
            // Picture-in-picture is turned off for this app in system settings.
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !pipSupported || !wantsPictureInPicture()) return
        try {
            enterPictureInPictureMode(pictureInPictureParams())
        } catch (e: IllegalStateException) {
            // Picture-in-picture is turned off for this app in system settings.
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        youtube.setPictureInPicture(isInPictureInPictureMode)
        updateChrome()
    }

    // ---------- TabHost ----------

    override fun onBlocked() {
        Toast.makeText(this, R.string.blocked_reels, Toast.LENGTH_SHORT).show()
    }

    override fun onProgress(tab: BrowserTab, progress: Int) {
        if (current !== tab) return
        this.progress.progress = progress
        this.progress.visibility = if (progress in 1..99 && !inPictureInPicture) View.VISIBLE else View.GONE
    }

    override fun onFullScreen(enabled: Boolean) {
        fullScreen = enabled
        updateChrome()
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

    /** The tab bar and progress bar are hidden for full-screen video and picture-in-picture. */
    private fun updateChrome() {
        val hide = fullScreen || inPictureInPicture
        tabBar.visibility = if (hide) View.GONE else View.VISIBLE
        if (hide) progress.visibility = View.GONE
    }

    private companion object {
        const val TAG = "AntiReelShortsWeb"
        const val EXTENSION_URI = "resource://android/assets/extension/"
        const val EXTENSION_ID = "webbox@antireelshorts"
        const val KEY_TAB = "tab"
        const val ACTION_PIP_PLAY_PAUSE = "com.antireelshorts.webbox.PIP_PLAY_PAUSE"
    }
}
