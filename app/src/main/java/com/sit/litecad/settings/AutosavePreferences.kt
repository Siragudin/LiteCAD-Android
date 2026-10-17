package com.sit.litecad.settings

import android.content.Context

/**
 * Хранение интервала периодического автосохранения документа (в минутах).
 * Значения: 0 (Выкл, дефолт) / 3 / 5 / 10.
 * Файл SharedPreferences общий с ThemePreferences/LanguagePreferences ("app_prefs").
 */
object AutosavePreferences {

    const val OFF = 0
    const val MIN_3 = 3
    const val MIN_5 = 5
    const val MIN_10 = 10

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_AUTOSAVE_INTERVAL = "autosave_interval_minutes"

    /** Поддерживаемые интервалы автосохранения. */
    val supported: List<Int> = listOf(OFF, MIN_3, MIN_5, MIN_10)

    /** Текущий интервал автосохранения в минутах. Если сохранённого нет или он не поддерживается — OFF. */
    fun getIntervalMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_AUTOSAVE_INTERVAL, OFF)
        return if (saved in supported) saved else OFF
    }

    /** Сохранить выбранный интервал автосохранения. */
    fun setIntervalMinutes(context: Context, minutes: Int) {
        if (minutes !in supported) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AUTOSAVE_INTERVAL, minutes)
            .apply()
    }
}