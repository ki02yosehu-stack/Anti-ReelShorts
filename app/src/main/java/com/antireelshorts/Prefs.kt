package com.antireelshorts

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_BLOCK_YOUTUBE = "block_youtube"
    const val KEY_BLOCK_INSTAGRAM = "block_instagram"
    const val KEY_COVER_TABS = "cover_tabs"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Every option defaults to on. */
    fun isOn(context: Context, key: String): Boolean = get(context).getBoolean(key, true)
}
