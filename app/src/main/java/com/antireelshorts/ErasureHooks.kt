package com.antireelshorts

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hides Shorts / Reels inside the app that owns them: the tab is GONE, the
 * feed row's height becomes 0, and links never open the vertical player.
 */
class ErasureHooks(private val target: Target) {
    private val prefs = ModulePrefs()
    private val gate = Any()
    private val scheduled = WeakHashMap<Activity, Boolean>()
    private val scanning = WeakHashMap<Activity, Boolean>()
    private val dirty = WeakHashMap<Activity, Boolean>()

    fun install() {
        hook("android.app.Activity", "onCreate", Bundle::class.java, launchHook())
        hook("android.app.Activity", "onNewIntent", Intent::class.java, launchHook(fromArgs = true))
        hook("android.app.Activity", "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (activity.packageName != target.packageName) return
                schedule(activity)
            }
        })
        hook(
            "android.app.Activity",
            "startActivityForResult",
            Intent::class.java,
            Int::class.javaPrimitiveType!!,
            Bundle::class.java,
            outgoingHook(),
        )
        hook(
            "android.app.ContextImpl",
            "startActivity",
            Intent::class.java,
            Bundle::class.java,
            outgoingHook(),
        )
        hook(
            "android.view.ViewGroup",
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType!!,
            ViewGroup.LayoutParams::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val child = param.args.getOrNull(0) as? View ?: return
                    val activity = activityOf(child) ?: return
                    if (activity.packageName != target.packageName) return
                    schedule(activity)
                }
            },
        )
        // A recycled row often stays attached and only changes its text.
        hook(
            "android.widget.TextView",
            "setText",
            CharSequence::class.java,
            TextView.BufferType::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? TextView ?: return
                    if (view.context.packageName != target.packageName) return
                    val text = (param.args.getOrNull(0) as? CharSequence)?.toString()
                    if (!needsRescan(view, text)) return
                    val activity = activityOf(view) ?: return
                    schedule(activity)
                }
            },
        )
    }

    /** onCreate carries the intent on the activity; onNewIntent passes it as the argument. */
    private fun launchHook(fromArgs: Boolean = false) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as? Activity ?: return
            if (activity.packageName != target.packageName) return
            val intent = if (fromArgs) param.args.getOrNull(0) as? Intent else activity.intent
            rewriteIncoming(activity, intent)
        }
    }

    private fun outgoingHook() = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            try {
                val context = param.thisObject as? Context
                val intent = param.args.getOrNull(0) as? Intent
                if (context != null && intent != null &&
                    context.packageName == target.packageName &&
                    prefs.isOn(target.prefKey) &&
                    cancelOutgoing(intent)
                ) {
                    return null
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
        }
    }

    private fun rewriteIncoming(activity: Activity, intent: Intent?) {
        if (intent == null || !prefs.isOn(target.prefKey)) return
        val data = intent.dataString ?: return
        when (target.packageName) {
            Targets.YOUTUBE.packageName -> {
                val action = LinkPolicy.youtube(data)
                if (action is LinkAction.OpenWatch) {
                    intent.action = Intent.ACTION_VIEW
                    intent.data = Uri.parse(action.url)
                    intent.setPackage(target.packageName)
                }
            }
            Targets.INSTAGRAM.packageName -> {
                if (LinkPolicy.instagram(data) == LinkAction.Close) finishQuietly(activity)
            }
        }
    }

    /** @return true when this launch should be dropped. A rewritten intent returns false. */
    private fun cancelOutgoing(intent: Intent): Boolean {
        val data = intent.dataString ?: return false
        return when (target.packageName) {
            Targets.YOUTUBE.packageName -> when (val action = LinkPolicy.youtube(data)) {
                is LinkAction.OpenWatch -> {
                    intent.action = Intent.ACTION_VIEW
                    intent.data = Uri.parse(action.url)
                    intent.setPackage(target.packageName)
                    intent.component = null
                    false
                }
                LinkAction.Close -> true
                null -> false
            }
            Targets.INSTAGRAM.packageName -> LinkPolicy.instagram(data) == LinkAction.Close
            else -> false
        }
    }

    private fun schedule(activity: Activity) {
        synchronized(gate) {
            if (scanning[activity] == true) {
                dirty[activity] = true
                return
            }
            if (scheduled[activity] == true) return
            scheduled[activity] = true
        }
        val decor = try {
            activity.window?.decorView
        } catch (_: RuntimeException) {
            null
        }
        if (decor == null) {
            synchronized(gate) { scheduled.remove(activity) }
            return
        }
        decor.postDelayed({
            synchronized(gate) { scheduled.remove(activity) }
            if (activity.isDestroyed || activity.isFinishing) return@postDelayed
            synchronized(gate) { scanning[activity] = true }
            try {
                scan(activity)
            } catch (t: Throwable) {
                XposedBridge.log(t)
            } finally {
                synchronized(gate) { scanning.remove(activity) }
            }
            val again = synchronized(gate) { dirty.remove(activity) == true }
            if (again) schedule(activity)
        }, SCAN_DELAY_MS)
    }

    private fun scan(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        val tracked = modified(decor)
        if (!prefs.isOn(target.prefKey)) {
            tracked.toList().forEach { restore(it) }
            tracked.clear()
            decor.setTag(R.id.erasure_close_attempts, 0)
            return
        }
        val keep = HashSet<View>()
        var viewer = false
        fun walk(view: View) {
            when (ViewPolicy.role(facts(view), target)) {
                ViewPolicy.Role.Tab -> {
                    hideTab(view, tracked)
                    keep.add(view)
                }
                ViewPolicy.Role.Viewer -> viewer = true
                ViewPolicy.Role.Shelf -> collapseFrom(view, decor, tracked, keep)
                ViewPolicy.Role.None -> Unit
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(decor)
        tracked.filter { it !in keep }.forEach {
            restore(it)
            tracked.remove(it)
        }
        if (viewer) closeViewer(activity, decor) else decor.setTag(R.id.erasure_close_attempts, 0)
    }

    private fun hideTab(tab: View, tracked: MutableSet<View>) {
        val parent = tab.parent as? ViewGroup
        if (tab.getTag(R.id.erasure_tab) != true) {
            tab.setTag(R.id.erasure_saved_visibility, tab.visibility)
            tab.setTag(R.id.erasure_tab, true)
            tracked.add(tab)
            parent?.let { bar ->
                for (i in 0 until bar.childCount) {
                    val child = bar.getChildAt(i)
                    if (child.getTag(R.id.erasure_saved_width) == null) {
                        child.setTag(R.id.erasure_saved_width, child.layoutParams?.width)
                    }
                }
            }
        }
        if (tab.visibility != View.GONE) tab.visibility = View.GONE
        tab.isEnabled = false
        if (parent != null) reflow(parent)
    }

    private fun reflow(parent: ViewGroup) {
        if (parent.width <= 0) return
        val children = (0 until parent.childCount).map { index ->
            val child = parent.getChildAt(index)
            ViewPolicy.BarChild(
                gone = child.visibility == View.GONE,
                width = child.width,
                layoutWidth = child.layoutParams?.width ?: 0,
            )
        }
        if (!ViewPolicy.shouldRedistribute(parent.width, children)) return
        val visible = (0 until parent.childCount).map { parent.getChildAt(it) }.filter { it.visibility != View.GONE }
        if (visible.isEmpty()) return
        val each = parent.width / visible.size
        for (child in visible) {
            val params = child.layoutParams ?: continue
            if (params.width != each) {
                params.width = each
                child.layoutParams = params
            }
        }
    }

    private fun collapseFrom(marker: View, decor: View, tracked: MutableSet<View>, keep: MutableSet<View>) {
        val screenH = marker.resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val views = chain(marker)
        val index = ViewPolicy.collapseHostIndex(nodes(views, screenH)) ?: return
        val host = views[index]
        if (host === decor) return
        when (ViewPolicy.role(facts(host), target)) {
            ViewPolicy.Role.Tab, ViewPolicy.Role.Viewer -> return
            else -> collapse(host, tracked, keep)
        }
    }

    private fun collapse(view: View, tracked: MutableSet<View>, keep: MutableSet<View>) {
        val params = view.layoutParams ?: return
        if (view.getTag(R.id.erasure_collapsed) != true) {
            view.setTag(R.id.erasure_saved_height, params.height)
            view.setTag(R.id.erasure_saved_visibility, view.visibility)
            view.setTag(R.id.erasure_collapsed, true)
            tracked.add(view)
        }
        if (params.height != 0) {
            params.height = 0
            view.layoutParams = params
        }
        if (view.visibility != View.GONE) view.visibility = View.GONE
        keep.add(view)
    }

    private fun restore(view: View) {
        val wasTab = view.getTag(R.id.erasure_tab) == true
        val wasCollapsed = view.getTag(R.id.erasure_collapsed) == true
        if (!wasTab && !wasCollapsed) return
        val visibility = view.getTag(R.id.erasure_saved_visibility) as? Int
        if (visibility != null) view.visibility = visibility
        if (wasCollapsed) {
            val height = view.getTag(R.id.erasure_saved_height) as? Int
            val params = view.layoutParams
            if (height != null && params != null) {
                params.height = height
                view.layoutParams = params
            }
        }
        if (wasTab) view.isEnabled = true
        view.setTag(R.id.erasure_tab, null)
        view.setTag(R.id.erasure_collapsed, null)
        view.setTag(R.id.erasure_saved_visibility, null)
        view.setTag(R.id.erasure_saved_height, null)
        if (!wasTab) return
        val parent = view.parent as? ViewGroup ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val width = child.getTag(R.id.erasure_saved_width) as? Int ?: continue
            val params = child.layoutParams ?: continue
            params.width = width
            child.layoutParams = params
            child.setTag(R.id.erasure_saved_width, null)
        }
        parent.requestLayout()
    }

    /**
     * A viewer inside the main activity is a fragment, so Back pops it.
     * A viewer that was opened on top of the app is its own activity.
     * Two attempts, then stop, so a failed pop cannot walk the user out of the app.
     */
    private fun closeViewer(activity: Activity, decor: View) {
        val now = SystemClock.uptimeMillis()
        val last = decor.getTag(R.id.erasure_last_close) as? Long ?: 0L
        val attempts = decor.getTag(R.id.erasure_close_attempts) as? Int ?: 0
        if (attempts >= MAX_CLOSE_ATTEMPTS || now - last < CLOSE_COOLDOWN_MS) return
        decor.setTag(R.id.erasure_close_attempts, attempts + 1)
        decor.setTag(R.id.erasure_last_close, now)
        if (activity.isTaskRoot) {
            @Suppress("DEPRECATION")
            activity.onBackPressed()
        } else {
            activity.finish()
        }
    }

    private fun facts(view: View): ViewPolicy.Facts {
        val metrics = view.resources.displayMetrics
        val screenW = metrics.widthPixels.coerceAtLeast(1)
        val screenH = metrics.heightPixels.coerceAtLeast(1)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return ViewPolicy.Facts(
            resourceName = resourceName(view),
            contentDescription = view.contentDescription?.toString(),
            text = (view as? TextView)?.text?.toString(),
            widthFraction = view.width.toFloat() / screenW,
            heightFraction = view.height.toFloat() / screenH,
            topFraction = location[1].toFloat() / screenH,
        )
    }

    private fun resourceName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun chain(start: View): List<View> {
        val views = ArrayList<View>()
        var current: View? = start
        var guard = 0
        while (current != null && guard++ < 40) {
            views.add(current)
            current = current.parent as? View
        }
        return views
    }

    private fun nodes(views: List<View>, screenH: Int): List<ViewPolicy.ChainNode> =
        views.map { view ->
            val parent = view.parent as? ViewGroup
            ViewPolicy.ChainNode(
                heightFraction = view.height.toFloat() / screenH.coerceAtLeast(1),
                parentChildCount = parent?.childCount ?: 1,
            )
        }

    @Suppress("UNCHECKED_CAST")
    private fun modified(decor: View): MutableSet<View> {
        val existing = decor.getTag(R.id.erasure_modified) as? MutableSet<View>
        if (existing != null) return existing
        val created = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
        decor.setTag(R.id.erasure_modified, created)
        return created
    }

    /** Label appeared, or a row we already erased is being rebound. */
    private fun needsRescan(view: View, text: String?): Boolean {
        val labels = target.tabDescriptions + target.shelfDescriptions
        if (ViewPolicy.matchesAnyLabel(text, labels)) return true
        var current: View? = view
        var guard = 0
        while (current != null && guard++ < 40) {
            if (current.getTag(R.id.erasure_collapsed) == true || current.getTag(R.id.erasure_tab) == true) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun finishQuietly(activity: Activity) {
        try {
            activity.setVisible(false)
        } catch (_: RuntimeException) {
        }
        activity.finish()
        try {
            activity.overridePendingTransition(0, 0)
        } catch (_: RuntimeException) {
        }
    }

    private fun hook(className: String, method: String, vararg paramsAndCallback: Any) {
        try {
            XposedHelpers.findAndHookMethod(className, null, method, *paramsAndCallback)
        } catch (t: Throwable) {
            XposedBridge.log("AntiReelShorts: hook failed for $className.$method")
            XposedBridge.log(t)
        }
    }

    private companion object {
        const val SCAN_DELAY_MS = 150L
        const val CLOSE_COOLDOWN_MS = 800L
        const val MAX_CLOSE_ATTEMPTS = 2

        fun activityOf(view: View): Activity? = activityOf(view.context)

        fun activityOf(context: Context): Activity? {
            var current: Context? = context
            var guard = 0
            while (current is ContextWrapper && guard++ < 10) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}

/** Reads this module's settings from inside YouTube or Instagram. */
private class ModulePrefs {
    private val prefs = XSharedPreferences(Prefs.PACKAGE, Prefs.FILE)

    init {
        prefs.makeWorldReadable()
    }

    fun isOn(key: String): Boolean = try {
        prefs.reload()
        prefs.getBoolean(key, true)
    } catch (t: Throwable) {
        XposedBridge.log(t)
        true
    }
}
