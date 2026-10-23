package com.antireelshorts

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

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
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status).setText(
            if (isServiceEnabled()) R.string.status_on else R.string.status_off
        )
    }

    private fun bindSwitch(id: Int, key: String) {
        val switch = findViewById<Switch>(id)
        switch.isChecked = Prefs.isOn(this, key)
        switch.setOnCheckedChangeListener { _, checked ->
            Prefs.get(this).edit().putBoolean(key, checked).apply()
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { ComponentName.unflattenFromString(it) == me }
    }
}
