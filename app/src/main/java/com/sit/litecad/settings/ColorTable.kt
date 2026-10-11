package com.sit.litecad.settings

/**
 * Список цветов линий, совпадает с ПК-версией.
 */
object ColorTable {

    data class Entry(val name: String, val argb: Int)

    val values: List<Entry> = listOf(
        Entry("Чёрный", 0xFF000000.toInt()),
        Entry("Серый", 0xFF808080.toInt()),
        Entry("Синий", 0xFF0000FF.toInt()),
        Entry("Красный", 0xFFFF0000.toInt()),
        Entry("Зелёный", 0xFF00A000.toInt()),
        Entry("Тёмно-синий", 0xFF1565C0.toInt()),
    )

    val DEFAULT: Int = values[0].argb

    fun nameFor(argb: Int): String =
        values.firstOrNull { it.argb == argb }?.name ?: "Чёрный"
}