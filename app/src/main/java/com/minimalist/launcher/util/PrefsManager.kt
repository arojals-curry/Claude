package com.minimalist.launcher.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var use24HourClock: Boolean
        get() = prefs.getBoolean(KEY_24H_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_24H_CLOCK, value).apply()

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DATE, value).apply()

    var showBattery: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BATTERY, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BATTERY, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var maxFavorites: Int
        get() = prefs.getInt(KEY_MAX_FAVORITES, 6)
        set(value) = prefs.edit().putInt(KEY_MAX_FAVORITES, value).apply()

    fun getFavorites(): List<String> {
        val raw = prefs.getString(KEY_FAVORITES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEPARATOR)
    }

    fun setFavorites(favorites: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString(SEPARATOR)).apply()
    }

    fun addFavorite(appKey: String): Boolean {
        val current = getFavorites().toMutableList()
        if (current.size >= maxFavorites) return false
        if (current.contains(appKey)) return false
        current.add(appKey)
        setFavorites(current)
        return true
    }

    fun removeFavorite(appKey: String) {
        val current = getFavorites().toMutableList()
        current.remove(appKey)
        setFavorites(current)
    }

    fun isFavorite(appKey: String): Boolean = getFavorites().contains(appKey)

    fun getCustomLabel(appKey: String): String? {
        return prefs.getString("$KEY_CUSTOM_LABEL_PREFIX$appKey", null)
    }

    fun setCustomLabel(appKey: String, label: String?) {
        if (label == null) {
            prefs.edit().remove("$KEY_CUSTOM_LABEL_PREFIX$appKey").apply()
        } else {
            prefs.edit().putString("$KEY_CUSTOM_LABEL_PREFIX$appKey", label).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "minimalist_launcher_prefs"
        private const val KEY_24H_CLOCK = "use_24h_clock"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_SHOW_BATTERY = "show_battery"
        private const val KEY_THEME = "theme"
        private const val KEY_MAX_FAVORITES = "max_favorites"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_CUSTOM_LABEL_PREFIX = "custom_label_"
        private const val SEPARATOR = ";;;"

        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_AMOLED = "amoled"
    }
}
