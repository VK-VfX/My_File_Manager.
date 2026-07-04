package com.vfxsal.filemanager.feature.settings

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("Match system"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Small SharedPreferences-backed store for app-wide display preferences, read once at
 *  startup by [SettingsViewModel] and written back whenever the user changes them in Settings. */
object SettingsStore {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode {
        val name = prefs(context).getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getDynamicColor(context: Context): Boolean = prefs(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
