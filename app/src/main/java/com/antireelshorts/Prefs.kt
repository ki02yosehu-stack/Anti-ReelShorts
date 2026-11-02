package com.antireelshorts

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object Prefs {
    const val PACKAGE = "com.antireelshorts"
    const val FILE = "settings"

    const val KEY_BLOCK_YOUTUBE = "block_youtube"
    const val KEY_BLOCK_INSTAGRAM = "block_instagram"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Every option defaults to on. */
    fun isOn(context: Context, key: String): Boolean = get(context).getBoolean(key, true)

    /** commit() so a running YouTube or Instagram process can reload the file. */
    fun set(context: Context, key: String, value: Boolean) {
        get(context).edit().putBoolean(key, value).commit()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.setExecutable(true, false)
        dir.setReadable(true, false)
        File(dir, "$FILE.xml").setReadable(true, false)
    }
}
