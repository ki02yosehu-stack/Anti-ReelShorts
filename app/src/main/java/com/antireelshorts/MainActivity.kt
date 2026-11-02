package com.antireelshorts

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.open_manager).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage(LSPOSED_MANAGER)
            if (launch != null) {
                startActivity(launch)
            } else {
                Toast.makeText(this, R.string.manager_missing, Toast.LENGTH_LONG).show()
            }
        }

        bindSwitch(R.id.block_youtube, Prefs.KEY_BLOCK_YOUTUBE)
        bindSwitch(R.id.block_instagram, Prefs.KEY_BLOCK_INSTAGRAM)
    }

    private fun bindSwitch(id: Int, key: String) {
        val switch = findViewById<Switch>(id)
        switch.isChecked = Prefs.isOn(this, key)
        switch.setOnCheckedChangeListener { _, checked ->
            Prefs.set(this, key, checked)
        }
    }

    private companion object {
        const val LSPOSED_MANAGER = "org.lsposed.manager"
    }
}
