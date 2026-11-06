package com.antireelshorts

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Turns [BlockerService] on and off by writing the system's list of enabled
 * accessibility services. This needs WRITE_SECURE_SETTINGS, which only adb can
 * grant:
 *
 *     adb shell pm grant com.antireelshorts android.permission.WRITE_SECURE_SETTINGS
 */
object AutoToggle {
    const val ADB_COMMAND =
        "adb shell pm grant com.antireelshorts android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Auto mode is switched on and has everything it needs to work. */
    fun isActive(context: Context): Boolean =
        Prefs.isAutoMode(context) && canWriteSecureSettings(context) && hasUsageAccess(context)

    fun isServiceEnabled(context: Context): Boolean =
        enabledServices(context).contains(component(context))

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        val me = component(context)
        val services = enabledServices(context).filter { it != me }.toMutableList()
        if (enabled) services += me
        // Other apps' accessibility services in the list are kept as they are.
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(":") { it.flattenToString() },
        )
        if (enabled) {
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
    }

    private fun component(context: Context) = ComponentName(context, BlockerService::class.java)

    private fun enabledServices(context: Context): List<ComponentName> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
}
