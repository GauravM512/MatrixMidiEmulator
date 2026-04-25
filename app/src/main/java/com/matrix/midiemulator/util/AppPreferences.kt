package com.matrix.midiemulator.util

import android.content.Context

object AppPreferences {
    const val LAYOUT_MODE_MYSTRIX = 0
    const val LAYOUT_MODE_LAUNCHPAD_PRO_MK2 = 1

    private const val PREFS_NAME = "matrix_midi_emulator_prefs"
    private const val KEY_SHOW_FN_BUTTON = "show_fn_button"
    private const val KEY_SHOW_CONNECTION_STATUS = "show_connection_status"
    private const val KEY_SELECTED_PAGE = "selected_page"
    private const val KEY_LAYOUT_MODE = "layout_mode"
    private const val KEY_ACTIVE_PALETTE_SLOT = "active_palette_slot"
    private const val KEY_PALETTE_IMPORT_SLOT = "palette_import_slot"
    private const val KEY_LED_BRIGHTNESS_PERCENT = "led_brightness_percent"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedPage(context: Context): Int {
        return prefs(context).getInt(KEY_SELECTED_PAGE, 8).coerceIn(1, 16)
    }

    fun setSelectedPage(context: Context, page: Int) {
        prefs(context).edit().putInt(KEY_SELECTED_PAGE, page.coerceIn(1, 16)).apply()
    }

    fun getLayoutMode(context: Context): Int {
        return prefs(context)
            .getInt(KEY_LAYOUT_MODE, LAYOUT_MODE_MYSTRIX)
            .coerceIn(LAYOUT_MODE_MYSTRIX, LAYOUT_MODE_LAUNCHPAD_PRO_MK2)
    }

    fun setLayoutMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(
            KEY_LAYOUT_MODE,
            mode.coerceIn(LAYOUT_MODE_MYSTRIX, LAYOUT_MODE_LAUNCHPAD_PRO_MK2)
        ).apply()
    }

    fun getActivePaletteSlot(context: Context): Int {
        return prefs(context).getInt(KEY_ACTIVE_PALETTE_SLOT, 0).coerceIn(0, 4)
    }

    fun setActivePaletteSlot(context: Context, slot: Int) {
        prefs(context).edit().putInt(KEY_ACTIVE_PALETTE_SLOT, slot.coerceIn(0, 4)).apply()
    }

    fun getPaletteImportSlot(context: Context): Int {
        return prefs(context).getInt(KEY_PALETTE_IMPORT_SLOT, 1).coerceIn(1, 4)
    }

    fun setPaletteImportSlot(context: Context, slot: Int) {
        prefs(context).edit().putInt(KEY_PALETTE_IMPORT_SLOT, slot.coerceIn(1, 4)).apply()
    }

    fun getLedBrightnessPercent(context: Context): Int {
        return prefs(context).getInt(KEY_LED_BRIGHTNESS_PERCENT, 100).coerceIn(0, 200)
    }

    fun setLedBrightnessPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_LED_BRIGHTNESS_PERCENT, percent.coerceIn(0, 200)).apply()
    }

    fun isFnVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_FN_BUTTON, false)
    }

    fun setFnVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_FN_BUTTON, visible).apply()
    }

    fun isConnectionStatusVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_CONNECTION_STATUS, false)
    }

    fun setConnectionStatusVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CONNECTION_STATUS, visible).apply()
    }
}
