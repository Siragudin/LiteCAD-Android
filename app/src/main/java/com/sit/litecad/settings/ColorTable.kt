package com.sit.litecad.settings

import com.sit.litecad.R

/**
 * Список цветов линий, совпадает с ПК-версией.
 */
object ColorTable {

    data class Entry(val nameRes: Int, val argb: Int)

    val values: List<Entry> = listOf(
        Entry(R.string.color_black, 0xFF000000.toInt()),
        Entry(R.string.color_gray, 0xFF808080.toInt()),
        Entry(R.string.color_blue, 0xFF0000FF.toInt()),
        Entry(R.string.color_red, 0xFFFF0000.toInt()),
        Entry(R.string.color_green, 0xFF00A000.toInt()),
        Entry(R.string.color_dark_blue, 0xFF1565C0.toInt()),
    )

    val DEFAULT: Int = values[0].argb

    fun nameResFor(argb: Int): Int =
        values.firstOrNull { it.argb == argb }?.nameRes ?: R.string.color_black
}
