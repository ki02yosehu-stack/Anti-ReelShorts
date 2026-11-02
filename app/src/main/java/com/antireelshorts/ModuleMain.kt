package com.antireelshorts

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry. Runs inside the official YouTube and Instagram processes,
 * which is the same place a browser extension's content script runs.
 */
class ModuleMain : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Skip crash-report and other secondary processes. The UI lives in the main one.
        if (lpparam.processName != lpparam.packageName) return
        val target = Targets.forPackage(lpparam.packageName) ?: return
        ErasureHooks(target).install()
        XposedBridge.log("AntiReelShorts: hooked ${target.packageName}")
    }
}
