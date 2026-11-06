package com.antireelshorts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts auto mode after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> try {
                WatcherService.sync(context)
            } catch (e: IllegalStateException) {
                // Background start refused; opening the app starts it again.
            }
        }
    }
}
