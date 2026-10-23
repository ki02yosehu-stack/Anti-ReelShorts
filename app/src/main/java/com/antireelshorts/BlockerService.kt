package com.antireelshorts

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast

/**
 * Watches the YouTube and Instagram apps. When the Shorts / Reels player is on
 * screen it presses Back, and it draws a cover over the bottom tab button so
 * the short-video tab can't be opened in the first place.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { check() }
    private var checkScheduled = false

    private var lastBackAt = 0L
    private var lastToastAt = 0L

    // Where the current short-video player was opened from, for allowedOriginIds.
    private var statePackage: String? = null
    private var viewerOpen = false
    private var viewerAllowed = false
    private var inAllowedOrigin = false

    private lateinit var windowManager: WindowManager
    private val covers = mutableListOf<View>()
    private val coveredRects = mutableListOf<Rect>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        // Ignore unrelated apps unless a cover is showing and might need removing.
        if (Targets.forPackage(pkg) == null && covers.isEmpty() &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // Content-changed events fire constantly while videos play; coalesce them.
        if (!checkScheduled) {
            checkScheduled = true
            handler.postDelayed(checkRunnable, CHECK_DELAY_MS)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        removeCovers()
        super.onDestroy()
    }

    private fun check() {
        checkScheduled = false
        val root = rootInActiveWindow
        val target = Targets.forPackage(root?.packageName?.toString())
        if (root == null || target == null || !Prefs.isOn(this, target.prefKey)) {
            removeCovers()
            return
        }

        if (target.packageName != statePackage) {
            statePackage = target.packageName
            viewerOpen = false
            inAllowedOrigin = false
        }

        val tabRects = findTabRects(root, target)

        if (target.viewerIds.any { isVisible(root, target.packageName, it) }) {
            if (!viewerOpen) {
                // Decide once per opening, based on the screen it was opened from.
                viewerOpen = true
                viewerAllowed = inAllowedOrigin &&
                    target.allowOriginPrefKey?.let { Prefs.isOn(this, it) } == true
            }
            if (!viewerAllowed) goBack(target)
        } else {
            viewerOpen = false
            // Only a positive sighting changes this, so blank frames during the
            // opening animation don't reset it. Seeing the bottom tab bar means
            // we're back on a main screen.
            if (target.allowedOriginIds.any { isVisible(root, target.packageName, it) }) {
                inAllowedOrigin = true
            } else if (tabRects.isNotEmpty()) {
                inAllowedOrigin = false
            }
        }

        if (Prefs.isOn(this, Prefs.KEY_COVER_TABS)) {
            showCovers(tabRects)
        } else {
            removeCovers()
        }
    }

    private fun isVisible(root: AccessibilityNodeInfo, pkg: String, id: String): Boolean =
        root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }

    private fun goBack(target: Target) {
        val now = SystemClock.uptimeMillis()
        // Give the app time to leave the screen before pressing Back again.
        if (now - lastBackAt < BACK_COOLDOWN_MS) return
        lastBackAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)

        if (now - lastToastAt > TOAST_COOLDOWN_MS) {
            lastToastAt = now
            Toast.makeText(this, getString(R.string.blocked_toast, target.label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun findTabRects(root: AccessibilityNodeInfo, target: Target): List<Rect> {
        val screenHeight = resources.displayMetrics.heightPixels
        val nodes = target.tabIds.flatMap {
            root.findAccessibilityNodeInfosByViewId("${target.packageName}:id/$it")
        }.ifEmpty {
            // findAccessibilityNodeInfosByText also matches substrings of video titles,
            // so require an exact description and a position in the bottom bar.
            target.tabDescriptions.flatMap { desc ->
                root.findAccessibilityNodeInfosByText(desc).filter {
                    it.contentDescription?.toString().equals(desc, ignoreCase = true) ||
                        it.text?.toString().equals(desc, ignoreCase = true)
                }
            }
        }
        val rects = nodes
            .filter { it.isVisibleToUser }
            .map { node -> Rect().also { node.getBoundsInScreen(it) } }
            .filter { !it.isEmpty && it.top > screenHeight * 3 / 4 }
            .distinct()
        // A tab's label and its button can both match; keep only the outer one.
        return rects.filter { inner -> rects.none { it != inner && it.contains(inner) } }
    }

    private fun showCovers(rects: List<Rect>) {
        if (rects == coveredRects) return
        removeCovers()
        for (rect in rects) {
            val view = TextView(this).apply {
                text = "🚫"
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(235, 40, 40, 40))
                // Swallow taps so the tab underneath can't be pressed.
                setOnClickListener {}
            }
            val params = WindowManager.LayoutParams(
                rect.width(),
                rect.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = rect.left
                y = rect.top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            try {
                windowManager.addView(view, params)
                covers += view
            } catch (e: RuntimeException) {
                // The window token can be gone if the service is being torn down.
            }
        }
        coveredRects.clear()
        coveredRects += rects
    }

    private fun removeCovers() {
        for (view in covers) {
            try {
                windowManager.removeView(view)
            } catch (e: RuntimeException) {
                // Already detached.
            }
        }
        covers.clear()
        coveredRects.clear()
    }

    private companion object {
        const val CHECK_DELAY_MS = 150L
        const val BACK_COOLDOWN_MS = 800L
        const val TOAST_COOLDOWN_MS = 5_000L
    }
}
