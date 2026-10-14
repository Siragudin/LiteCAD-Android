package com.sit.litecad.settings

import android.content.Context

/**
 * Хранение выбранного языка приложения.
 * Коды: "en" (дефолт) / "ru". Используется в MainActivity.attachBaseContext
 * и в экране настроек.
 */
object LanguagePreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANGUAGE = "en"

    /** Поддерживаемые языки. */
    val supported: List<String> = listOf("en", "ru")

    /** Текущий язык. Если сохранённого нет или он не поддерживается — дефолт. */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        return if (saved != null && saved in supported) saved else DEFAULT_LANGUAGE
    }

    /** Сохранить выбранный язык. */
    fun setLanguage(context: Context, language: String) {
        if (language !in supported) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }
}
