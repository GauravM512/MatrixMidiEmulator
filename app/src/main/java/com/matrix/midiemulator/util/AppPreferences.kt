package com.matrix.midiemulator.util

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "matrix_midi_emulator_prefs"
    private const val KEY_SHOW_FN_BUTTON = "show_fn_button"
    private const val KEY_SHOW_CONNECTION_STATUS = "show_connection_status"
    private const val KEY_SHOW_TITLE = "show_title"
    private const val KEY_SELECTED_PAGE = "selected_page"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFnVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_FN_BUTTON, true)
    }

    fun setFnVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_FN_BUTTON, visible).apply()
    }

    fun isTitleVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_TITLE, true)
    }

    fun setTitleVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_TITLE, visible).apply()
    }

    fun getSelectedPage(context: Context): Int {
        return prefs(context).getInt(KEY_SELECTED_PAGE, 8).coerceIn(1, 16)
    }

    fun setSelectedPage(context: Context, page: Int) {
        prefs(context).edit().putInt(KEY_SELECTED_PAGE, page.coerceIn(1, 16)).apply()
    }
    
    fun isConnectionStatusVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_CONNECTION_STATUS, true)
    }

    fun setConnectionStatusVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CONNECTION_STATUS, visible).apply()
    }
}
