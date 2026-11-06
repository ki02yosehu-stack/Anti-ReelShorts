package com.antireelshorts

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        bindSwitch(R.id.block_youtube, Prefs.KEY_BLOCK_YOUTUBE)
        bindSwitch(R.id.block_instagram, Prefs.KEY_BLOCK_INSTAGRAM)
        bindSwitch(R.id.cover_tabs, Prefs.KEY_COVER_TABS)
        bindSwitch(R.id.allow_dm_reels, Prefs.KEY_ALLOW_DM_REELS)

        val autoSwitch = findViewById<Switch>(R.id.auto_mode)
        autoSwitch.isChecked = Prefs.isAutoMode(this)
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.get(this).edit().putBoolean(Prefs.KEY_AUTO_MODE, checked).apply()
            if (checked) {
                requestNotificationPermission()
            } else if (AutoToggle.canWriteSecureSettings(this)) {
                // Leaving auto mode means "block all the time" again.
                AutoToggle.setServiceEnabled(this, true)
            }
            WatcherService.sync(this)
            updateStatus()
        }

        findViewById<Button>(R.id.auto_usage_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<TextView>(R.id.auto_adb_command).text = AutoToggle.ADB_COMMAND
        findViewById<Button>(R.id.auto_copy).setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("adb", AutoToggle.ADB_COMMAND))
            Toast.makeText(this, R.string.auto_copied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up permissions granted while we were in the background.
        WatcherService.sync(this)
        updateStatus()
    }

    private fun updateStatus() {
        findViewById<TextView>(R.id.status).setText(
            when {
                AutoToggle.isActive(this) -> R.string.status_auto
                AutoToggle.isServiceEnabled(this) -> R.string.status_on
                else -> R.string.status_off
            }
        )

        val usageOk = AutoToggle.hasUsageAccess(this)
        findViewById<TextView>(R.id.auto_usage_status).setText(
            if (usageOk) R.string.auto_usage_ok else R.string.auto_usage_ng
        )
        findViewById<Button>(R.id.auto_usage_button).visibility =
            if (usageOk) View.GONE else View.VISIBLE

        val adbOk = AutoToggle.canWriteSecureSettings(this)
        findViewById<TextView>(R.id.auto_adb_status).setText(
            if (adbOk) R.string.auto_adb_ok else R.string.auto_adb_ng
        )
        findViewById<View>(R.id.auto_adb_section).visibility =
            if (adbOk) View.GONE else View.VISIBLE
    }

    private fun bindSwitch(id: Int, key: String) {
        val switch = findViewById<Switch>(id)
        switch.isChecked = Prefs.isOn(this, key)
        switch.setOnCheckedChangeListener { _, checked ->
            Prefs.get(this).edit().putBoolean(key, checked).apply()
        }
    }

    /** Without it the auto-mode notification is hidden, but auto mode still works. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
