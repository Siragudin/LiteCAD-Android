package com.sit.litecad.settings

import android.content.Context

/**
 * Хранение выбранной темы редактора.
 * Коды: "light" (дефолт) / "dark". Используется в EditorScreen.
 * Файл SharedPreferences общий с LanguagePreferences ("app_prefs").
 */
object ThemePreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_THEME = "theme"
    private const val DEFAULT_THEME = "light"

    /** Поддерживаемые темы. */
    val supported: List<String> = listOf("light", "dark")

    /** Текущая тема. Если сохранённой нет или она не поддерживается — дефолт. */
    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, null)
        return if (saved != null && saved in supported) saved else DEFAULT_THEME
    }

    /** Сохранить выбранную тему. */
    fun setTheme(context: Context, theme: String) {
        if (theme !in supported) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }
}
