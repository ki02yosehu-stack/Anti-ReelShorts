package com.antireelshorts

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_BLOCK_YOUTUBE = "block_youtube"
    const val KEY_BLOCK_INSTAGRAM = "block_instagram"
    const val KEY_COVER_TABS = "cover_tabs"
    const val KEY_ALLOW_DM_REELS = "allow_dm_reels"
    const val KEY_AUTO_MODE = "auto_mode"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Every option defaults to on. */
    fun isOn(context: Context, key: String): Boolean = get(context).getBoolean(key, true)

    /** Unlike the other options, auto mode needs extra setup and so defaults to off. */
    fun isAutoMode(context: Context): Boolean = get(context).getBoolean(KEY_AUTO_MODE, false)
}
