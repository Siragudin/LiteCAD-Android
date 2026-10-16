package com.sit.litecad.settings

import android.content.Context

/**
 * Хранение списка инструментов быстрого доступа (боковой блок редактора).
 * Хранится в общем файле app_prefs под ключом quick_tools как строка id через запятую,
 * например "eraser,leader,fill". Порядок — по добавлению (последний — самый новый).
 */
object QuickToolsPreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_QUICK_TOOLS = "quick_tools"
    private const val SEPARATOR = ","

    /** Максимум инструментов в блоке. */
    const val MAX: Int = 3

    /** Текущий список id (в порядке добавления; индекс 0 — самый старый, последний — самый новый). */
    fun getTools(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_QUICK_TOOLS, "") ?: ""
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    /**
     * Добавить инструмент (в конец списка). Если уже есть — ничего не делать.
     * Если размер == MAX — заменить самый старый (удалить первый, добавить в конец).
     */
    fun add(context: Context, toolId: String) {
        if (toolId.isBlank()) return
        val current = getTools(context)
        if (toolId in current) return
        val updated = if (current.size >= MAX) {
            current.drop(1) + toolId
        } else {
            current + toolId
        }
        save(context, updated)
    }

    /** Убрать инструмент из списка (если был). */
    fun remove(context: Context, toolId: String) {
        save(context, getTools(context).filter { it != toolId })
    }

    /** Переключить: если есть — убрать, если нет — добавить (с учётом MAX). */
    fun toggle(context: Context, toolId: String) {
        if (toolId in getTools(context)) remove(context, toolId) else add(context, toolId)
    }

    private fun save(context: Context, tools: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_TOOLS, tools.joinToString(SEPARATOR))
            .apply()
    }
}
