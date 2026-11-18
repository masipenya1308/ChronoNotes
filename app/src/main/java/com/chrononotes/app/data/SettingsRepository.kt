package com.chrononotes.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val themeMode: Int = 0, // 0 — система, 1 — светлая, 2 — тёмная
    val fontScale: Float = 1f,
    val autoLockMinutes: Int = 5,
    val accent: Int = 0,
)

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chrononotes_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            themeMode = prefs.getInt(KEY_THEME, 0),
            fontScale = prefs.getFloat(KEY_FONT, 1f),
            autoLockMinutes = prefs.getInt(KEY_LOCK, 5),
            accent = prefs.getInt(KEY_ACCENT, 0),
        ),
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putInt(KEY_THEME, next.themeMode)
            .putFloat(KEY_FONT, next.fontScale)
            .putInt(KEY_LOCK, next.autoLockMinutes)
            .putInt(KEY_ACCENT, next.accent)
            .apply()
        _settings.value = next
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_FONT = "font_scale"
        const val KEY_LOCK = "auto_lock_minutes"
        const val KEY_ACCENT = "accent"
    }
}
