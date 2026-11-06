package com.antireelshorts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Auto mode: keeps [BlockerService] on only while Instagram or YouTube is in
 * the foreground, so apps that refuse to run with an accessibility service
 * enabled (e.g. PayPay) keep working.
 *
 * The accessibility service can't watch for app launches while it is off, so
 * this foreground service polls the usage-stats event log instead.
 */
class WatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private lateinit var usageStats: UsageStatsManager
    private lateinit var power: PowerManager
    private var queriedUntil = 0L
    private var foreground: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        if (!AutoToggle.isActive(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    private fun poll() {
        if (!power.isInteractive) return

        val now = System.currentTimeMillis()
        // On the first poll, look back far enough to find the current app. Later
        // polls overlap a little in case an event was logged late; re-reading
        // the same events is harmless because only the latest one counts.
        val since = if (queriedUntil == 0L) now - LOOKBACK_MS else queriedUntil - OVERLAP_MS
        val events = usageStats.queryEvents(since, now)
        queriedUntil = now

        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = event.packageName
        }
        if (latest == null || latest == foreground) return
        foreground = latest

        // Only act when the foreground app changes, so that BlockerService
        // turning itself off (see BlockerService.check) isn't undone here.
        if (latest == packageName) return
        val target = Targets.forPackage(latest)
        val wanted = target != null && Prefs.isOn(this, target.prefKey)
        if (wanted != AutoToggle.isServiceEnabled(this)) {
            AutoToggle.setServiceEnabled(this, wanted)
        }
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.auto_channel_name), NotificationManager.IMPORTANCE_MIN
            )
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.auto_notification_title))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "auto_mode"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 500L
        private const val LOOKBACK_MS = 60_000L
        private const val OVERLAP_MS = 5_000L

        /** Starts or stops the watcher to match the current settings. */
        fun sync(context: Context) {
            val intent = Intent(context, WatcherService::class.java)
            if (AutoToggle.isActive(context)) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
